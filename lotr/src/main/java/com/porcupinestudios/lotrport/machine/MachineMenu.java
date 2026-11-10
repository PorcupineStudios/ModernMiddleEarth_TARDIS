package com.porcupinestudios.lotrport.machine;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The screen of a forge, the hobbit oven, the millstone, the unsmeltery or the armour stand, with LOTR's slot layouts
 * (read from its LOTRContainer* classes) and textures. The client draws it with {@code client/MachineScreen}.
 */
public class MachineMenu extends AbstractContainerMenu {
    /** One screen: its slots (x, y per machine slot), height, where the player's inventory starts, and its gauges. */
    public enum Layout {
        FORGE("forge", "forge", 233, 151, new int[][] {
            { 53, 21 }, { 71, 21 }, { 89, 21 }, { 107, 21 }, { 53, 39 }, { 71, 39 }, { 89, 39 }, { 107, 39 },
            { 53, 85 }, { 71, 85 }, { 89, 85 }, { 107, 85 }, { 80, 129 } }, 8, 12, 12,
                new int[] { 80, 112 }, new int[] { 80, 58, 24, 1 }),
        OVEN("oven", "oven", 215, 133, new int[][] {
            { 8, 21 }, { 26, 21 }, { 44, 21 }, { 62, 21 }, { 80, 21 }, { 98, 21 }, { 116, 21 }, { 134, 21 },
            { 152, 21 }, { 8, 67 }, { 26, 67 }, { 44, 67 }, { 62, 67 }, { 80, 67 }, { 98, 67 }, { 116, 67 },
            { 134, 67 }, { 152, 67 }, { 80, 111 } }, 9, 18, 18, new int[] { 80, 94 }, new int[] { 80, 40, 24, 1 }),
        MILLSTONE("millstone", "millstone", 182, 100, new int[][] { { 84, 25 }, { 84, 71 } }, 1, 2, -1, null,
                new int[] { 85, 47, 14, 2 }),
        UNSMELTERY("unsmeltery", "unsmeltery", 176, 84, new int[][] { { 56, 17 }, { 56, 53 }, { 116, 35 } }, 2, 3, 1,
                new int[] { 56, 36 }, new int[] { 79, 34, 24, 0 }),
        ARMOR_STAND("armor_stand", "armor_stand", 189, 107,
                new int[][] { { 80, 21 }, { 80, 39 }, { 80, 57 }, { 80, 75 } }, 4, 4, -1, null, null);

        public final String id;
        public final ResourceLocation texture;
        public final int height;
        public final int inventoryY;
        final int[][] slots;
        final int outputStart;
        final int outputEnd;
        public final int fuel;
        /** Flame x, y (14 wide, 12 high, at u 176), or null. */
        public final int[] flame;
        /** Progress x, y, length, and direction: 0 right (24 x 16 at u 176, v 14), 1 down (16 wide), 2 down (14 wide, v 0). */
        public final int[] arrow;

        Layout(String id, String texture, int height, int inventoryY, int[][] slots, int outputStart, int outputEnd,
                int fuel, int[] flame, int[] arrow) {
            this.id = id;
            this.texture = LotrPort.id("textures/gui/" + texture + ".png");
            this.height = height;
            this.inventoryY = inventoryY;
            this.slots = slots;
            this.outputStart = outputStart;
            this.outputEnd = outputEnd;
            this.fuel = fuel;
            this.flame = flame;
            this.arrow = arrow;
        }

        public int size() {
            return slots.length;
        }

        boolean isOutput(int slot) {
            return this != ARMOR_STAND && slot >= outputStart && slot < outputEnd;
        }

        /** Hoppers: outputs from below, fuel from the sides, inputs from above (as a furnace). */
        int[] faceSlots(Direction side) {
            java.util.List<Integer> out = new java.util.ArrayList<>();
            for (int i = 0; i < size(); i++) {
                boolean take = side == Direction.DOWN ? isOutput(i) || i == fuel
                        : side == Direction.UP ? !isOutput(i) && i != fuel : i == fuel;
                if (take) {
                    out.add(i);
                }
            }
            return out.stream().mapToInt(Integer::intValue).toArray();
        }

        Supplier<MenuType<MachineMenu>> type() {
            return switch (this) {
                case FORGE -> Machines.FORGE_MENU;
                case OVEN -> Machines.OVEN_MENU;
                case MILLSTONE -> Machines.MILLSTONE_MENU;
                case UNSMELTERY -> Machines.UNSMELTERY_MENU;
                case ARMOR_STAND -> Machines.ARMOR_STAND_MENU;
            };
        }
    }

    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET };

    public final Layout layout;
    private final Container machine;
    private final ContainerData data;

    public MachineMenu(Layout layout, int id, Inventory inventory) {
        this(layout, id, inventory, new SimpleContainer(layout.size()), new SimpleContainerData(4));
    }

    public MachineMenu(Layout layout, int id, Inventory inventory, Container machine, ContainerData data) {
        super(layout.type().get(), id);
        this.layout = layout;
        this.machine = machine;
        this.data = data;
        checkContainerSize(machine, layout.size());
        machine.startOpen(inventory.player);
        for (int i = 0; i < layout.size(); i++) {
            int slot = i;
            int[] xy = layout.slots[i];
            if (layout == Layout.ARMOR_STAND) {
                addSlot(new ArmorSlot(machine, i, xy[0], xy[1], ARMOR[i], inventory.player));
            } else if (layout.isOutput(i)) {
                addSlot(new Slot(machine, i, xy[0], xy[1]) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            } else {
                addSlot(new Slot(machine, i, xy[0], xy[1]) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return machine.canPlaceItem(slot, stack);
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, layout.inventoryY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, layout.inventoryY + 58));
        }
        if (layout == Layout.ARMOR_STAND) {
            // the player's own armour beside the stand's (LOTRContainerArmorStand)
            for (int i = 0; i < 4; i++) {
                addSlot(new ArmorSlot(inventory, 39 - i, 8, 21 + i * 18, ARMOR[i], inventory.player));
            }
        }
        addDataSlots(data);
    }

    /** An armour slot that takes only armour for its body slot, one piece. */
    static class ArmorSlot extends Slot {
        private final EquipmentSlot equip;
        private final Player player;

        ArmorSlot(Container c, int index, int x, int y, EquipmentSlot equip, Player player) {
            super(c, index, x, y);
            this.equip = equip;
            this.player = player;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.canEquip(equip, player);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    public int burn() {
        return data.get(0);
    }

    public int burnMax() {
        return data.get(1);
    }

    public int progress() {
        return data.get(2);
    }

    public int runTicks() {
        return Math.max(1, data.get(3));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int n = layout.size();
        int playerEnd = n + 36;
        if (index < n || index >= playerEnd) {
            if (!moveItemStackTo(stack, n, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            if (layout.fuel >= 0 && stack.getBurnTime(RecipeType.SMELTING) > 0) {
                moved = moveItemStackTo(stack, layout.fuel, layout.fuel + 1, false);
            }
            if (!moved) {
                for (int i = 0; i < n && !stack.isEmpty(); i++) {
                    if (i != layout.fuel && !layout.isOutput(i) && slots.get(i).mayPlace(stack)) {
                        moveItemStackTo(stack, i, i + 1, false);
                    }
                }
                moved = stack.getCount() != copy.getCount();
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
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
        return machine.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        machine.stopOpen(player);
    }
}
