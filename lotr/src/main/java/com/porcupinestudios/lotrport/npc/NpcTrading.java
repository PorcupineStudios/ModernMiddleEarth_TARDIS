package com.porcupinestudios.lotrport.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A trader's deals from its trade pools, as LOTR's trade screen shows them (LOTRTradeEntries): up to nine it sells
 * (the Buy row) and nine it buys (the Sell row), each with a price in silver coins and a number of uses.
 */
public final class NpcTrading {
    /** Slots in each row of the trade screen. */
    public static final int ROW = 9;

    private NpcTrading() {}

    /** One deal: {@code item} (its count is the lot size) for {@code cost} coins, {@code uses} times. */
    public static final class Deal {
        public static final StreamCodec<RegistryFriendlyByteBuf, Deal> CODEC = StreamCodec.of((buf, d) -> {
            ItemStack.STREAM_CODEC.encode(buf, d.item);
            buf.writeVarInt(d.cost);
            buf.writeVarInt(d.uses);
        }, buf -> new Deal(ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt()));

        public final ItemStack item;
        public final int cost;
        public int uses;

        public Deal(ItemStack item, int cost, int uses) {
            this.item = item;
            this.cost = cost;
            this.uses = uses;
        }

        public boolean available() {
            return uses > 0;
        }
    }

    /** The deals of one trader: the same for one trader every time (picked with its UUID), different between traders. */
    public record Deals(List<Deal> buy, List<Deal> sell) {
        public static final Deals NONE = new Deals(List.of(), List.of());

        public boolean isEmpty() {
            return buy.isEmpty() && sell.isEmpty();
        }

        /** The sell deal for {@code stack}, or null. */
        public Deal sellDeal(ItemStack stack) {
            for (Deal d : sell) {
                if (!stack.isEmpty() && stack.is(d.item.getItem())) {
                    return d;
                }
            }
            return null;
        }

        /** How many of {@code stack} a sale takes (whole lots only). */
        public int sellCount(ItemStack stack) {
            Deal d = sellDeal(stack);
            if (d == null || !d.available()) {
                return 0;
            }
            return stack.getCount() / d.item.getCount() * d.item.getCount();
        }

        /** The coins {@code stack} sells for. */
        public int sellValue(ItemStack stack) {
            Deal d = sellDeal(stack);
            return d == null ? 0 : sellCount(stack) / d.item.getCount() * d.cost;
        }
    }

    public static Deals deals(LotrNpc npc) {
        NpcType type = npc.npcType();
        if (type == null) {
            return Deals.NONE;
        }
        Random random = new Random(npc.getUUID().getLeastSignificantBits());
        List<Deal> buy = new ArrayList<>();
        List<Deal> sell = new ArrayList<>();
        type.buy().flatMap(NpcData::trades).ifPresent(pool -> add(pool, random, buy, sell));
        type.sell().flatMap(NpcData::trades).ifPresent(pool -> add(pool, random, buy, sell));
        return new Deals(buy.size() > ROW ? new ArrayList<>(buy.subList(0, ROW)) : buy,
                sell.size() > ROW ? new ArrayList<>(sell.subList(0, ROW)) : sell);
    }

    private static void add(TradePool pool, Random random, List<Deal> buy, List<Deal> sell) {
        List<TradePool.Entry> entries = new ArrayList<>(pool.entries());
        int max = pool.offers() > 0 ? Math.min(pool.offers(), ROW) : ROW;
        if (max < entries.size()) {
            Collections.shuffle(entries, random);
            entries = entries.subList(0, max);
        }
        for (TradePool.Entry e : entries) {
            Item item = BuiltInRegistries.ITEM.get(e.item());
            if (item == Items.AIR || e.cost() <= 0) {
                continue;
            }
            int count = Math.max(1, Math.min(e.count(), item.getDefaultMaxStackSize()));
            Deal d = new Deal(new ItemStack(item, count), e.cost(), pool.uses());
            (pool.kind() == TradePool.Kind.BUY ? buy : sell).add(d);
        }
    }
}
