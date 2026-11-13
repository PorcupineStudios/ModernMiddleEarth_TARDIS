package com.porcupinestudios.lotrport.colony.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.Skills;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.work.Stores;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.journey.CaravanConditions;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.ColonySystem.Cadence;
import com.porcupinestudios.lotrport.society.sim.ColonySystems;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import com.porcupinestudios.lotrport.society.sim.Tier;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * The colony's research points, in tiers ({@code colony_research_tier/<id>.json}). Researchers working in a library
 * earn points of the tier the colony puts them on (the Research tab): tier 1, basic research, costs only their time;
 * higher tiers also use goods from the stores for every point (paper, books...) and may need the colony to meet a
 * condition. Researchers not put on a tier do basic research. Upgrades spend points of a given tier.
 */
public final class Research {
    public static final ResourceLocation SYSTEM = LotrPort.id("research");
    public static final ResourceLocation JOB = LotrPort.id("researcher");
    public static final ResourceLocation BASIC = LotrPort.id("basic");

    /**
     * A tier of research.
     *
     * @param name what the screen calls its points
     * @param order tiers are listed (and researchers put on them) in this order; basic is lowest
     * @param rate points a researcher earns a day (times their skill)
     * @param cost goods used up per point
     * @param requires what the colony must meet to research it
     * @param requiresText that, in words
     */
    public record TierDef(String name, int order, double rate, Map<ResourceLocation, Double> cost,
            Optional<Condition> requires, String requiresText) {
        public static final Codec<TierDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name", "").forGetter(TierDef::name),
                Codec.INT.optionalFieldOf("order", 1).forGetter(TierDef::order),
                Codec.DOUBLE.optionalFieldOf("rate", 1.0).forGetter(TierDef::rate),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("cost", Map.of())
                        .forGetter(TierDef::cost),
                Conditions.CODEC.optionalFieldOf("requires").forGetter(TierDef::requires),
                Codec.STRING.optionalFieldOf("requires_text", "").forGetter(TierDef::requiresText))
                .apply(i, TierDef::new));
        static final TierDef BASIC_DEFAULT = new TierDef("Basic research", 1, 1, Map.of(), Optional.empty(), "");
    }

    /** Unspent points per tier. */
    public static final ComponentType<Map<ResourceLocation, Double>> POINTS = ComponentType.register(
            LotrPort.id("research"), Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE));
    /** How many researchers the colony puts on each tier above basic. */
    public static final ComponentType<Map<ResourceLocation, Integer>> PLAN = ComponentType.register(
            LotrPort.id("research_plan"), Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT));

    private static final CodecJsonLoader<TierDef> TIERS = new CodecJsonLoader<>("colony_research_tier",
            TierDef.CODEC);

    private Research() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(TIERS));
        ColonySystems.register(ColonySystem.of(SYSTEM, Cadence.DAY, Tier.ALL, Set.of(LotrPort.id("needs")),
                Research::day));
    }

    /** The tiers, lowest first (basic always there). */
    public static Map<ResourceLocation, TierDef> tiers() {
        Map<ResourceLocation, TierDef> all = new LinkedHashMap<>(TIERS.entries());
        all.putIfAbsent(BASIC, TierDef.BASIC_DEFAULT);
        List<Map.Entry<ResourceLocation, TierDef>> list = new ArrayList<>(all.entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<ResourceLocation, TierDef> e) -> e.getValue().order())
                .thenComparing(e -> e.getKey().toString()));
        Map<ResourceLocation, TierDef> out = new LinkedHashMap<>();
        list.forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    public static TierDef tier(ResourceLocation id) {
        return tiers().getOrDefault(id, TierDef.BASIC_DEFAULT);
    }

    public static String name(ResourceLocation id) {
        TierDef t = tier(id);
        return t.name().isEmpty() ? id.getPath() : t.name();
    }

    static Colony fresh(MinecraftServer server, Colony c) {
        return Colonies.get(server).get(c.id()).orElse(c);
    }

    public static double points(MinecraftServer server, Colony c, ResourceLocation tier) {
        return fresh(server, c).get(POINTS).map(m -> m.getOrDefault(tier, 0.0)).orElse(0.0);
    }

    public static void add(MinecraftServer server, Colony c, ResourceLocation tier, double n) {
        Colony now = fresh(server, c);
        Map<ResourceLocation, Double> m = new LinkedHashMap<>(now.get(POINTS).orElse(Map.of()));
        m.merge(tier, n, Double::sum);
        Colonies.get(server).set(now, POINTS, m);
    }

    /** Spends points of a tier; false (nothing spent) if there are too few. */
    public static boolean spend(MinecraftServer server, Colony c, ResourceLocation tier, double n) {
        if (points(server, c, tier) + 1e-9 < n) {
            return false;
        }
        add(server, c, tier, -n);
        return true;
    }

    public static Map<ResourceLocation, Integer> plan(MinecraftServer server, Colony c) {
        return fresh(server, c).get(PLAN).orElse(Map.of());
    }

    public static void setPlan(MinecraftServer server, Colony c, ResourceLocation tier, int n) {
        Colony now = fresh(server, c);
        Map<ResourceLocation, Integer> m = new LinkedHashMap<>(now.get(PLAN).orElse(Map.of()));
        if (n <= 0 || tier.equals(BASIC)) {
            m.remove(tier);
        } else {
            m.put(tier, n);
        }
        Colonies.get(server).set(now, PLAN, m);
    }

    /** Why the colony can't research this tier now (empty: it can; goods are checked each day). */
    public static Optional<Component> whyNot(MinecraftServer server, Colony c, ResourceLocation tier) {
        TierDef t = tier(tier);
        if (t.requires().isPresent() && !t.requires().get().test(CaravanConditions.colonyContext(server, c))) {
            return Optional.of(Component.literal(t.requiresText().isEmpty() ? "The colony isn't ready"
                    : "Needs " + t.requiresText()));
        }
        return Optional.empty();
    }

    /** The colony's working researchers (in a job building that stands). */
    public static List<Person> researchers(MinecraftServer server, Colony c) {
        Buildings buildings = Buildings.of(c);
        List<Person> out = new ArrayList<>();
        for (Person p : Society.citizens(server, c)) {
            var job = p.get(WorkComponents.JOB).orElse(null);
            if (job != null && job.job().equals(JOB) && buildings.get(job.building()).isPresent()) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparing(Person::id));
        return out;
    }

    /** Who works on which tier today: the plan's tiers from the highest down, the rest on basic. */
    public static Map<ResourceLocation, List<Person>> assignment(MinecraftServer server, Colony c) {
        List<Person> left = new ArrayList<>(researchers(server, c));
        // the most skilled take the hardest tiers
        left.sort(Comparator.comparingDouble((Person p) -> -Skills.workFactor(p, JOB)).thenComparing(Person::id));
        Map<ResourceLocation, Integer> plan = plan(server, c);
        List<ResourceLocation> order = new ArrayList<>(tiers().keySet());
        java.util.Collections.reverse(order);
        Map<ResourceLocation, List<Person>> out = new LinkedHashMap<>();
        for (ResourceLocation t : order) {
            if (t.equals(BASIC) || whyNot(server, c, t).isPresent()) {
                continue;
            }
            int n = Math.min(plan.getOrDefault(t, 0), left.size());
            if (n > 0) {
                out.put(t, new ArrayList<>(left.subList(0, n)));
                left = new ArrayList<>(left.subList(n, left.size()));
            }
        }
        out.put(BASIC, left);
        return out;
    }

    /** Points a tier would earn in a day with these researchers, before goods. */
    static double earn(ResourceLocation tier, List<Person> who) {
        double sum = 0;
        for (Person p : who) {
            sum += tier(tier).rate() * Skills.workFactor(p, JOB);
        }
        return sum;
    }

    /** What each tier earns a day as planned (before goods run short). */
    public static Map<ResourceLocation, Double> perDay(MinecraftServer server, Colony c) {
        Map<ResourceLocation, Double> out = new LinkedHashMap<>();
        assignment(server, c).forEach((t, who) -> out.put(t, earn(t, who)));
        return out;
    }

    /** One day of research: points per tier, higher tiers limited by (and using up) their goods. */
    public static Map<ResourceLocation, Double> research(MinecraftServer server, Colony c, double days) {
        Map<ResourceLocation, Double> got = new LinkedHashMap<>();
        Stores stores = null;
        for (Map.Entry<ResourceLocation, List<Person>> e : assignment(server, c).entrySet()) {
            TierDef t = tier(e.getKey());
            double want = earn(e.getKey(), e.getValue()) * days;
            if (want <= 0) {
                continue;
            }
            if (!t.cost().isEmpty()) {
                if (stores == null) {
                    stores = new Stores(server, server.getLevel(c.dimension()), fresh(server, c));
                }
                double share = 1;
                for (Map.Entry<ResourceLocation, Double> k : t.cost().entrySet()) {
                    double need = want * k.getValue();
                    share = Math.min(share, need <= 0 ? 1 : stores.count(k.getKey(), null) / need);
                }
                want *= Math.max(0, share);
                if (want <= 1e-9) {
                    continue;
                }
                for (Map.Entry<ResourceLocation, Double> k : t.cost().entrySet()) {
                    int n = (int) Math.ceil(want * k.getValue() - 1e-9);
                    stores.take(k.getKey(), n, null);
                }
            }
            got.put(e.getKey(), want);
        }
        if (stores != null) {
            stores.save();
        }
        got.forEach((t, n) -> add(server, c, t, n));
        return got;
    }

    static void day(SystemContext ctx) {
        if (!ctx.colony().ai()) {
            research(ctx.server(), ctx.colony(), ctx.days());
        }
    }

    /** "2 Paper, 1 Ink Sac" a point. */
    public static String costText(ResourceLocation tier) {
        List<String> out = new ArrayList<>();
        tier(tier).cost().forEach((k, v) -> out.add((v == Math.floor(v) ? Integer.toString(v.intValue())
                : String.format("%.2f", v)) + " " + Market.name(k)));
        return out.isEmpty() ? "time only" : String.join(", ", out) + " a point";
    }
}
