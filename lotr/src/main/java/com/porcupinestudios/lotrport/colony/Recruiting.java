package com.porcupinestudios.lotrport.colony;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.travel.FastTravel;
import com.porcupinestudios.lotrport.travel.Waypoint;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Society;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Winning a rare race (RingRealms' recruitment quests): a colony's founder sneak + right-clicks an NPC whose type has
 * a {@code recruit/<id>.json}. If the gates pass (alignment, population, the race's limit), a quest of ordered stages
 * starts; each stage's objectives (deliver, kill, visit, population) must all be met. Delivering is done by
 * sneak + right-clicking the envoy again. After the last stage the envoy goes to the Hall and becomes a citizen.
 */
public final class Recruiting {
    public static final ResourceLocation STARTED_HISTORY = LotrPort.id("recruit_started");

    private Recruiting() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Recruiting::onDeath);
        NeoForge.EVENT_BUS.addListener(Recruiting::onPlayerTick);
    }

    /** The recruit definition for an NPC type, if it is a rare race. */
    public static Optional<Map.Entry<ResourceLocation, ColonyData.Recruit>> forType(ResourceLocation type) {
        return ColonyData.recruits().entrySet().stream().filter(e -> e.getValue().npc().equals(type)).findFirst();
    }

    /** Sneak + right-click on an NPC. Returns false if it is not a rare race (so the click does something else). */
    public static boolean interact(ServerPlayer player, LotrNpc npc) {
        Optional<Map.Entry<ResourceLocation, ColonyData.Recruit>> def = forType(npc.typeId());
        if (def.isEmpty() || Colonies.colonyOf(npc).isPresent()) {
            return false;
        }
        Colonies all = Colonies.get(player.server);
        Optional<Colony> colony = all.ofFounder(player.getUUID());
        if (colony.isEmpty()) {
            player.sendSystemMessage(Component.translatableWithFallback("recruit.lotr_port.no_colony",
                    "%s would only join a colony you have founded", npc.getDisplayName()));
            return true;
        }
        ResourceLocation id = def.get().getKey();
        Recruitment open = colony.get().recruits.get(id);
        if (open != null && open.envoy().equals(npc.getUUID())) {
            deliver(player, colony.get(), id, npc);
            advance(player.server, colony.get(), id);
            if (colony.get().recruits.containsKey(id)) {
                describe(player, colony.get(), id);
            }
            return true;
        }
        if (open != null) {
            player.sendSystemMessage(Component.translatableWithFallback("recruit.lotr_port.busy",
                    "You are already winning over another of them"));
            return true;
        }
        Optional<Component> problem = start(player, colony.get(), id, npc);
        problem.ifPresent(player::sendSystemMessage);
        return true;
    }

    /** Starts recruiting this envoy for the colony, or says why not. */
    public static Optional<Component> start(ServerPlayer player, Colony colony, ResourceLocation id, LotrNpc npc) {
        ColonyData.Recruit def = ColonyData.recruit(id).orElseThrow();
        if (def.faction().isPresent() && Alignment.get(player, def.faction().get()) < def.alignment()) {
            ResourceLocation f = def.faction().get();
            return Optional.of(Component.translatableWithFallback("recruit.lotr_port.alignment",
                    "%s will not listen until you have %s alignment with %s", npc.getDisplayName(), def.alignment(),
                    Component.translatable("faction." + f.getNamespace() + "." + f.getPath())));
        }
        if (Society.citizenCount(player.server, colony) < def.minPopulation()) {
            return Optional.of(Component.translatableWithFallback("recruit.lotr_port.population",
                    "%s wants to see a colony of at least %s people", npc.getDisplayName(), def.minPopulation()));
        }
        long already = Society.citizens(player.server, colony).stream().filter(c -> c.type().equals(def.npc())
                && c.get(CoreComponents.CITIZEN).map(CoreComponents.Citizen::rare).orElse(false)).count();
        if (def.limit() > 0 && already >= def.limit()) {
            return Optional.of(Component.translatableWithFallback("recruit.lotr_port.limit",
                    "%s already has as many of them as it can keep", colony.name));
        }
        npc.setPersistenceRequired(); // waits for you while the quest is open
        colony.recruits.put(id, new Recruitment(npc.getUUID(), player.getUUID(), 0, List.of(),
                player.server.overworld().getGameTime()));
        Colonies.get(player.server).changed();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", colony.id);
        tag.putString("recruit", id.toString());
        tag.putUUID("envoy", npc.getUUID());
        HistoryLog.get(player.server).append(new HistoryEntry(player.server.overworld().getGameTime(),
                STARTED_HISTORY, npc.level().dimension(), Optional.of(npc.blockPosition()), tag));
        player.sendSystemMessage(Component.translatableWithFallback("recruit.lotr_port.started",
                "%s might join %s, if you prove yourself:", npc.getDisplayName(), colony.name));
        advance(player.server, colony, id); // a stage may already be met (population)
        if (colony.recruits.containsKey(id)) {
            describe(player, colony, id);
        }
        return Optional.empty();
    }

    /** Takes items for the current stage's deliver objectives from the player. */
    public static void deliver(ServerPlayer player, Colony colony, ResourceLocation id, LotrNpc envoy) {
        Recruitment r = colony.recruits.get(id);
        List<ColonyData.Objective> objectives = stage(id, r);
        for (int i = 0; i < objectives.size(); i++) {
            ColonyData.Objective o = objectives.get(i);
            if (o.type() != ColonyData.ObjectiveType.DELIVER || o.target().isEmpty()) {
                continue;
            }
            int missing = o.count() - r.progress(i);
            Item item = BuiltInRegistries.ITEM.get(o.target().get());
            int given = take(player.getInventory(), item, missing);
            if (given > 0) {
                r = r.withProgress(i, r.progress(i) + given);
            }
        }
        colony.recruits.put(id, r);
        Colonies.get(player.server).changed();
    }

    private static List<ColonyData.Objective> stage(ResourceLocation id, Recruitment r) {
        List<ColonyData.Stage> stages = ColonyData.recruit(id).map(ColonyData.Recruit::stages).orElse(List.of());
        return r.stage() < stages.size() ? stages.get(r.stage()).objectives() : List.of();
    }

    private static boolean met(MinecraftServer server, Colony colony, Recruitment r, int i, ColonyData.Objective o) {
        if (o.type() == ColonyData.ObjectiveType.POPULATION) {
            return Society.citizenCount(server, colony) >= o.count();
        }
        return r.progress(i) >= o.goal();
    }

    /** Moves through every stage whose objectives are all met; after the last one the envoy joins. */
    public static void advance(MinecraftServer server, Colony colony, ResourceLocation id) {
        ColonyData.Recruit def = ColonyData.recruit(id).orElse(null);
        Recruitment r = colony.recruits.get(id);
        if (def == null || r == null) {
            return;
        }
        while (r.stage() < def.stages().size()) {
            List<ColonyData.Objective> objectives = def.stages().get(r.stage()).objectives();
            boolean done = true;
            for (int i = 0; i < objectives.size(); i++) {
                done &= met(server, colony, r, i, objectives.get(i));
            }
            if (!done) {
                break;
            }
            r = r.nextStage();
            ServerPlayer recruiter = server.getPlayerList().getPlayer(r.recruiter());
            if (recruiter != null && r.stage() < def.stages().size()) {
                recruiter.sendSystemMessage(Component.translatableWithFallback("recruit.lotr_port.stage",
                        "Stage %s of %s done.", r.stage(), def.stages().size()));
            }
        }
        colony.recruits.put(id, r);
        Colonies.get(server).changed();
        if (r.stage() >= def.stages().size()) {
            tryJoin(server, colony, id);
        }
    }

    /** Finished quests whose envoy wasn't loaded yet: try again (each colony day). */
    static void tryJoinAll(MinecraftServer server, Colony colony) {
        for (ResourceLocation id : new ArrayList<>(colony.recruits.keySet())) {
            Recruitment r = colony.recruits.get(id);
            int stages = ColonyData.recruit(id).map(d -> d.stages().size()).orElse(0);
            if (r.stage() >= stages) {
                tryJoin(server, colony, id);
            }
        }
    }

    private static void tryJoin(MinecraftServer server, Colony colony, ResourceLocation id) {
        Recruitment r = colony.recruits.get(id);
        LotrNpc envoy = null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(r.envoy());
            if (e instanceof LotrNpc n && n.isAlive()) {
                envoy = n;
                break;
            }
        }
        if (envoy == null) {
            return;
        }
        ServerLevel home = server.getLevel(colony.dimension);
        if (home == null) {
            return;
        }
        home.getChunk(colony.hall);
        BlockPos at = home.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, colony.hall.offset(1, 0, 1));
        if (envoy.level() != home) {
            return; // envoys only join from the colony's own world; met again there later
        }
        envoy.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        colony.recruits.remove(id);
        Colonies.get(server).join(server, colony, envoy, true);
        ServerPlayer recruiter = server.getPlayerList().getPlayer(r.recruiter());
        if (recruiter != null) {
            recruiter.sendSystemMessage(Component.translatableWithFallback("recruit.lotr_port.joined",
                    "%s has joined %s!", envoy.getDisplayName(), colony.name));
        }
    }

    /** Tells the player what the current stage asks, with progress. */
    public static void describe(ServerPlayer player, Colony colony, ResourceLocation id) {
        Recruitment r = colony.recruits.get(id);
        List<ColonyData.Objective> objectives = stage(id, r);
        int stages = ColonyData.recruit(id).map(d -> d.stages().size()).orElse(0);
        player.sendSystemMessage(Component.translatableWithFallback("recruit.lotr_port.stage_title",
                "Stage %s of %s:", r.stage() + 1, stages));
        for (int i = 0; i < objectives.size(); i++) {
            ColonyData.Objective o = objectives.get(i);
            int done = o.type() == ColonyData.ObjectiveType.POPULATION ? Society.citizenCount(player.server, colony)
                    : r.progress(i);
            player.sendSystemMessage(Component.literal(" - ").append(text(o))
                    .append(" (" + Math.min(done, o.goal()) + "/" + o.goal() + ")"));
        }
    }

    public static MutableComponent text(ColonyData.Objective o) {
        ResourceLocation t = o.target().orElse(LotrPort.id("none"));
        return switch (o.type()) {
            case DELIVER -> Component.translatableWithFallback("recruit.lotr_port.deliver", "bring %s %s", o.count(),
                    BuiltInRegistries.ITEM.get(t).getDescription());
            case KILL_FACTION -> Component.translatableWithFallback("recruit.lotr_port.kill_faction",
                    "slay %s of %s", o.count(), Component.translatable("faction." + t.getNamespace() + "."
                            + t.getPath()));
            case KILL_NPC -> Component.translatableWithFallback("recruit.lotr_port.kill_npc", "slay %s %s",
                    o.count(), Component.translatable("npc." + t.getNamespace() + "." + t.getPath()));
            case VISIT -> Component.translatableWithFallback("recruit.lotr_port.visit", "go to %s", FastTravel.name(t));
            case POPULATION -> Component.translatableWithFallback("recruit.lotr_port.population_goal",
                    "grow the colony to %s people", o.count());
        };
    }

    private static int take(Inventory inv, Item item, int amount) {
        int taken = 0;
        for (int i = 0; i < inv.getContainerSize() && taken < amount; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int n = Math.min(amount - taken, s.getCount());
                s.shrink(n);
                taken += n;
            }
        }
        return taken;
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            killed(player, event.getEntity());
        }
    }

    /** Counts a kill toward the player's colony's recruitment kill objectives. */
    public static void killed(ServerPlayer player, LivingEntity victim) {
        Optional<Colony> colony = Colonies.get(player.server).ofFounder(player.getUUID());
        if (colony.isEmpty() || colony.get().recruits.isEmpty()) {
            return;
        }
        Optional<ResourceLocation> faction = FactionKills.factionOf(victim);
        ResourceLocation type = victim instanceof LotrNpc n ? n.typeId() : null;
        for (ResourceLocation id : new ArrayList<>(colony.get().recruits.keySet())) {
            Recruitment r = colony.get().recruits.get(id);
            List<ColonyData.Objective> objectives = stage(id, r);
            boolean changed = false;
            for (int i = 0; i < objectives.size(); i++) {
                ColonyData.Objective o = objectives.get(i);
                boolean counts = switch (o.type()) {
                    case KILL_FACTION -> faction.isPresent() && o.target().equals(faction);
                    case KILL_NPC -> type != null && o.target().equals(Optional.of(type));
                    default -> false;
                };
                if (counts && r.progress(i) < o.goal()) {
                    r = r.withProgress(i, r.progress(i) + 1);
                    changed = true;
                }
            }
            if (changed) {
                colony.get().recruits.put(id, r);
                advance(player.server, colony.get(), id);
            }
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            visited(player);
        }
    }

    /** Marks visit objectives the player is standing at. */
    public static void visited(ServerPlayer player) {
        Optional<Colony> colony = Colonies.get(player.server).ofFounder(player.getUUID());
        if (colony.isEmpty() || colony.get().recruits.isEmpty()) {
            return;
        }
        for (ResourceLocation id : new ArrayList<>(colony.get().recruits.keySet())) {
            Recruitment r = colony.get().recruits.get(id);
            List<ColonyData.Objective> objectives = stage(id, r);
            boolean changed = false;
            for (int i = 0; i < objectives.size(); i++) {
                ColonyData.Objective o = objectives.get(i);
                if (o.type() != ColonyData.ObjectiveType.VISIT || r.progress(i) >= 1 || o.target().isEmpty()) {
                    continue;
                }
                Optional<Waypoint> w = FastTravel.waypoint(o.target().get());
                if (w.isPresent() && sq(player.getX() - w.get().x()) + sq(player.getZ() - w.get().z())
                        <= sq(o.radius())) {
                    r = r.withProgress(i, 1);
                    changed = true;
                    player.displayClientMessage(Component.translatableWithFallback("recruit.lotr_port.visited",
                            "You have been to %s", FastTravel.name(o.target().get())), true);
                }
            }
            if (changed) {
                colony.get().recruits.put(id, r);
                advance(player.server, colony.get(), id);
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
