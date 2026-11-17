package com.porcupinestudios.lotrport.npc;

import com.porcupinestudios.lotrport.npc.NpcTrading.Deal;
import com.porcupinestudios.lotrport.npc.NpcTrading.Deals;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * LOTR's trade screen (LOTRContainerTrade): the Buy row (what the trader sells; click one to pay and take it), the
 * Sell row (what it buys, with prices), nine Sell offer slots and a coin button that sells them, then the inventory.
 * The client draws it with {@code client/TradeScreen}.
 */
public class TraderMenu extends AbstractContainerMenu {
    public static final int BUY = 0;
    public static final int SELL = 9;
    public static final int OFFERS = 18;
    public static final int INVENTORY = 27;
    /** {@link #clickMenuButton} id of the coin button. */
    public static final int SELL_BUTTON = 0;

    public final int npcId;
    public final Component title;
    public final Deals deals;
    private final LotrNpc npc;
    private final SimpleContainer offers = new SimpleContainer(NpcTrading.ROW);
    /** Bits: which buy (low 9) and sell (next 9) deals still have uses; two shorts, as data slots sync shorts. */
    private final DataSlot buyOpen = DataSlot.standalone();
    private final DataSlot sellOpen = DataSlot.standalone();

    /** Client side, from the opening packet. */
    public TraderMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                new Deals(read(buf), read(buf)));
    }

    /** Server side. */
    public TraderMenu(int id, Inventory inventory, LotrNpc npc) {
        this(id, inventory, npc, npc.getId(), npc.tradeTitle(), npc.deals());
    }

    private TraderMenu(int id, Inventory inventory, LotrNpc npc, int npcId, Component title, Deals deals) {
        super(Traders.TRADE_MENU.get(), id);
        this.npc = npc;
        this.npcId = npcId;
        this.title = title;
        this.deals = deals;
        SimpleContainer buy = new SimpleContainer(NpcTrading.ROW);
        SimpleContainer sell = new SimpleContainer(NpcTrading.ROW);
        for (int i = 0; i < NpcTrading.ROW; i++) {
            if (i < deals.buy().size()) {
                buy.setItem(i, deals.buy().get(i).item.copy());
            }
            if (i < deals.sell().size()) {
                sell.setItem(i, deals.sell().get(i).item.copy());
            }
        }
        for (int i = 0; i < NpcTrading.ROW; i++) {
            addSlot(new Shown(buy, i, 8 + i * 18, 40));
        }
        for (int i = 0; i < NpcTrading.ROW; i++) {
            addSlot(new Shown(sell, i, 8 + i * 18, 92));
        }
        for (int i = 0; i < NpcTrading.ROW; i++) {
            addSlot(new Slot(offers, i, 8 + i * 18, 141) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return TraderMenu.this.deals.sellDeal(stack) != null;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 188 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 246));
        }
        addDataSlot(buyOpen);
        addDataSlot(sellOpen);
        updateOpen();
    }

    private static List<Deal> read(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Deal> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(Deal.CODEC.decode(buf));
        }
        return out;
    }

    /** Writes what the client constructor reads. */
    public static void write(RegistryFriendlyByteBuf buf, LotrNpc npc) {
        buf.writeVarInt(npc.getId());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, npc.tradeTitle());
        for (List<Deal> list : List.of(npc.deals().buy(), npc.deals().sell())) {
            buf.writeVarInt(list.size());
            list.forEach(d -> Deal.CODEC.encode(buf, d));
        }
    }

    private void updateOpen() {
        if (npc != null) {
            buyOpen.set(bits(deals.buy()));
            sellOpen.set(bits(deals.sell()));
        }
    }

    private static int bits(List<Deal> list) {
        int b = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).available()) {
                b |= 1 << i;
            }
        }
        return b;
    }

    public boolean buyOpen(int i) {
        return (buyOpen.get() >> i & 1) != 0;
    }

    public boolean sellOpen(int i) {
        return (sellOpen.get() >> i & 1) != 0;
    }

    /** What the Sell offer slots fetch now. */
    public int sellPrice() {
        int total = 0;
        for (int i = 0; i < offers.getContainerSize(); i++) {
            ItemStack s = offers.getItem(i);
            Deal d = deals.sellDeal(s);
            if (d != null && sellOpen(deals.sell().indexOf(d))) {
                total += deals.sellValue(s);
            }
        }
        return total;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot >= BUY && slot < OFFERS) {
            if (slot < SELL && player instanceof ServerPlayer sp
                    && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                buy(sp, slot - BUY, type == ClickType.QUICK_MOVE);
            }
            return;
        }
        super.clicked(slot, button, type, player);
    }

    /** Pays for buy deal {@code i} and puts the goods on the cursor, or in the inventory on a shift-click. */
    private void buy(ServerPlayer player, int i, boolean toInventory) {
        if (i >= deals.buy().size()) {
            return;
        }
        Deal d = deals.buy().get(i);
        ItemStack goods = d.item.copy();
        ItemStack carried = getCarried();
        boolean fits = toInventory || carried.isEmpty() || ItemStack.isSameItemSameComponents(carried, goods)
                && carried.getCount() + goods.getCount() <= carried.getMaxStackSize();
        if (!d.available() || !fits || !Coins.pay(player, d.cost)) {
            return;
        }
        d.uses--;
        if (toInventory) {
            if (!player.getInventory().add(goods)) {
                player.drop(goods, false);
            }
        } else if (carried.isEmpty()) {
            setCarried(goods);
        } else {
            carried.grow(goods.getCount());
        }
        npc.traded();
        updateOpen();
        broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != SELL_BUTTON || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        int total = 0;
        List<Deal> used = new ArrayList<>();
        for (int i = 0; i < offers.getContainerSize(); i++) {
            ItemStack s = offers.getItem(i);
            Deal d = deals.sellDeal(s);
            if (d == null || !d.available()) {
                continue;
            }
            int n = deals.sellCount(s);
            if (n > 0) {
                total += deals.sellValue(s);
                s.shrink(n);
                if (!used.contains(d)) {
                    used.add(d);
                }
            }
        }
        if (total <= 0) {
            return false;
        }
        used.forEach(d -> d.uses--);
        offers.setChanged();
        Coins.give(sp, total);
        npc.traded();
        updateOpen();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index < OFFERS) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < INVENTORY) {
            if (!moveItemStackTo(stack, INVENTORY, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (deals.sellDeal(stack) != null) {
            if (!moveItemStackTo(stack, OFFERS, INVENTORY, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < INVENTORY + 27) {
            if (!moveItemStackTo(stack, INVENTORY + 27, slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, INVENTORY, INVENTORY + 27, false)) {
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
        clearContainer(player, offers);
    }

    @Override
    public boolean stillValid(Player player) {
        return npc == null || Traders.inReach(player, npc);
    }

    /** A slot that only shows an item: nothing goes in or comes out by hand. */
    static class Shown extends Slot {
        Shown(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    /** The trader as an entity, if the client has it. */
    public Entity npc(net.minecraft.world.level.Level level) {
        return level.getEntity(npcId);
    }
}
