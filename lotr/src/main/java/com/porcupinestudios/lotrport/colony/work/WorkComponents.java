package com.porcupinestudios.lotrport.colony.work;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.ComponentType;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/** The Person and colony components of colony life: home, job, needs, purse, and the colony's stock and taxes. */
public final class WorkComponents {
    private WorkComponents() {}

    /** Where a citizen lives: a home building (one of its beds). */
    public record Home(UUID building) {
        public static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("building").forGetter(Home::building)).apply(i, Home::new));
    }

    /**
     * A citizen's job at a workplace. {@code skill} 0-100 grows with every day worked; {@code carry} is the part of an
     * item made but not yet whole, per item.
     */
    public record Job(ResourceLocation job, UUID building, double skill, long since, Map<ResourceLocation, Double> carry) {
        public static final Codec<Job> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("job").forGetter(Job::job),
                UUIDUtil.CODEC.fieldOf("building").forGetter(Job::building),
                Codec.DOUBLE.optionalFieldOf("skill", 0.0).forGetter(Job::skill),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(Job::since),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("carry", Map.of())
                        .forGetter(Job::carry))
                .apply(i, Job::new));

        public Job worked(double newSkill, Map<ResourceLocation, Double> newCarry) {
            return new Job(job, building, newSkill, since, Map.copyOf(newCarry));
        }
    }

    /**
     * How a citizen fares: food 0-20 (20 fed), happiness 0-100, days in a row unhappy, days unpaid (striking while
     * above 0).
     */
    public record Needs(double food, double happiness, int unhappyDays, int unpaid) {
        public static final Needs NEW = new Needs(20, 60, 0, 0);
        public static final Codec<Needs> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("food", 20.0).forGetter(Needs::food),
                Codec.DOUBLE.optionalFieldOf("happiness", 60.0).forGetter(Needs::happiness),
                Codec.INT.optionalFieldOf("unhappy_days", 0).forGetter(Needs::unhappyDays),
                Codec.INT.optionalFieldOf("unpaid", 0).forGetter(Needs::unpaid))
                .apply(i, Needs::new));

        public Needs withFood(double f) {
            return new Needs(f, happiness, unhappyDays, unpaid);
        }

        public Needs withHappiness(double h, int days) {
            return new Needs(food, h, days, unpaid);
        }

        public Needs withUnpaid(int days) {
            return new Needs(food, happiness, unhappyDays, days);
        }
    }

    /** A citizen's own coins: wages in, taxes and tavern spending out. */
    public record Purse(long coins) {
        public static final Codec<Purse> CODEC = Codec.LONG.xmap(Purse::new, Purse::coins);
        public static final Purse EMPTY = new Purse(0);
    }

    /**
     * Goods the colony holds outside its chests (made while nobody could put them away); moved into storehouses when
     * they are loaded. Item id to count.
     */
    public record Stock(Map<ResourceLocation, Integer> items) {
        public static final Stock EMPTY = new Stock(Map.of());
        public static final Codec<Stock> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                .xmap(Stock::new, Stock::items);

        public Stock {
            items = Map.copyOf(items);
        }

        public int count(ResourceLocation item) {
            return items.getOrDefault(item, 0);
        }
    }

    /** The colony's economy settings: head tax a citizen pays a day, and a bonus on every wage. */
    public record Economy(int headTax, int wageBonus) {
        public static final Economy DEFAULT = new Economy(1, 0);
        public static final Codec<Economy> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("head_tax", 1).forGetter(Economy::headTax),
                Codec.INT.optionalFieldOf("wage_bonus", 0).forGetter(Economy::wageBonus))
                .apply(i, Economy::new));
    }

    public static final ComponentType<Home> HOME = ComponentType.register(LotrPort.id("home"), Home.CODEC);
    public static final ComponentType<Job> JOB = ComponentType.register(LotrPort.id("job"), Job.CODEC);
    public static final ComponentType<Needs> NEEDS = ComponentType.register(LotrPort.id("needs"), Needs.CODEC);
    public static final ComponentType<Purse> PURSE = ComponentType.register(LotrPort.id("purse"), Purse.CODEC);
    public static final ComponentType<Stock> STOCK = ComponentType.register(LotrPort.id("stock"), Stock.CODEC);
    public static final ComponentType<Economy> ECONOMY = ComponentType.register(LotrPort.id("economy"),
            Economy.CODEC);

    /** Loads the class (registers the types) from the mod constructor. */
    public static void init() {}
}
