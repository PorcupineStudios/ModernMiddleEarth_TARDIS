package com.porcupinestudios.lotrport.machine;

import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import com.porcupinestudios.lotrport.crafting.MachineRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The kebab stand (LOTRTileEntityKebabStand): up to {@code kebab_slots} pieces of raw meat on a spit. Fuel comes from
 * whatever container is under it (LOTR's takeFuelFromBelow: a furnace, a chest of coal), one piece of fuel at a time;
 * each piece of meat cooks in {@code kebab_ticks}, first in first. Once all are cooked, right-clicking takes a kebab
 * off; until then more meat can go on.
 */
public class KebabStandBlockEntity extends BlockEntity {
    private NonNullList<ItemStack> meat = NonNullList.withSize(16, ItemStack.EMPTY);
    private int cookTime;
    private int fuel;

    public KebabStandBlockEntity(BlockPos pos, BlockState state) {
        super(Machines.KEBAB_STAND.get(), pos, state);
    }

    private int slots() {
        return Math.min(meat.size(), Machines.settings().kebabSlots());
    }

    /** The kebab-stand result for raw meat, or empty when it isn't meat. */
    public ItemStack kebabFor(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return ItemStack.EMPTY;
        }
        for (RecipeHolder<MachineRecipe> h : level.getRecipeManager()
                .getAllRecipesFor(LotrRecipes.MACHINE_TYPES.get(MachineRecipe.Machine.KEBAB_STAND).get())) {
            if (h.value().ingredients().get(0).test(stack)) {
                return h.value().result();
            }
        }
        return ItemStack.EMPTY;
    }

    public int amount() {
        int n = 0;
        for (int i = 0; i < slots(); i++) {
            if (!meat.get(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private boolean raw(ItemStack s) {
        return !s.isEmpty() && !kebabFor(s).isEmpty();
    }

    /** Whether every piece on the spit is cooked (and there is some). */
    public boolean cooked() {
        if (amount() == 0) {
            return false;
        }
        for (int i = 0; i < slots(); i++) {
            if (raw(meat.get(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean cooking() {
        return fuel > 0 && !cooked() && amount() > 0;
    }

    public boolean addMeat(ItemStack stack) {
        if (cooked() || kebabFor(stack).isEmpty()) {
            return false;
        }
        for (int i = 0; i < slots(); i++) {
            if (meat.get(i).isEmpty()) {
                meat.set(i, stack.copyWithCount(1));
                changed();
                return true;
            }
        }
        return false;
    }

    /** Takes the last piece off once all are cooked. */
    public ItemStack take() {
        if (!cooked()) {
            return ItemStack.EMPTY;
        }
        for (int i = slots() - 1; i >= 0; i--) {
            if (!meat.get(i).isEmpty()) {
                ItemStack out = meat.get(i);
                meat.set(i, ItemStack.EMPTY);
                changed();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    public NonNullList<ItemStack> contents() {
        return meat;
    }

    void serverTick() {
        boolean wasCooking = cooking();
        if (fuel > 0) {
            fuel--;
        }
        if (amount() > 0 && !cooked()) {
            if (fuel == 0) {
                fuel = fuelFromBelow();
            }
            if (fuel > 0) {
                if (++cookTime >= Machines.settings().kebabTicks()) {
                    cookTime = 0;
                    for (int i = 0; i < slots(); i++) {
                        if (raw(meat.get(i))) {
                            meat.set(i, kebabFor(meat.get(i)).copyWithCount(1));
                            break;
                        }
                    }
                    changed();
                }
            }
        } else {
            cookTime = 0;
        }
        if (wasCooking != cooking()) {
            changed();
        }
    }

    private int fuelFromBelow() {
        IItemHandler below = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.below(), Direction.UP);
        if (below == null) {
            return 0;
        }
        for (int i = 0; i < below.getSlots(); i++) {
            ItemStack s = below.getStackInSlot(i);
            int t = s.getBurnTime(RecipeType.SMELTING);
            if (t > 0 && !below.extractItem(i, 1, true).isEmpty()) {
                below.extractItem(i, 1, false);
                return t;
            }
        }
        return 0;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, meat, true, registries);
        tag.putInt("CookTime", cookTime);
        tag.putInt("Fuel", fuel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        meat = NonNullList.withSize(16, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, meat, registries);
        cookTime = tag.getInt("CookTime");
        fuel = tag.getInt("Fuel");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
