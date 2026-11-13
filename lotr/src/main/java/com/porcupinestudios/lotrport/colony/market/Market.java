package com.porcupinestudios.lotrport.colony.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.work.Stores;
import com.porcupinestudios.lotrport.journey.Caravans;
import com.porcupinestudios.lotrport.npc.Coins;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * The colony market (Aaron 2026-11-12: the colony is self-contained). Players can't put goods into or take goods out
 * of the colony's stores for free ({@link StoreLock}); they buy from the stores and sell to them here, at prices set
 * by supply and demand, paid from and into the treasury. What the colony takes from players is capped per item per
 * day ({@link #limit}): the cap is set per item by the colony, up to a ceiling that research points raise
 * ({@link #upgrade}). Delivery hatches (barrels in a market building) sell what hoppers feed them the same way.
 * Numbers: {@code colony_market/settings.json}.
 */
public final class Market {
    /**
     * @param baseLimit the daily ceiling per item at level 0
     * @param limitPerLevel what each upgrade adds to it
     * @param maxLevel highest upgrade
     * @param upgradeCost research points for the first upgrade
     * @param upgradeGrowth each further upgrade costs this many times the last
     * @param pointsPerResearcher research points a researcher earns a day (times their skill)
     * @param target the stock a colony wants of an item (price is base at the target)
     * @param curve how hard the price follows (target / stock)
     * @param minFactor cheapest, times base
     * @param maxFactor dearest, times base
     * @param sellShare what the colony pays for an item, as a share of its own price
     * @param defaultPrice base price of an item not listed
     * @param prices base prices in coins
     * @param targets per-item targets
     */
    public record Settings(int baseLimit, int limitPerLevel, int maxLevel, double upgradeCost, double upgradeGrowth,
            double pointsPerResearcher, int target, double curve, double minFactor, double maxFactor, double sellShare,
            double defaultPrice, Map<ResourceLocation, Double> prices, Map<ResourceLocation, Integer> targets) {
        public static final Settings DEFAULT = new Settings(16, 16, 10, 20, 1.5, 1, 64, 0.6, 0.25, 4, 0.5, 2,
                Map.of(), Map.of());
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("base_limit", DEFAULT.baseLimit).forGetter(Settings::baseLimit),
                Codec.INT.optionalFieldOf("limit_per_level", DEFAULT.limitPerLevel).forGetter(Settings::limitPerLevel),
                Codec.INT.optionalFieldOf("max_level", DEFAULT.maxLevel).forGetter(Settings::maxLevel),
                Codec.DOUBLE.optionalFieldOf("upgrade_cost", DEFAULT.upgradeCost).forGetter(Settings::upgradeCost),
                Codec.DOUBLE.optionalFieldOf("upgrade_growth", DEFAULT.upgradeGrowth).forGetter(Settings::upgradeGrowth),
                Codec.DOUBLE.optionalFieldOf("points_per_researcher", DEFAULT.pointsPerResearcher)
                        .forGetter(Settings::pointsPerResearcher),
                Codec.INT.optionalFieldOf("target", DEFAULT.target).forGetter(Settings::target),
                Codec.DOUBLE.optionalFieldOf("curve", DEFAULT.curve).forGetter(Settings::curve),
                Codec.DOUBLE.optionalFieldOf("min_factor", DEFAULT.minFactor).forGetter(Settings::minFactor),
                Codec.DOUBLE.optionalFieldOf("max_factor", DEFAULT.maxFactor).forGetter(Settings::maxFactor),
                Codec.DOUBLE.optionalFieldOf("sell_share", DEFAULT.sellShare).forGetter(Settings::sellShare),
                Codec.DOUBLE.optionalFieldOf("default_price", DEFAULT.defaultPrice).forGetter(Settings::defaultPrice),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("prices", Map.of())
                        .forGetter(Settings::prices),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("targets", Map.of())
                        .forGetter(Settings::targets))
                .apply(i, Settings::new));
    }

    /**
     * A colony's market.
     *
     * @param level upgrades bought with research points
     * @param limits the daily cap the colony set per item (unset: the ceiling)
     * @param taken what it took from players today, per item
     * @param day the day {@code taken} counts
     * @param accounts coins owed to players for hatch deliveries (collected at the market)
     * @param hatches who placed each delivery hatch (block pos as a long, in text)
     */
    public record State(int level, Map<ResourceLocation, Integer> limits, Map<ResourceLocation, Integer> taken,
            long day, Map<UUID, Long> accounts, Map<String, UUID> hatches) {
        public static final State EMPTY = new State(0, Map.of(), Map.of(), 0, Map.of(), Map.of());
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("level", 0).forGetter(State::level),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("limits", Map.of())
                        .forGetter(State::limits),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("taken", Map.of())
                        .forGetter(State::taken),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(State::day),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("accounts", Map.of())
                        .forGetter(State::accounts),
                Codec.unboundedMap(Codec.STRING, UUIDUtil.CODEC).optionalFieldOf("hatches", Map.of())
                        .forGetter(State::hatches))
                .apply(i, State::new));

        State today(long now) {
            return now == day ? this : new State(level, limits, Map.of(), now, accounts, hatches);
        }
    }

    public static final ComponentType<State> STATE = ComponentType.register(LotrPort.id("market"), State.CODEC);
    private static final CodecJsonLoader<Settings> SETTINGS = new CodecJsonLoader<>("colony_market", Settings.CODEC);

    private Market() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(SETTINGS));
        Research.register();
        StoreLock.register();
        Hatches.register();
    }

    public static Settings settings() {
        return SETTINGS.entries().getOrDefault(LotrPort.id("settings"), Settings.DEFAULT);
    }

    static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    /** The colony's market as of today (yesterday's intake forgotten). */
    public static State state(MinecraftServer server, Colony c) {
        Colony now = Colonies.get(server).get(c.id()).orElse(c);
        return now.get(STATE).orElse(State.EMPTY).today(day(server));
    }

    static void save(MinecraftServer server, Colony c, State s) {
        Colony now = Colonies.get(server).get(c.id()).orElse(c);
        Colonies.get(server).set(now, STATE, s);
    }

    // ---------------------------------------------------------------- limits and upgrades

    /** The highest daily cap the colony may set per item at this level. */
    public static int ceiling(int level) {
        Settings s = settings();
        return s.baseLimit() + s.limitPerLevel() * Math.max(0, Math.min(level, s.maxLevel()));
    }

    /** Research points the next upgrade costs (or -1 at the top). */
    public static double upgradeCost(int level) {
        Settings s = settings();
        return level >= s.maxLevel() ? -1 : Math.ceil(s.upgradeCost() * Math.pow(s.upgradeGrowth(), level));
    }

    /** Today's cap for an item: what the colony set, never above the ceiling. */
    public static int limit(State st, ResourceLocation item) {
        int top = ceiling(st.level());
        return Math.min(top, st.limits().getOrDefault(item, top));
    }

    /** What the colony still takes of an item today. */
    public static int room(State st, ResourceLocation item) {
        return Math.max(0, limit(st, item) - st.taken().getOrDefault(item, 0));
    }

    /** Sets an item's daily cap (clamped to the ceiling; the ceiling itself clears it). */
    public static void setLimit(MinecraftServer server, Colony c, ResourceLocation item, int n) {
        State st = state(server, c);
        Map<ResourceLocation, Integer> limits = new LinkedHashMap<>(st.limits());
        int v = Math.max(0, Math.min(n, ceiling(st.level())));
        if (v == ceiling(st.level())) {
            limits.remove(item);
        } else {
            limits.put(item, v);
        }
        save(server, c, new State(st.level(), limits, st.taken(), st.day(), st.accounts(), st.hatches()));
    }

    public record Answer(boolean ok, Component message) {}

    /** Spends research points on the next level: every item's ceiling rises. */
    public static Answer upgrade(MinecraftServer server, Colony c) {
        State st = state(server, c);
        double cost = upgradeCost(st.level());
        if (cost < 0) {
            return new Answer(false, Component.literal("The market is as large as it gets"));
        }
        if (!Research.spend(server, c, cost)) {
            return new Answer(false, Component.literal("Needs " + (int) cost + " research points (has "
                    + (int) Research.points(Colonies.get(server).get(c.id()).orElse(c)) + ")"));
        }
        st = state(server, c);
        save(server, c, new State(st.level() + 1, st.limits(), st.taken(), st.day(), st.accounts(), st.hatches()));
        return new Answer(true, Component.literal("The market takes up to " + ceiling(st.level() + 1)
                + " of each item a day"));
    }

    // ---------------------------------------------------------------- prices

    public static double base(ResourceLocation item) {
        Settings s = settings();
        return s.prices().getOrDefault(item, s.defaultPrice());
    }

    public static int target(ResourceLocation item) {
        Settings s = settings();
        return s.targets().getOrDefault(item, s.target());
    }

    /** What the colony asks for one, with this much in its stores. */
    public static double unitPrice(ResourceLocation item, int stock) {
        Settings s = settings();
        double f = Math.pow((double) target(item) / Math.max(1, stock), s.curve());
        return base(item) * Math.max(s.minFactor(), Math.min(s.maxFactor(), f));
    }

    /** What the colony pays for one, with this much in its stores. */
    public static double unitBid(ResourceLocation item, int stock) {
        return unitPrice(item, stock) * settings().sellShare();
    }

    /** Coins for buying n from a stock (each one raises the price of the next), rounded up. */
    public static long buyCost(ResourceLocation item, int stock, int n) {
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += unitPrice(item, stock - i);
        }
        return (long) Math.ceil(sum - 1e-9);
    }

    /** Coins for selling n into a stock (each one lowers the next), rounded down. */
    public static long sellPay(ResourceLocation item, int stock, int n) {
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += unitBid(item, stock + i);
        }
        return (long) Math.floor(sum + 1e-9);
    }

    public static int stock(MinecraftServer server, Colony c, ResourceLocation item) {
        return new Stores(server, server.getLevel(c.dimension()), c).count(item, null);
    }

    static long treasury(MinecraftServer server, Colony c) {
        Colony now = Colonies.get(server).get(c.id()).orElse(c);
        return now.get(CoreComponents.TREASURY).orElse(CoreComponents.Treasury.EMPTY).coins();
    }

    static void addTreasury(MinecraftServer server, Colony c, long n) {
        Colony now = Colonies.get(server).get(c.id()).orElse(c);
        Colonies.get(server).set(now, CoreComponents.TREASURY, new CoreComponents.Treasury(treasury(server, now) + n));
    }

    /** What the colony won't sell below: its caravan floors. */
    static int floor(Colony c, ResourceLocation item) {
        return Caravans.book(c).keep().getOrDefault(item.toString(), 0);
    }

    static String name(ResourceLocation item) {
        return new ItemStack(BuiltInRegistries.ITEM.get(item)).getHoverName().getString();
    }

    // ---------------------------------------------------------------- buying and selling

    /** A player buys up to n from the stores (never below the colony's floor) with their coins. */
    public static Answer buy(ServerPlayer player, Colony c, ResourceLocation item, int n) {
        MinecraftServer server = player.server;
        Stores stores = new Stores(server, server.getLevel(c.dimension()), c);
        int have = stores.count(item, null);
        int can = Math.min(n, have - floor(c, item));
        if (can <= 0) {
            return new Answer(false, Component.literal("The colony has no " + name(item) + " to spare"));
        }
        long cost = buyCost(item, have, can);
        while (can > 0 && Coins.count(player) < cost) {
            can--;
            cost = buyCost(item, have, can);
        }
        if (can <= 0 || !Coins.pay(player, (int) Math.min(Integer.MAX_VALUE, cost))) {
            return new Answer(false, Component.literal("You can't pay for any (one costs "
                    + buyCost(item, have, 1) + ")"));
        }
        int got = stores.take(item, can, null);
        stores.save();
        ItemStack out = new ItemStack(BuiltInRegistries.ITEM.get(item), got);
        if (!player.getInventory().add(out)) {
            player.drop(out, false);
        }
        addTreasury(server, c, cost);
        return new Answer(true, Component.literal("Bought " + got + " " + name(item) + " for " + cost + " coins"));
    }

    /**
     * Takes up to n of an item into the stores within today's cap and what the treasury can pay; returns how many,
     * and pays them to {@code pay} (coins into a hand, or an account).
     */
    static int intake(MinecraftServer server, Colony c, ResourceLocation item, int n, java.util.function.LongConsumer pay) {
        State st = state(server, c);
        Stores stores = new Stores(server, server.getLevel(c.dimension()), c);
        int have = stores.count(item, null);
        int can = Math.min(n, room(st, item));
        long coins = treasury(server, c);
        while (can > 0 && sellPay(item, have, can) > coins) {
            can--;
        }
        if (can <= 0) {
            return 0;
        }
        long paid = sellPay(item, have, can);
        stores.put(item, can, null);
        stores.save();
        addTreasury(server, c, -paid);
        Map<ResourceLocation, Integer> taken = new LinkedHashMap<>(st.taken());
        taken.merge(item, can, Integer::sum);
        st = state(server, c);
        save(server, c, new State(st.level(), st.limits(), taken, st.day(), st.accounts(), st.hatches()));
        pay.accept(paid);
        return can;
    }

    /** A player sells up to n of an item from their inventory (plain stacks only). */
    public static Answer sell(ServerPlayer player, Colony c, ResourceLocation item, int n) {
        MinecraftServer server = player.server;
        Inventory inv = player.getInventory();
        int carried = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (plain(s, item)) {
                carried += s.getCount();
            }
        }
        int want = Math.min(n, carried);
        if (want <= 0) {
            return new Answer(false, Component.literal("You carry no " + name(item)));
        }
        State st = state(server, c);
        if (room(st, item) <= 0) {
            return new Answer(false, Component.literal("The colony takes no more " + name(item) + " today (limit "
                    + limit(st, item) + ")"));
        }
        long[] paid = {0};
        int sold = intake(server, c, item, want, v -> paid[0] = v);
        if (sold <= 0) {
            return new Answer(false, Component.literal("The treasury can't pay for any"));
        }
        int left = sold;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (plain(s, item)) {
                int k = Math.min(left, s.getCount());
                s.shrink(k);
                left -= k;
            }
        }
        Coins.give(player, (int) paid[0]);
        return new Answer(true, Component.literal("Sold " + sold + " " + name(item) + " for " + paid[0] + " coins"));
    }

    static boolean plain(ItemStack s, ResourceLocation item) {
        return !s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(item)
                && s.getComponentsPatch().isEmpty();
    }

    /** Pays out what hatch deliveries earned this player. */
    public static Answer collect(ServerPlayer player, Colony c) {
        State st = state(player.server, c);
        long owed = st.accounts().getOrDefault(player.getUUID(), 0L);
        if (owed <= 0) {
            return new Answer(false, Component.literal("Nothing is owed to you"));
        }
        Map<UUID, Long> acc = new LinkedHashMap<>(st.accounts());
        acc.remove(player.getUUID());
        save(player.server, c, new State(st.level(), st.limits(), st.taken(), st.day(), acc, st.hatches()));
        Coins.give(player, (int) Math.min(Integer.MAX_VALUE, owed));
        return new Answer(true, Component.literal("Collected " + owed + " coins for your deliveries"));
    }

    static void credit(MinecraftServer server, Colony c, UUID who, long coins) {
        if (coins <= 0) {
            return;
        }
        State st = state(server, c);
        Map<UUID, Long> acc = new LinkedHashMap<>(st.accounts());
        acc.merge(who, coins, Long::sum);
        save(server, c, new State(st.level(), st.limits(), st.taken(), st.day(), acc, st.hatches()));
    }
}
