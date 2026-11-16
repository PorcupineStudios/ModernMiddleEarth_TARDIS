package com.porcupinestudios.lotrport.drink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** What a set-down vessel holds: one drink (with its strength and vessel) or the empty vessel. Key "Held". */
public class VesselBlockEntity extends BlockEntity {
    private ItemStack held = ItemStack.EMPTY;

    public VesselBlockEntity(BlockPos pos, BlockState state) {
        super(Vessels.BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStack held() {
        return held;
    }

    public void set(ItemStack stack) {
        held = stack.copyWithCount(1);
        setChanged();
    }

    public ItemStack take() {
        ItemStack s = held;
        held = ItemStack.EMPTY;
        setChanged();
        return s;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!held.isEmpty()) {
            tag.put("Held", held.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        held = tag.contains("Held") ? ItemStack.parseOptional(registries, tag.getCompound("Held")) : ItemStack.EMPTY;
    }
}
