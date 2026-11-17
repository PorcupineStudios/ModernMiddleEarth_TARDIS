package com.porcupinestudios.lotrport.colony.people;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.work.Life;
import com.porcupinestudios.lotrport.colony.work.Modifiers;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.ColonySystems;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import com.porcupinestudios.lotrport.society.sim.Tier;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * A colony's society (RingRealms P4: grudges, incidents and brawls, unity, edicts, the Council).
 * <ul>
 *   <li>Unhappy citizens quarrel with another citizen now and then; each quarrel adds a grudge both ways (more
 *       between races that clash). Once a grudge reaches {@code brawl_at} the quarrel is a brawl: both lose happiness
 *       and the colony loses unity. Grudges fade by the day.</li>
 *   <li>Unity (0-100) moves toward the citizens' average happiness, less per extra race and plus the edicts'; it
 *       scales all work and adds to (or takes from) everyone's happiness.</li>
 *   <li>Edicts ({@link EdictDef}) are proclaimed in the Council tab, cost upkeep a day and lapse unpaid.</li>
 * </ul>
 * System {@code society} runs after {@code needs}. Seeded per colony and day, so a replay quarrels the same.
 */
public final class Council {
    public static final ResourceLocation SYSTEM = LotrPort.id("society");
    public static final ResourceLocation BRAWL_HISTORY = LotrPort.id("brawl");
    public static final ResourceLocation QUARRELS = LotrPort.id("quarrels");
    public static final ResourceLocation BRAWLS = LotrPort.id("brawls");

    /** A citizen's grudges against other citizens. */
    public record Grudges(Map<UUID, Double> against) {
        public static final Grudges NONE = new Grudges(Map.of());
        public static final Codec<Grudges> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.DOUBLE)
                .xmap(Grudges::new, Grudges::against);
    }

    /** Something that happened, for the Council tab. */
    public record Report(long time, String text, boolean brawl) {
        public static final Codec<Report> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("time").forGetter(Report::time),
                Codec.STRING.fieldOf("text").forGetter(Report::text),
                Codec.BOOL.optionalFieldOf("brawl", false).forGetter(Report::brawl)).apply(i, Report::new));
    }

    /** The colony's side: unity, the edicts in force, recent reports. */
    public record State(double unity, List<ResourceLocation> edicts, List<Report> reports) {
        public static final State NEW = new State(50, List.of(), List.of());
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("unity", 50.0).forGetter(State::unity),
                ResourceLocation.CODEC.listOf().optionalFieldOf("edicts", List.of()).forGetter(State::edicts),
                Report.CODEC.listOf().optionalFieldOf("reports", List.of()).forGetter(State::reports))
                .apply(i, State::new));

        State with(double u, List<ResourceLocation> e, List<Report> r) {
            return new State(u, List.copyOf(e), List.copyOf(r));
        }
    }

    public static final ComponentType<Grudges> GRUDGES = ComponentType.register(LotrPort.id("grudges"),
            Grudges.CODEC);
    public static final ComponentType<State> STATE = ComponentType.register(LotrPort.id("council"), State.CODEC);

    private Council() {}

    public static void register() {
        PeopleSettings.register();
        EdictDef.register();
        ColonySystems.register(ColonySystem.of(SYSTEM, ColonySystem.Cadence.DAY, Tier.ALL, Set.of(Life.NEEDS),
                Council::day));
        Modifiers.work("unity", (server, c) -> c.ai() ? 1 : PeopleSettings.get().unityWork(state(server, c).unity()));
        Modifiers.happiness("unity", (server, c) -> c.ai() ? 0
                : (state(server, c).unity() - 50) / 50 * PeopleSettings.get().unityHappiness());
        Modifiers.work("edicts", (server, c) -> {
            double f = 1;
            for (EdictDef e : edicts(server, c)) {
                f *= e.work();
            }
            return f;
        });
        Modifiers.happiness("edicts", (server, c) -> edicts(server, c).stream().mapToDouble(EdictDef::happiness).sum());
    }

    public static State state(MinecraftServer server, Colony c) {
        return Colonies.get(server).get(c.id()).orElse(c).get(STATE).orElse(State.NEW);
    }

    static void save(MinecraftServer server, Colony c, State s) {
        Colonies.get(server).set(Colonies.get(server).get(c.id()).orElse(c), STATE, s);
    }

    static List<EdictDef> edicts(MinecraftServer server, Colony c) {
        List<EdictDef> out = new ArrayList<>();
        for (ResourceLocation id : state(server, c).edicts()) {
            EdictDef.get(id).ifPresent(out::add);
        }
        return out;
    }

    public static int slots(MinecraftServer server, Colony c) {
        PeopleSettings s = PeopleSettings.get();
        return s.edictSlotsBase() + Society.citizens(server, c).size() / Math.max(1, s.citizensPerEdictSlot());
    }

    /** Why an edict can't be proclaimed now, if it can't. */
    public static Optional<Component> whyNot(MinecraftServer server, Colony c, ResourceLocation id) {
        Optional<EdictDef> def = EdictDef.get(id);
        if (def.isEmpty()) {
            return Optional.of(Component.literal("No such edict"));
        }
        State st = state(server, c);
        if (st.edicts().contains(id)) {
            return Optional.of(Component.literal("Already in force"));
        }
        if (st.edicts().size() >= slots(server, c)) {
            return Optional.of(Component.literal("The Council can hold only " + slots(server, c) + " edicts now"));
        }
        if (Society.citizens(server, c).size() < def.get().minPopulation()) {
            return Optional.of(Component.literal("Needs " + def.get().minPopulation() + " citizens"));
        }
        if (def.get().requires().isPresent()
                && !com.porcupinestudios.lotrport.colony.work.Production.unlocked.test(c, def.get().requires().get())) {
            return Optional.of(Component.literal("Needs research: " + def.get().requires().get().getPath()
                    .replace('_', ' ')));
        }
        for (ResourceLocation other : st.edicts()) {
            boolean clash = def.get().excludes().contains(other)
                    || EdictDef.get(other).map(o -> o.excludes().contains(id)).orElse(false);
            if (clash) {
                return Optional.of(Component.literal("Can't stand with " + EdictDef.get(other).map(o -> o.title(other))
                        .orElse(other.getPath())));
            }
        }
        return Optional.empty();
    }

    public static Component proclaim(MinecraftServer server, Colony c, ResourceLocation id) {
        Optional<Component> why = whyNot(server, c, id);
        if (why.isPresent()) {
            return why.get();
        }
        State st = state(server, c);
        List<ResourceLocation> e = new ArrayList<>(st.edicts());
        e.add(id);
        save(server, c, st.with(st.unity(), e, st.reports()));
        return Component.literal("Proclaimed: " + EdictDef.get(id).get().title(id));
    }

    public static Component repeal(MinecraftServer server, Colony c, ResourceLocation id) {
        State st = state(server, c);
        List<ResourceLocation> e = new ArrayList<>(st.edicts());
        if (!e.remove(id)) {
            return Component.literal("That edict isn't in force");
        }
        save(server, c, st.with(st.unity(), e, st.reports()));
        return Component.literal("Repealed");
    }

    /** A citizen's grudges, worst first, for the citizen page. */
    public static Map<UUID, Double> grudges(Person p) {
        return p.get(GRUDGES).orElse(Grudges.NONE).against();
    }

    // ================================================================ the day

    static void day(SystemContext ctx) {
        Colony c = ctx.colony();
        if (c.ai()) {
            return;
        }
        MinecraftServer server = ctx.server();
        PeopleSettings s = PeopleSettings.get();
        Persons persons = Persons.get(server);
        State st = state(server, c);
        List<Person> people = new ArrayList<>();
        for (Person p : Society.citizens(server, c)) {
            if (!p.has(CoreComponents.UNIT)) {
                people.add(p);
            }
        }
        List<Report> reports = new ArrayList<>(st.reports());
        List<ResourceLocation> edicts = new ArrayList<>(st.edicts());
        double unity = st.unity();
        int days = Math.max(1, ctx.days());

        // upkeep: edicts the treasury can't pay lapse, last proclaimed first
        long treasury = c.get(CoreComponents.TREASURY).orElse(CoreComponents.Treasury.EMPTY).coins();
        long upkeep = 0;
        for (ResourceLocation id : edicts) {
            upkeep += EdictDef.get(id).map(EdictDef::upkeep).orElse(0) * (long) days;
        }
        while (upkeep > treasury && !edicts.isEmpty()) {
            ResourceLocation gone = edicts.remove(edicts.size() - 1);
            upkeep -= EdictDef.get(gone).map(EdictDef::upkeep).orElse(0) * (long) days;
            reports.add(new Report(ctx.now(), "The treasury could not keep up "
                    + EdictDef.get(gone).map(d -> d.title(gone)).orElse(gone.getPath()) + "; it lapsed", false));
        }
        if (upkeep > 0) {
            Colonies.get(server).set(Colonies.get(server).get(c.id()).orElse(c), CoreComponents.TREASURY,
                    new CoreComponents.Treasury(treasury - upkeep));
        }
        double quarrelFactor = 1;
        double edictUnity = 0;
        for (ResourceLocation id : edicts) {
            Optional<EdictDef> d = EdictDef.get(id);
            if (d.isPresent()) {
                quarrelFactor *= d.get().quarrels();
                edictUnity += d.get().unity();
            }
        }

        // grudges fade
        Map<UUID, Map<UUID, Double>> grudges = new HashMap<>();
        Map<UUID, Person> byId = new HashMap<>();
        Set<String> races = new HashSet<>();
        double happy = 0;
        for (Person p : people) {
            byId.put(p.id(), p);
            races.add(Races.of(p.type()));
            happy += p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW).happiness();
            Map<UUID, Double> g = p.get(GRUDGES).orElse(Grudges.NONE).against();
            if (!g.isEmpty()) {
                Map<UUID, Double> m = new HashMap<>();
                g.forEach((who, v) -> {
                    double left = v - s.grudgeDecay() * days;
                    if (left > 0) {
                        m.put(who, left);
                    }
                });
                grudges.put(p.id(), m);
            }
        }

        // quarrels and brawls
        Random random = new Random(c.id().getLeastSignificantBits() ^ ctx.now() * 31L);
        Set<UUID> changedNeeds = new LinkedHashSet<>();
        Map<UUID, Double> lostHappiness = new HashMap<>();
        int quarrels = 0;
        int brawls = 0;
        if (people.size() > 1) {
            for (Person p : people) {
                double h = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW).happiness();
                if (h >= s.calmAt()) {
                    continue;
                }
                double chance = s.incidentChance() * (1 - h / s.calmAt()) * quarrelFactor;
                if (random.nextDouble() >= Math.min(1, chance * days)) {
                    continue;
                }
                // most often with someone they already hold a grudge against
                Map<UUID, Double> mine = grudges.getOrDefault(p.id(), Map.of());
                Person other = null;
                if (!mine.isEmpty() && random.nextBoolean()) {
                    other = byId.get(mine.keySet().iterator().next());
                }
                if (other == null) {
                    other = people.get(random.nextInt(people.size()));
                }
                if (other.id().equals(p.id())) {
                    continue;
                }
                double add = s.grudgePerIncident() * s.clash(Races.of(p.type()), Races.of(other.type()));
                double g = grudges.computeIfAbsent(p.id(), k -> new HashMap<>()).merge(other.id(), add, Double::sum);
                grudges.computeIfAbsent(other.id(), k -> new HashMap<>()).merge(p.id(), add, Double::sum);
                quarrels++;
                if (g >= s.brawlAt()) {
                    brawls++;
                    lostHappiness.merge(p.id(), s.brawlHappiness(), Double::sum);
                    lostHappiness.merge(other.id(), s.brawlHappiness(), Double::sum);
                    grudges.get(p.id()).put(other.id(), g / 2);
                    grudges.get(other.id()).put(p.id(), g / 2);
                    unity -= s.brawlUnity();
                    String text = name(p) + " and " + name(other) + " came to blows";
                    reports.add(new Report(ctx.now(), text, true));
                    CompoundTag tag = new CompoundTag();
                    tag.putUUID("colony", c.id());
                    tag.putUUID("a", p.id());
                    tag.putUUID("b", other.id());
                    HistoryLog.get(server).append(new HistoryEntry(ctx.now(), BRAWL_HISTORY, c.dimension(),
                            Optional.of(c.hall()), tag));
                } else if (random.nextInt(4) == 0) {
                    reports.add(new Report(ctx.now(), name(p) + " quarrelled with " + name(other), false));
                }
            }
        }
        for (Person p : people) {
            Map<UUID, Double> g = grudges.get(p.id());
            if (g != null) {
                persons.setQuiet(p, GRUDGES, g.isEmpty() ? null : new Grudges(Map.copyOf(g)));
            }
            Double lost = lostHappiness.get(p.id());
            if (lost != null) {
                WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
                persons.setQuiet(p, WorkComponents.NEEDS, n.withHappiness(Math.max(0, n.happiness() - lost),
                        n.unhappyDays()));
            }
        }
        ctx.count(QUARRELS, quarrels);
        ctx.count(BRAWLS, brawls);

        // unity toward its target
        double avg = people.isEmpty() ? 50 : happy / people.size();
        double target = Math.max(0, Math.min(100, avg - s.mixPenalty() * Math.max(0, races.size() - 1) + edictUnity));
        double step = s.unityPerDay() * days;
        unity = Math.max(0, Math.min(100, unity < target ? Math.min(target, unity + step)
                : Math.max(target, unity - step)));
        while (reports.size() > s.reportsKept()) {
            reports.remove(0);
        }
        save(server, c, st.with(unity, edicts, reports));
    }

    static String name(Person p) {
        return p.name().isEmpty() ? p.type().getPath().replace('_', ' ') : p.name();
    }
}
