package com.porcupinestudios.lotrport.colony.work;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.Person;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * What the colony's trades make (RingRealms P5 production): each crafting batch a worker picks the recipe of their
 * trade that is needed most. Work orders come first (oldest first), then the item furthest below its stock target.
 * With targets set and all met, a trade rests instead of filling the stores. Gatherers stop gathering an item once it
 * reaches its target. Tools wear out ({@link JobDef#tool()}): every {@code tool_days} worker-days one tool is taken from
 * the stores, and without one a worker makes {@code no_tool_factor} as much. Each tool has an automatic target of one
 * spare per worker who uses it, so smiths keep the colony equipped.
 */
public final class Production {
    /** An order for {@code left} more of an item. */
    public record Order(ResourceLocation item, int left) {
        public static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Order::item),
                Codec.INT.fieldOf("left").forGetter(Order::left)).apply(i, Order::new));
    }

    /** The colony's production plan: stock targets, open orders, and each trade's wear on its current tool. */
    public record Plan(Map<ResourceLocation, Integer> targets, List<Order> orders, Map<ResourceLocation, Double> wear) {
        public static final Plan EMPTY = new Plan(Map.of(), List.of(), Map.of());
        public static final Codec<Plan> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("targets", Map.of())
                        .forGetter(Plan::targets),
                Order.CODEC.listOf().optionalFieldOf("orders", List.of()).forGetter(Plan::orders),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("wear", Map.of())
                        .forGetter(Plan::wear))
                .apply(i, Plan::new));

        public int order(ResourceLocation item) {
            return orders.stream().filter(o -> o.item().equals(item)).mapToInt(Order::left).sum();
        }
    }

    public static final ComponentType<Plan> PLAN = ComponentType.register(LotrPort.id("production"), Plan.CODEC);
    public static final ResourceLocation TOOLS_WORN = LotrPort.id("tools_worn");

    /** Whether a colony may use a recipe that names a tech; the tech tree sets this (true until it does). */
    public static BiPredicate<Colony, ResourceLocation> unlocked = (c, tech) -> true;

    private Production() {}

    public static void init() {}

    public static Plan plan(MinecraftServer server, Colony c) {
        return Colonies.get(server).get(c.id()).orElse(c).get(PLAN).orElse(Plan.EMPTY);
    }

    static void save(MinecraftServer server, Colony c, Plan p) {
        Colony now = Colonies.get(server).get(c.id()).orElse(c);
        Colonies.get(server).set(now, PLAN, p);
    }

    /** Sets a stock target (0 removes it). */
    public static void setTarget(MinecraftServer server, Colony c, ResourceLocation item, int n) {
        Plan p = plan(server, c);
        Map<ResourceLocation, Integer> t = new LinkedHashMap<>(p.targets());
        if (n <= 0) {
            t.remove(item);
        } else {
            t.put(item, n);
        }
        save(server, c, new Plan(t, p.orders(), p.wear()));
    }

    /** Sets how many of an item are still ordered (0 cancels the order). */
    public static void setOrder(MinecraftServer server, Colony c, ResourceLocation item, int n) {
        Plan p = plan(server, c);
        List<Order> o = new ArrayList<>();
        boolean placed = false;
        for (Order x : p.orders()) {
            if (x.item().equals(item)) {
                if (!placed && n > 0) {
                    o.add(new Order(item, n));
                }
                placed = true;
            } else {
                o.add(x);
            }
        }
        if (!placed && n > 0) {
            o.add(new Order(item, n));
        }
        save(server, c, new Plan(p.targets(), o, p.wear()));
    }

    /** Every item some trade can make (for the Production tab), with the trade. */
    public static Map<ResourceLocation, ResourceLocation> makeable() {
        Map<ResourceLocation, ResourceLocation> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JobDef> e : JobDef.all().entrySet()) {
            for (JobDef.Recipe r : e.getValue().recipes()) {
                r.produces().keySet().forEach(item -> out.putIfAbsent(item, e.getKey()));
            }
        }
        return out;
    }

    /** One day's working state of a colony: the plan being changed, and auto targets for spare tools. */
    static final class Day {
        final Colony colony;
        final Map<ResourceLocation, Integer> targets;
        final List<Order> orders;
        final Map<ResourceLocation, Double> wear;
        int toolsWorn;

        Day(MinecraftServer server, Colony colony, List<Person> citizens) {
            this.colony = colony;
            Plan p = plan(server, colony);
            this.targets = new HashMap<>(p.targets());
            this.orders = new ArrayList<>(p.orders());
            this.wear = new HashMap<>(p.wear());
            Map<ResourceLocation, Integer> spares = new HashMap<>();
            for (Person c : citizens) {
                c.get(WorkComponents.JOB).flatMap(j -> JobDef.get(j.job())).flatMap(JobDef::tool)
                        .ifPresent(t -> spares.merge(t, 1, Integer::sum));
            }
            spares.forEach((t, n) -> targets.merge(t, n, Math::max));
        }

        void save(MinecraftServer server) {
            Plan before = plan(server, colony);
            Map<ResourceLocation, Integer> manual = before.targets();
            save(server, colony, new Plan(manual, List.copyOf(orders), Map.copyOf(wear)));
        }

        private static void save(MinecraftServer server, Colony c, Plan p) {
            Production.save(server, c, p);
        }

        /** How much one item is wanted: an order, or the gap below its target; 0 when it isn't. */
        double need(ResourceLocation item, Stores stores, Building place) {
            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i).item().equals(item)) {
                    return 1_000_000 - i;
                }
            }
            Integer t = targets.get(item);
            if (t == null) {
                return -1;
            }
            int have = stores.count(item, place);
            return have < t ? (t - have) / (double) t : 0;
        }

        /**
         * The recipe to make next, if any: needed most, inputs at hand, unlocked. With no order or target on any of
         * the trade's outputs, the first recipe whose inputs are at hand (the trade's old behaviour).
         */
        Optional<JobDef.Recipe> choose(JobDef def, Stores stores, Building place) {
            JobDef.Recipe best = null;
            double bestNeed = 0;
            JobDef.Recipe fallback = null;
            boolean anyPlanned = false;
            for (JobDef.Recipe r : def.recipes()) {
                if (r.requires().isPresent() && !unlocked.test(colony, r.requires().get())) {
                    continue;
                }
                double need = -1;
                for (ResourceLocation out : r.produces().keySet()) {
                    need = Math.max(need, need(out, stores, place));
                }
                anyPlanned |= need >= 0;
                if (!inputs(r, stores, place)) {
                    continue;
                }
                if (need > bestNeed) {
                    best = r;
                    bestNeed = need;
                }
                if (fallback == null && need < 0) {
                    fallback = r;
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
            // nothing planned at all: keep making the first thing it can; planned and all met: rest
            return anyPlanned && fallback == null ? Optional.empty() : Optional.ofNullable(fallback);
        }

        static boolean inputs(JobDef.Recipe r, Stores stores, Building place) {
            for (Map.Entry<ResourceLocation, Integer> in : r.consumes().entrySet()) {
                if (stores.count(in.getKey(), place) < in.getValue()) {
                    return false;
                }
            }
            return true;
        }

        /** Whether a gatherer should still gather this item. */
        boolean gather(ResourceLocation item, Stores stores, Building place) {
            return need(item, stores, place) != 0;
        }

        /** Counts made items against orders. */
        void made(ResourceLocation item, int n) {
            for (int i = 0; i < orders.size() && n > 0; i++) {
                Order o = orders.get(i);
                if (o.item().equals(item)) {
                    int used = Math.min(n, o.left());
                    n -= used;
                    if (o.left() - used <= 0) {
                        orders.remove(i--);
                    } else {
                        orders.set(i, new Order(item, o.left() - used));
                    }
                }
            }
        }

        /**
         * Wears the trade's tool for a worker's days; returns the work factor (1 with a tool, less without).
         */
        double tool(ResourceLocation job, JobDef def, double days, Stores stores, Building place) {
            if (def.tool().isEmpty()) {
                return 1;
            }
            ResourceLocation tool = def.tool().get();
            double w = wear.getOrDefault(job, 0.0);
            // 1.0 or more: the trade has no tool in hand and needs a new one
            if (w >= 1) {
                if (stores.take(tool, 1, place) < 1) {
                    wear.put(job, 1.0);
                    return def.noToolFactor();
                }
                toolsWorn++;
                w -= 1;
            }
            wear.put(job, w + days / Math.max(1, def.toolDays()));
            return 1;
        }
    }
}
