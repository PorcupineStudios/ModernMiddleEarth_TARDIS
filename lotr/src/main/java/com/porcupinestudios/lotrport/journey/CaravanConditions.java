package com.porcupinestudios.lotrport.journey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.work.Stores;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.ringbearer.api.condition.ConditionType;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Condition types for caravan rules (the shared {@code ringbearer_api} conditions, so routes combine them with
 * {@code all}, {@code any} and {@code not}, and any other mod's types work too). Asked with {@link #context}: the
 * colony a route belongs to and the route. Colonies are named {@code "home"}, {@code "destination"} or by id.
 * <ul>
 * <li>{@code lotr_port:colony_stock} {colony, item, compare, value}: how many of an item a colony holds;</li>
 * <li>{@code lotr_port:colony_population} {colony, compare, value};</li>
 * <li>{@code lotr_port:days_since_sent} {days}: this route last set out at least that many days ago (or never);</li>
 * <li>{@code lotr_port:time_of_day} {from, to}: the hour (0-23) is in [from, to), wrapping past midnight;</li>
 * <li>{@code lotr_port:caravans_out} {compare, value}: how many of the home colony's caravans are on the road.</li>
 * </ul>
 * Compare is {@code <}, {@code <=}, {@code >}, {@code >=}, {@code =} or {@code !=}.
 */
public final class CaravanConditions {
    /** The {@link ConditionContext#extra} key holding a {@link Ctx}. */
    public static final String KEY = LotrPort.MOD_ID + ":caravan";
    public static final java.util.List<String> COMPARES = java.util.List.of("<", "<=", ">", ">=", "=", "!=");

    private static final DeferredRegister<ConditionType<?>> TYPES =
            DeferredRegister.create(Conditions.REGISTRY_KEY, LotrPort.MOD_ID);

    public static final DeferredHolder<ConditionType<?>, ConditionType<Stock>> STOCK =
            TYPES.register("colony_stock", () -> new ConditionType<>(Stock.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<Population>> POPULATION =
            TYPES.register("colony_population", () -> new ConditionType<>(Population.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<DaysSinceSent>> DAYS =
            TYPES.register("days_since_sent", () -> new ConditionType<>(DaysSinceSent.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<TimeOfDay>> TIME =
            TYPES.register("time_of_day", () -> new ConditionType<>(TimeOfDay.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<CaravansOut>> OUT =
            TYPES.register("caravans_out", () -> new ConditionType<>(CaravansOut.CODEC));

    private CaravanConditions() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    /** What a caravan rule is asked about. */
    public record Ctx(MinecraftServer server, Colony home, CaravanBook.Route route) {}

    public static ConditionContext context(MinecraftServer server, Colony home, CaravanBook.Route route) {
        return new ConditionContext(server.overworld(), Optional.empty(), Map.of(KEY, new Ctx(server, home, route)));
    }

    static Optional<Ctx> ctx(ConditionContext c) {
        return c.extra().get(KEY) instanceof Ctx x ? Optional.of(x) : Optional.empty();
    }

    public static boolean compare(int have, String op, int value) {
        return switch (op) {
            case "<" -> have < value;
            case "<=" -> have <= value;
            case ">" -> have > value;
            case ">=" -> have >= value;
            case "!=" -> have != value;
            default -> have == value;
        };
    }

    /** "home", "destination" or a colony id, as the colony. */
    public static Optional<Colony> colony(Ctx x, String ref) {
        Colonies cs = Colonies.get(x.server());
        return switch (ref) {
            case "home" -> cs.get(x.home().id());
            case "destination" -> cs.get(x.route().to());
            default -> {
                try {
                    Optional<Colony> c = cs.get(UUID.fromString(ref));
                    // another's colony may only be watched when the route goes there
                    yield c.filter(o -> o.founder().equals(x.home().founder()) || o.id().equals(x.route().to()));
                } catch (IllegalArgumentException e) {
                    yield Optional.empty();
                }
            }
        };
    }

    public static int stockOf(MinecraftServer server, Colony c, String item) {
        ResourceLocation id = ResourceLocation.tryParse(item);
        if (id == null) {
            return 0;
        }
        return new Stores(server, server.getLevel(c.dimension()), c).count(id, null);
    }

    public record Stock(String colony, String item, String compare, int value) implements Condition {
        public static final MapCodec<Stock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("colony", "destination").forGetter(Stock::colony),
                Codec.STRING.fieldOf("item").forGetter(Stock::item),
                Codec.STRING.optionalFieldOf("compare", "<").forGetter(Stock::compare),
                Codec.INT.optionalFieldOf("value", 0).forGetter(Stock::value))
                .apply(i, Stock::new));

        @Override
        public ConditionType<?> type() {
            return STOCK.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return ctx(context).flatMap(x -> colony(x, colony).map(c -> compare(stockOf(x.server(), c, item),
                    compare, value))).orElse(false);
        }
    }

    public record Population(String colony, String compare, int value) implements Condition {
        public static final MapCodec<Population> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("colony", "destination").forGetter(Population::colony),
                Codec.STRING.optionalFieldOf("compare", "<").forGetter(Population::compare),
                Codec.INT.optionalFieldOf("value", 0).forGetter(Population::value))
                .apply(i, Population::new));

        @Override
        public ConditionType<?> type() {
            return POPULATION.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return ctx(context).flatMap(x -> colony(x, colony).map(c -> compare(Society.citizens(x.server(), c)
                    .size(), compare, value))).orElse(false);
        }
    }

    public record DaysSinceSent(int days) implements Condition {
        public static final MapCodec<DaysSinceSent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("days", 1).forGetter(DaysSinceSent::days))
                .apply(i, DaysSinceSent::new));

        @Override
        public ConditionType<?> type() {
            return DAYS.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return ctx(context).map(x -> {
                long day = x.server().overworld().getDayTime() / 24000L;
                return x.route().lastDay() < 0 || day - x.route().lastDay() >= days;
            }).orElse(false);
        }
    }

    public record TimeOfDay(int from, int to) implements Condition {
        public static final MapCodec<TimeOfDay> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("from", 6).forGetter(TimeOfDay::from),
                Codec.INT.optionalFieldOf("to", 18).forGetter(TimeOfDay::to))
                .apply(i, TimeOfDay::new));

        @Override
        public ConditionType<?> type() {
            return TIME.get();
        }

        public static int hour(MinecraftServer server) {
            return (int) (((server.overworld().getDayTime() + 6000L) % 24000L) / 1000L);
        }

        @Override
        public boolean test(ConditionContext context) {
            int h = (int) (((context.level().getDayTime() + 6000L) % 24000L) / 1000L);
            return from <= to ? h >= from && h < to : h >= from || h < to;
        }
    }

    public record CaravansOut(String compare, int value) implements Condition {
        public static final MapCodec<CaravansOut> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("compare", "<").forGetter(CaravansOut::compare),
                Codec.INT.optionalFieldOf("value", 1).forGetter(CaravansOut::value))
                .apply(i, CaravansOut::new));

        @Override
        public ConditionType<?> type() {
            return OUT.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return ctx(context).map(x -> {
                int n = 0;
                for (Journey j : Journeys.get(x.server()).from(x.home().id())) {
                    if (j.kind.equals(Caravans.KIND) && j.stage != Journey.Stage.DONE) {
                        n++;
                    }
                }
                return compare(n, compare, value);
            }).orElse(false);
        }
    }
}
