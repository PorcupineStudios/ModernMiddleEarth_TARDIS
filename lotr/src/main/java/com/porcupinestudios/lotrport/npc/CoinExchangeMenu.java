package com.porcupinestudios.lotrport.npc;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * LOTR's coin exchange (LOTRContainerCoinExchange): coins go in the middle; the left arrow breaks them into ten of the
 * next smaller coin (left slot), the right arrow makes every ten into one of the next larger (right slot). Free.
 * The client draws it with {@code client/CoinExchangeScreen}.
 */
public class CoinExchangeMenu extends AbstractContainerMenu {
    public static final int INPUT = 0;
    public static final int SMALLER = 1;
    public static final int LARGER = 2;
    private static final int INVENTORY = 3;
    private static final int[] VALUES = { 1, 10, 100 };

    public final int npcId;
    private final LotrNpc npc;
    private final SimpleContainer coins = new SimpleContainer(3);

    public CoinExchangeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, buf.readVarInt());
    }

    public CoinExchangeMenu(int id, Inventory inventory, LotrNpc npc) {
        this(id, inventory, npc, npc.getId());
    }

    private CoinExchangeMenu(int id, Inventory inventory, LotrNpc npc, int npcId) {
        super(Traders.EXCHANGE_MENU.get(), id);
        this.npc = npc;
        this.npcId = npcId;
        addSlot(new Slot(coins, INPUT, 80, 46) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return value(stack) > 0;
            }
        });
        addSlot(new Result(coins, SMALLER, 26, 46));
        addSlot(new Result(coins, LARGER, 134, 46));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 106 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 164));
        }
    }

    /** One coin's value, or 0 if it isn't a coin. */
    public static int value(ItemStack stack) {
        return stack.isEmpty() ? 0 : Coins.value(stack) / stack.getCount();
    }

    private static Item coin(int value) {
        return Coins.coin(value);
    }

    /** Whether the left arrow would do anything now. */
    public boolean canBreak() {
        return breakCount() > 0;
    }

    /** Whether the right arrow would do anything now. */
    public boolean canJoin() {
        return joinCount() > 0;
    }

    private int breakCount() {
        ItemStack in = coins.getItem(INPUT);
        int v = value(in);
        if (v <= VALUES[0]) {
            return 0;
        }
        ItemStack out = coins.getItem(SMALLER);
        if (!out.isEmpty() && !out.is(coin(v / 10))) {
            return 0;
        }
        return Math.min(in.getCount(), (64 - out.getCount()) / 10);
    }

    private int joinCount() {
        ItemStack in = coins.getItem(INPUT);
        int v = value(in);
        if (v <= 0 || v >= VALUES[VALUES.length - 1]) {
            return 0;
        }
        ItemStack out = coins.getItem(LARGER);
        if (!out.isEmpty() && !out.is(coin(v * 10))) {
            return 0;
        }
        return Math.min(in.getCount() / 10, 64 - out.getCount());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        ItemStack in = coins.getItem(INPUT);
        int v = value(in);
        int n = id == 0 ? breakCount() : joinCount();
        if (n <= 0) {
            return false;
        }
        int slot = id == 0 ? SMALLER : LARGER;
        Item to = coin(id == 0 ? v / 10 : v * 10);
        in.shrink(id == 0 ? n : n * 10);
        ItemStack out = coins.getItem(slot);
        coins.setItem(slot, new ItemStack(to, out.getCount() + (id == 0 ? n * 10 : n)));
        coins.setChanged();
        if (npc != null) {
            npc.traded();
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < INVENTORY) {
            if (!moveItemStackTo(stack, INVENTORY, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (value(stack) > 0) {
            if (!moveItemStackTo(stack, INPUT, INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, coins);
    }

    @Override
    public boolean stillValid(Player player) {
        return npc == null || Traders.inReach(player, npc);
    }

    /** Take-only. */
    static class Result extends Slot {
        Result(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
