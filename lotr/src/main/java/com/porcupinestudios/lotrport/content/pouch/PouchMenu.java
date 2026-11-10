package com.porcupinestudios.lotrport.content.pouch;

import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * An open pouch (LOTRContainerPouch's layout: its rows of 9 at y 30, the player's inventory at y 98, hotbar 156).
 * Changes are written back to the pouch item at once; the pouch itself can't be moved while open.
 */
public class PouchMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    /** The player's inventory slot the pouch is in ({@link Pouches#OFFHAND} for the off hand). */
    public final int pouchSlot;
    private final SimpleContainer contents;
    public final int rows;

    public PouchMenu(int id, Inventory inventory, int pouchSlot) {
        super(Pouches.MENU.get(), id);
        this.inventory = inventory;
        this.pouchSlot = pouchSlot;
        ItemStack pouch = pouch();
        NonNullList<ItemStack> items = PouchItem.contents(pouch);
        rows = Math.max(1, items.size() / 9);
        contents = new SimpleContainer(items.size()) {
            @Override
            public void setChanged() {
                super.setChanged();
                PouchItem.store(pouch(), getItems());
            }
        };
        for (int i = 0; i < items.size(); i++) {
            contents.setItem(i, items.get(i));
        }
        for (int i = 0; i < items.size(); i++) {
            addSlot(new Slot(contents, i, 8 + i % 9 * 18, 30 + i / 9 * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !(stack.getItem() instanceof PouchItem); // LOTRSlotPouch
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 98 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 156));
        }
    }

    public ItemStack pouch() {
        return pouchSlot == Pouches.OFFHAND ? inventory.offhand.get(0) : inventory.getItem(pouchSlot);
    }

    public int size() {
        return contents.getContainerSize();
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot >= 0 && slot < slots.size() && slots.get(slot).container == inventory
                && slots.get(slot).getContainerSlot() == pouchSlot) {
            return; // the open pouch stays put
        }
        if (type == ClickType.SWAP && button == pouchSlot) {
            return;
        }
        super.clicked(slot, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int n = size();
        if (index < n) {
            if (!moveItemStackTo(stack, n, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof PouchItem || !moveItemStackTo(stack, 0, n, false)) {
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
    public boolean stillValid(Player player) {
        return pouch().getItem() instanceof PouchItem;
    }

    /** LOTR's rename field: the new name, or none to clear it. */
    public void rename(String name) {
        ItemStack pouch = pouch();
        if (name.isBlank()) {
            pouch.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        } else {
            pouch.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(name.length() > 50 ? name.substring(0, 50) : name));
        }
    }
}
