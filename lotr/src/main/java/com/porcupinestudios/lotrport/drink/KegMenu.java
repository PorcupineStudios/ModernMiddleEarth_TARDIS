package com.porcupinestudios.lotrport.drink;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The keg's screen, laid out as LOTR's barrel (210 x 221): ingredients in the 3x3's top two rows, water buckets in its
 * bottom row, the drink at the arrow's end, and the Start/Stop Brewing button (menu buttons 0 and 1).
 */
public class KegMenu extends AbstractContainerMenu {
    private final Container keg;
    private final ContainerData data;

    public KegMenu(int id, Inventory inventory) {
        // not capped at the drink's stack of 1: the drink view (slot 9) holds every serving
        this(id, inventory, new SimpleContainer(10) {
            @Override
            public int getMaxStackSize(ItemStack stack) {
                return getMaxStackSize();
            }
        }, new SimpleContainerData(3));
    }

    KegMenu(int id, Inventory inventory, Container keg, ContainerData data) {
        super(Drinks.KEG_MENU.get(), id);
        checkContainerSize(keg, 10);
        checkContainerDataCount(data, 3);
        this.keg = keg;
        this.data = data;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int x = 14 + col * 18;
                int y = 34 + row * 18;
                addSlot(row < 2 ? new Slot(keg, row * 3 + col, x, y) : new Slot(keg, row * 3 + col, x, y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.is(Items.WATER_BUCKET);
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
            }
        }
        addSlot(new Slot(keg, KegBlockEntity.DRINK_SLOT, 108, 52) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 25 + col * 18, 139 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 25 + col * 18, 197));
        }
        addDataSlots(data);
    }

    public int mode() {
        return data.get(0);
    }

    public int amount() {
        return data.get(1);
    }

    public int capacity() {
        return data.get(2);
    }

    /** The drink the keg holds, as many as there are whole servings. */
    public ItemStack drink() {
        return keg.getItem(KegBlockEntity.DRINK_SLOT);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (keg instanceof KegBlockEntity k) {
            return id == 0 ? k.startBrewing() : id == 1 && k.stopBrewing();
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index == KegBlockEntity.DRINK_SLOT || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerStart = KegBlockEntity.DRINK_SLOT + 1;
        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, stack.is(Items.WATER_BUCKET) ? 6 : 0,
                stack.is(Items.WATER_BUCKET) ? 9 : 6, false)) {
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
        return keg.stillValid(player);
    }
}
