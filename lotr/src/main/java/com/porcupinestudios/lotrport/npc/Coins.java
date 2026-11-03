package com.porcupinestudios.lotrport.npc;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** LOTR's silver coins (1, 10 and 100): counting, paying with change, and splitting a price into stacks. */
public final class Coins {
    /** Coin values, largest first, with their item ids. */
    private static final int[] VALUES = { 100, 10, 1 };
    private static final String[] IDS = { "silver_coin_100", "silver_coin_10", "silver_coin" };

    private Coins() {}

    public static Item coin(int value) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i] == value) {
                return BuiltInRegistries.ITEM.get(LotrPort.id(IDS[i]));
            }
        }
        throw new IllegalArgumentException("no coin worth " + value);
    }

    public static int value(ItemStack stack) {
        for (int i = 0; i < VALUES.length; i++) {
            if (stack.is(coin(VALUES[i]))) {
                return VALUES[i] * stack.getCount();
            }
        }
        return 0;
    }

    public static int count(Player player) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            total += value(inv.getItem(i));
        }
        return total;
    }

    /** Takes {@code amount} from the player's coins, giving change. False (and nothing taken) if they have too few. */
    public static boolean pay(Player player, int amount) {
        if (count(player) < amount) {
            return false;
        }
        Inventory inv = player.getInventory();
        int taken = 0;
        // smallest coins first, so change is rarely needed
        for (int c = VALUES.length - 1; c >= 0 && taken < amount; c--) {
            for (int i = 0; i < inv.getContainerSize() && taken < amount; i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.is(coin(VALUES[c]))) {
                    continue;
                }
                int need = (amount - taken + VALUES[c] - 1) / VALUES[c];
                int use = Math.min(need, stack.getCount());
                stack.shrink(use);
                taken += use * VALUES[c];
            }
        }
        give(player, taken - amount);
        inv.setChanged();
        return true;
    }

    /** Gives {@code amount} in as few coins as possible. */
    public static void give(Player player, int amount) {
        for (ItemStack stack : stacks(amount)) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /** {@code amount} as coin stacks, largest coins first. */
    public static List<ItemStack> stacks(int amount) {
        List<ItemStack> out = new ArrayList<>();
        for (int v : VALUES) {
            int n = amount / v;
            amount -= n * v;
            while (n > 0) {
                int s = Math.min(n, 64);
                out.add(new ItemStack(coin(v), s));
                n -= s;
            }
        }
        return out;
    }

    /**
     * A price as at most two coin stacks, for a trade screen slot pair. Prices that don't fit exactly are rounded up
     * to the next 10 (only above 640 coins, where 64 small coins can't make up the rest).
     */
    public static List<ItemStack> price(int amount) {
        if (amount <= 64) {
            return List.of(new ItemStack(coin(1), Math.max(1, amount)));
        }
        if (amount <= 64 * 10 + 9 && amount % 10 <= 9 && amount / 10 <= 64) {
            return pair(10, amount / 10, 1, amount % 10);
        }
        int hundreds = Math.min(64, amount / 100);
        int rest = amount - hundreds * 100;
        return pair(100, hundreds, 10, Math.min(64, (rest + 9) / 10));
    }

    /** A payment as one coin stack (a trade screen has one result slot), rounded down to fit. */
    public static ItemStack payment(int amount) {
        if (amount <= 64) {
            return new ItemStack(coin(1), Math.max(1, amount));
        }
        if (amount <= 640) {
            return new ItemStack(coin(10), amount / 10);
        }
        return new ItemStack(coin(100), Math.min(64, amount / 100));
    }

    private static List<ItemStack> pair(int a, int countA, int b, int countB) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(coin(a), countA));
        if (countB > 0) {
            out.add(new ItemStack(coin(b), countB));
        }
        return out;
    }
}
