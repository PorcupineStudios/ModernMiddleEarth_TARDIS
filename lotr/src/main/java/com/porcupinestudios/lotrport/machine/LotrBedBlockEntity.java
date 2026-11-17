package com.porcupinestudios.lotrport.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An empty block entity for LOTR's beds. {@code BedBlock} is an EntityBlock, so the game expects one at every bed;
 * without it each bed logged "Tried to load a block entity ... but failed" whenever its chunk loaded.
 */
public class LotrBedBlockEntity extends BlockEntity {
    public LotrBedBlockEntity(BlockPos pos, BlockState state) {
        super(Machines.BED.get(), pos, state);
    }
}
