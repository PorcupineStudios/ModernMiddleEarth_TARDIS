package com.porcupinestudios.lotrport.colony.tech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.market.Research;
import com.porcupinestudios.lotrport.colony.work.Modifiers;
import com.porcupinestudios.lotrport.colony.work.Production;
import com.porcupinestudios.lotrport.journey.CaravanConditions;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.ColonySystems;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import com.porcupinestudios.lotrport.society.sim.Tier;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * A colony's tech tree and milestones: which {@link TechDef} nodes it has researched (and which fork branches it
 * chose), and which {@link MilestoneDef}s it has reached. Researching pays the node's cost; the {@code milestones}
 * colony system checks milestones once a day. Hooks: {@link Production#unlocked} (recipes, building types, edicts),
 * {@link Modifiers} {@code research} (the nodes' work and happiness).
 */
public final class Techs {
    public static final ResourceLocation MILESTONES = LotrPort.id("milestones");
    public static final ResourceLocation TECH_HISTORY = LotrPort.id("colony_tech");
    public static final ResourceLocation MILESTONE_HISTORY = LotrPort.id("colony_milestone");

    /** What a colony has: researched nodes (each with the day), reached milestones. */
    public record State(Map<ResourceLocation, Long> techs, Map<ResourceLocation, Long> milestones) {
        public static final State NEW = new State(Map.of(), Map.of());
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG).optionalFieldOf("techs", Map.of())
                        .forGetter(State::techs),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG).optionalFieldOf("milestones", Map.of())
                        .forGetter(State::milestones))
                .apply(i, State::new));
    }

    public static final ComponentType<State> STATE = ComponentType.register(LotrPort.id("techs"), State.CODEC);

    private Techs() {}

    public static void register() {
        TechDef.register();
        MilestoneDef.register();
        Production.unlocked = Techs::has;
        Modifiers.work("research", (server, c) -> {
            double f = 1;
            for (ResourceLocation id : of(c).techs().keySet()) {
                f *= TechDef.get(id).map(TechDef::work).orElse(1.0);
            }
            return f;
        });
        Modifiers.happiness("research", (server, c) -> {
            double h = 0;
            for (ResourceLocation id : of(c).techs().keySet()) {
                h += TechDef.get(id).map(TechDef::happiness).orElse(0.0);
            }
            return h;
        });
        ColonySystems.register(ColonySystem.of(MILESTONES, ColonySystem.Cadence.DAY, Tier.ALL, Set.of(Research.SYSTEM),
                Techs::milestones));
    }

    static State of(Colony c) {
        return c.get(STATE).orElse(State.NEW);
    }

    public static State state(MinecraftServer server, Colony c) {
        return of(Colonies.get(server).get(c.id()).orElse(c));
    }

    public static boolean has(Colony c, ResourceLocation tech) {
        return of(c).techs().containsKey(tech);
    }

    /** One trade's factor from researched nodes ({@code jobs}). */
    public static double jobFactor(Colony c, ResourceLocation job) {
        double f = 1;
        for (ResourceLocation id : of(c).techs().keySet()) {
            Optional<TechDef> d = TechDef.get(id);
            if (d.isPresent()) {
                f *= d.get().jobs().getOrDefault(job, 1.0);
            }
        }
        return f;
    }

    /** The fork branch another node of the same fork closed, if one did. */
    public static Optional<ResourceLocation> closedBy(Colony c, ResourceLocation id) {
        Optional<String> fork = TechDef.get(id).flatMap(TechDef::fork);
        if (fork.isEmpty()) {
            return Optional.empty();
        }
        for (ResourceLocation have : of(c).techs().keySet()) {
            if (!have.equals(id) && TechDef.get(have).flatMap(TechDef::fork).equals(fork)) {
                return Optional.of(have);
            }
        }
        return Optional.empty();
    }

    /** Why a node can't be researched now (empty: it can). */
    public static Optional<Component> whyNot(MinecraftServer server, Colony c, ResourceLocation id) {
        Colony now = Colonies.get(server).get(c.id()).orElse(c);
        Optional<TechDef> def = TechDef.get(id);
        if (def.isEmpty()) {
            return Optional.of(Component.literal("No such research"));
        }
        if (has(now, id)) {
            return Optional.of(Component.literal("Researched"));
        }
        Optional<ResourceLocation> closed = closedBy(now, id);
        if (closed.isPresent()) {
            return Optional.of(Component.literal("Closed: the colony chose " + TechDef.titleOf(closed.get())));
        }
        List<String> missing = new ArrayList<>();
        for (ResourceLocation a : def.get().after()) {
            if (!has(now, a)) {
                missing.add(TechDef.titleOf(a));
            }
        }
        if (!missing.isEmpty()) {
            return Optional.of(Component.literal("First: " + String.join(", ", missing)));
        }
        return def.get().cost().missing(server, now);
    }

    public static Component research(MinecraftServer server, Colony c, ResourceLocation id) {
        Optional<Component> why = whyNot(server, c, id);
        if (why.isPresent()) {
            return why.get();
        }
        Optional<Component> paid = TechDef.get(id).get().cost().pay(server, c);
        if (paid.isPresent()) {
            return paid.get();
        }
        Colony now = Colonies.get(server).get(c.id()).orElse(c);
        State st = of(now);
        Map<ResourceLocation, Long> techs = new java.util.LinkedHashMap<>(st.techs());
        long t = server.overworld().getGameTime();
        techs.put(id, t);
        Colonies.get(server).set(now, STATE, new State(Map.copyOf(techs), st.milestones()));
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", now.id());
        tag.putString("tech", id.toString());
        HistoryLog.get(server).append(new HistoryEntry(t, TECH_HISTORY, now.dimension(), Optional.of(now.hall()), tag));
        return Component.literal("Researched " + TechDef.titleOf(id)).withStyle(ChatFormatting.GREEN);
    }

    /** Whether a milestone's needs hold now. */
    public static boolean reached(MinecraftServer server, Colony c, MilestoneDef m) {
        for (ResourceLocation t : m.techs()) {
            if (!has(c, t)) {
                return false;
            }
        }
        if (!m.buildings().isEmpty()) {
            Set<ResourceLocation> types = new HashSet<>();
            for (Building b : Buildings.of(c).list()) {
                types.add(b.type());
            }
            if (!types.containsAll(m.buildings())) {
                return false;
            }
        }
        return m.condition().isEmpty() || m.condition().get().test(CaravanConditions.colonyContext(server, c));
    }

    static void milestones(SystemContext ctx) {
        Colony c = ctx.colony();
        if (c.ai()) {
            return;
        }
        MinecraftServer server = ctx.server();
        State st = of(c);
        List<Map.Entry<ResourceLocation, MilestoneDef>> open = new ArrayList<>();
        for (Map.Entry<ResourceLocation, MilestoneDef> e : MilestoneDef.all().entrySet()) {
            if (!st.milestones().containsKey(e.getKey())) {
                open.add(e);
            }
        }
        open.sort(Comparator.comparingInt(e -> e.getValue().order()));
        Map<ResourceLocation, Long> reached = new java.util.LinkedHashMap<>(st.milestones());
        long coins = 0;
        for (Map.Entry<ResourceLocation, MilestoneDef> e : open) {
            if (!reached(server, c, e.getValue())) {
                continue;
            }
            MilestoneDef m = e.getValue();
            reached.put(e.getKey(), ctx.now());
            coins += m.coins();
            m.research().forEach((tier, n) -> Research.add(server, c, tier, n));
            CompoundTag tag = new CompoundTag();
            tag.putUUID("colony", c.id());
            tag.putString("milestone", e.getKey().toString());
            HistoryLog.get(server).append(new HistoryEntry(ctx.now(), MILESTONE_HISTORY, c.dimension(),
                    Optional.of(c.hall()), tag));
            var ruler = server.getPlayerList().getPlayer(c.founder());
            if (ruler != null) {
                ruler.sendSystemMessage(Component.literal(c.name() + " reached a milestone: " + m.title(e.getKey())
                        + (m.description().isEmpty() ? "" : " (" + m.description() + ")")).withStyle(ChatFormatting.GOLD));
            }
        }
        if (reached.size() != st.milestones().size()) {
            Colony now = Colonies.get(server).get(c.id()).orElse(c);
            State fresh = of(now);
            Colonies.get(server).set(now, STATE, new State(fresh.techs(), Map.copyOf(reached)));
            if (coins > 0) {
                now = Colonies.get(server).get(c.id()).orElse(c);
                long gold = now.get(CoreComponents.TREASURY).orElse(CoreComponents.Treasury.EMPTY).coins();
                Colonies.get(server).set(now, CoreComponents.TREASURY, new CoreComponents.Treasury(gold + coins));
            }
        }
    }
}
