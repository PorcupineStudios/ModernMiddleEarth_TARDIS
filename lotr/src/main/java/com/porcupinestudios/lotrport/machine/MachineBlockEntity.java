package com.porcupinestudios.lotrport.machine;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A LOTR machine's inventory, fuel and progress: LOTR's forge base (LOTRTileEntityForgeBase) in modern form. Fuel burns
 * while anything can be worked; one run takes {@link #runTicks} and works every input at once ({@link #finish}).
 * The screen reads burn, burn max, progress and run length through {@link #data}.
 */
public abstract class MachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    protected NonNullList<ItemStack> items;
    protected int burn;
    protected int burnMax;
    protected int progress;

    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burn;
                case 1 -> burnMax;
                case 2 -> progress;
                default -> runTicks();
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 4;
        }
    };

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
        super(type, pos, state);
        items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    /** Ticks one run takes. */
    protected abstract int runTicks();

    /** Whether anything could be worked now. */
    protected abstract boolean canWork();

    /** One run is done: work every input that can be. */
    protected abstract void finish();

    /** The fuel slot, or -1 (the millstone runs on redstone). */
    protected abstract int fuelSlot();

    protected abstract MachineMenu.Layout layout();

    /** Whether the machine may run without fuel this tick (the millstone: while powered). */
    protected boolean powered() {
        return false;
    }

    void serverTick() {
        boolean work = canWork();
        if (fuelSlot() >= 0) {
            if (burn > 0) {
                burn--;
            }
            if (burn == 0 && work) {
                ItemStack fuel = items.get(fuelSlot());
                int t = fuel.getBurnTime(RecipeType.SMELTING);
                if (t > 0) {
                    burn = burnMax = t;
                    ItemStack rest = fuel.getCraftingRemainingItem();
                    fuel.shrink(1);
                    if (fuel.isEmpty() && !rest.isEmpty()) {
                        items.set(fuelSlot(), rest);
                    }
                    setChanged();
                }
            }
        }
        boolean running = work && (fuelSlot() >= 0 ? burn > 0 : powered());
        if (running) {
            if (++progress >= runTicks()) {
                progress = 0;
                finish();
            }
            setChanged();
        } else if (progress > 0 && (fuelSlot() < 0 || !work)) {
            progress = 0;
            setChanged();
        }
        boolean lit = fuelSlot() >= 0 ? burn > 0 : running;
        BlockState state = getBlockState();
        if (state.hasProperty(MachineBlock.LIT) && state.getValue(MachineBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(MachineBlock.LIT, lit), 3);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Whether {@code result} fits on the stack in {@code slot}. */
    protected boolean fits(int slot, ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack out = items.get(slot);
        return out.isEmpty() || ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= Math.min(out.getMaxStackSize(), getMaxStackSize());
    }

    protected void put(int slot, ItemStack result) {
        ItemStack out = items.get(slot);
        if (out.isEmpty()) {
            items.set(slot, result.copy());
        } else {
            out.grow(result.getCount());
        }
    }

    /** What a furnace makes of the stack, or empty. */
    protected ItemStack smelt(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return ItemStack.EMPTY;
        }
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)
                .map(r -> r.value().getResultItem(level.registryAccess()).copy()).orElse(ItemStack.EMPTY);
    }

    protected ResourceLocation blockId() {
        return BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock());
    }

    protected static boolean stationMatches(Optional<ResourceLocation> station, ResourceLocation block) {
        return station.isEmpty() || station.get().equals(block);
    }

    // ------------------------------------------------------------------ container

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new MachineMenu(layout(), id, inventory, this, data);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return layout().isOutput(slot) || slot == fuelSlot() && stack.getBurnTime(RecipeType.SMELTING) <= 0;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return layout().faceSlots(side);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (layout().isOutput(slot)) {
            return false;
        }
        return slot != fuelSlot() || stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Burn", burn);
        tag.putInt("BurnMax", burnMax);
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        burn = tag.getInt("Burn");
        burnMax = tag.getInt("BurnMax");
        progress = tag.getInt("Progress");
    }
}
