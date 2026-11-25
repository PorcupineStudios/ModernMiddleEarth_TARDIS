package com.porcupinestudios.lotrport.compat.create;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/** Create's crafter block entity under our own type (Create's type only accepts its own block). */
public class FactionCrafterBlockEntity extends MechanicalCrafterBlockEntity {
    public FactionCrafterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The inventory Create gives funnels, arms and chutes. */
    public IItemHandler itemHandler() {
        return getInvCapability();
    }
}
