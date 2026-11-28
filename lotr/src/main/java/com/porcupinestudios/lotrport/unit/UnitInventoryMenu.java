package com.porcupinestudios.lotrport.unit;

import com.mojang.datafixers.util.Pair;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A hired unit's gear (LOTR's LOTRContainerHiredWarriorInventory): its armour and both hands, beside the player's
 * inventory. Edits the unit's own equipment, so it fights with what you give it; what you give it always drops if it
 * dies. Only the player who may command it, within {@link #REACH} blocks, keeps it open.
 */
public class UnitInventoryMenu extends AbstractContainerMenu {
    public static final double REACH = 8;
    /** Where the hand slots sit (the screen draws their frames there). */
    public static final int HAND_X = 98;
    /** The unit's slots, top to bottom: armour, then main hand and off hand. */
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final ResourceLocation[] EMPTY = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, null, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD};

    @Nullable
    private final LotrNpc npc;
    private final int npcId;

    /** Client side, from the opening packet. */
    public UnitInventoryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, buf.readVarInt());
    }

    /** Server side. */
    public UnitInventoryMenu(int id, Inventory inventory, LotrNpc npc) {
        this(id, inventory, npc, npc.getId());
    }

    private UnitInventoryMenu(int id, Inventory inventory, @Nullable LotrNpc npc, int npcId) {
        super(UnitNet.INVENTORY_MENU.get(), id);
        this.npc = npc;
        this.npcId = npcId;
        LotrNpc body = npc != null ? npc
                : inventory.player.level().getEntity(npcId) instanceof LotrNpc n ? n : null;
        Container gear = new Gear(body);
        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            ResourceLocation empty = EMPTY[i];
            int x = i < 4 ? 8 : HAND_X;
            int y = i < 4 ? 8 + i * 18 : 44 + (i - 4) * 18;
            addSlot(new Slot(gear, i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return slot.getType() == EquipmentSlot.Type.HAND || body == null
                            || body.getEquipmentSlotForItem(stack) == slot;
                }

                @Override
                public int getMaxStackSize() {
                    return slot.getType() == EquipmentSlot.Type.HAND ? super.getMaxStackSize() : 1;
                }

                @Override
                public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    return empty == null ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, empty);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    /** The unit, on the client too (for its picture). */
    @Nullable
    public LotrNpc unit(Player player) {
        return npc != null ? npc : player.level().getEntity(npcId) instanceof LotrNpc n ? n : null;
    }

    @Override
    public boolean stillValid(Player player) {
        if (npc == null) {
            return true; // the client: the server decides
        }
        return npc.isAlive() && player.distanceTo(npc) <= REACH && player instanceof ServerPlayer sp
                && Units.canCommand(sp, npc);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot from = slots.get(index);
        if (!from.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = from.getItem();
        ItemStack copy = stack.copy();
        int gear = SLOTS.length;
        if (index < gear) {
            if (!moveItemStackTo(stack, gear, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < gear && !moved; i++) { // armour to its slot, else the empty main hand
                Slot s = slots.get(i);
                if (!s.hasItem() && s.mayPlace(stack) && i <= 4) {
                    moved = moveItemStackTo(stack, i, i + 1, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        from.setChanged();
        if (stack.isEmpty()) {
            from.setByPlayer(ItemStack.EMPTY);
        }
        return copy;
    }

    /** The unit's equipment as a container; on the client (no unit found) it holds nothing. */
    private static final class Gear implements Container {
        @Nullable
        private final LotrNpc npc;

        Gear(@Nullable LotrNpc npc) {
            this.npc = npc;
        }

        @Override
        public int getContainerSize() {
            return SLOTS.length;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < SLOTS.length; i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            return npc == null ? ItemStack.EMPTY : npc.getItemBySlot(SLOTS[i]);
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            ItemStack stack = getItem(i);
            ItemStack taken = stack.split(count);
            setItem(i, stack);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            ItemStack stack = getItem(i);
            setItem(i, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            if (npc != null) {
                npc.setItemSlot(SLOTS[i], stack);
                npc.setDropChance(SLOTS[i], 2f); // the player's gear always comes back if the unit dies
            }
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < SLOTS.length; i++) {
                setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
