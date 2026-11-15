package com.porcupinestudios.lotrport.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** Builds a trader's offers from its trade pools, for the vanilla trading screen. Prices are in silver coins. */
public final class NpcTrading {
    private NpcTrading() {}

    /** The trader's offers: the same for one trader every time (picked with its UUID), different between traders. */
    public static MerchantOffers offers(LotrNpc npc) {
        MerchantOffers offers = new MerchantOffers();
        NpcType type = npc.npcType();
        if (type == null) {
            return offers;
        }
        Random random = new Random(npc.getUUID().getLeastSignificantBits());
        type.buy().flatMap(NpcData::trades).ifPresent(pool -> add(offers, pool, random));
        type.sell().flatMap(NpcData::trades).ifPresent(pool -> add(offers, pool, random));
        if (!offers.isEmpty()) {
            exchange(offers);
        }
        return offers;
    }

    /**
     * LOTR's coin exchange (LOTRContainerCoinExchange, a trader's second screen): a coin for ten of the next smaller,
     * or ten for one of the next larger, at no cost. Here they are the trader's last offers, never used up.
     */
    private static void exchange(MerchantOffers offers) {
        for (int value : new int[] { 10, 100 }) {
            Item big = Coins.coin(value);
            Item small = Coins.coin(value / 10);
            offers.add(new MerchantOffer(new ItemCost(big, 1), new ItemStack(small, 10), Integer.MAX_VALUE, 0, 0f));
            offers.add(new MerchantOffer(new ItemCost(small, 10), new ItemStack(big, 1), Integer.MAX_VALUE, 0, 0f));
        }
    }

    private static void add(MerchantOffers offers, TradePool pool, Random random) {
        List<TradePool.Entry> entries = new ArrayList<>(pool.entries());
        if (pool.offers() > 0 && pool.offers() < entries.size()) {
            Collections.shuffle(entries, random);
            entries = entries.subList(0, pool.offers());
        }
        for (TradePool.Entry e : entries) {
            Item item = item(e.item());
            if (item == Items.AIR || e.cost() <= 0) {
                continue;
            }
            int count = Math.max(1, Math.min(e.count(), item.getDefaultMaxStackSize()));
            if (pool.kind() == TradePool.Kind.BUY) {
                List<ItemStack> price = Coins.price(e.cost());
                ItemCost a = new ItemCost(price.get(0).getItem(), price.get(0).getCount());
                Optional<ItemCost> b = price.size() > 1
                        ? Optional.of(new ItemCost(price.get(1).getItem(), price.get(1).getCount()))
                        : Optional.empty();
                offers.add(new MerchantOffer(a, b, new ItemStack(item, count), pool.uses(), 0, 0f));
            } else {
                offers.add(new MerchantOffer(new ItemCost(item, count), Coins.payment(e.cost()), pool.uses(), 0,
                        0f));
            }
        }
    }

    private static Item item(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }
}
