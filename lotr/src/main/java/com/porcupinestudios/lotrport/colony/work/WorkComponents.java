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

    /** The Treasury Ledger's books: one line per colony day, newest last, at most {@link #BOOK_DAYS}. */
    public record Books(java.util.List<Entry> days) {
        public static final Books EMPTY = new Books(java.util.List.of());
        public static final Codec<Books> CODEC = Entry.CODEC.listOf().xmap(Books::new, Books::days);

        public Books {
            days = java.util.List.copyOf(days);
        }

        /** Adds to the day's line (making it if new) and records the balance after. */
        public Books note(long day, int taxes, int wages, int soldiers, long balance) {
            java.util.List<Entry> out = new java.util.ArrayList<>(days);
            Entry last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && last.day() == day) {
                out.set(out.size() - 1, new Entry(day, last.taxes() + taxes, last.wages() + wages,
                        last.soldiers() + soldiers, balance));
            } else {
                out.add(new Entry(day, taxes, wages, soldiers, balance));
            }
            return new Books(out.subList(Math.max(0, out.size() - BOOK_DAYS), out.size()));
        }
    }

    /** One day of the books: coins in from the head tax, out in wages and soldiers' pay, and the balance after. */
    public record Entry(long day, int taxes, int wages, int soldiers, long balance) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Entry::day),
                Codec.INT.optionalFieldOf("taxes", 0).forGetter(Entry::taxes),
                Codec.INT.optionalFieldOf("wages", 0).forGetter(Entry::wages),
                Codec.INT.optionalFieldOf("soldiers", 0).forGetter(Entry::soldiers),
                Codec.LONG.optionalFieldOf("balance", 0L).forGetter(Entry::balance))
                .apply(i, Entry::new));
    }

    public static final int BOOK_DAYS = 30;

    /** Notes a pay pass in the colony's books (the day is the colony day of {@code now}). */
    public static void book(com.porcupinestudios.lotrport.colony.Colonies colonies,
            com.porcupinestudios.lotrport.colony.Colony c, long now, int taxes, int wages, int soldiers, long balance) {
        long day = now / Math.max(1, com.porcupinestudios.lotrport.colony.ColonyData.settings().dayTicks());
        colonies.set(c, BOOKS, c.get(BOOKS).orElse(Books.EMPTY).note(day, taxes, wages, soldiers, balance));
    }

    public static final ComponentType<Books> BOOKS = ComponentType.register(LotrPort.id("books"), Books.CODEC);

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
