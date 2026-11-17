package com.porcupinestudios.lotrport.content.woodset;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A LOTR wood's hanging sign. It is vanilla's hanging sign (so the game opens the hanging-sign edit screen and keeps
 * its narrower lines) reporting this mod's block entity type, which accepts LOTR's hanging sign blocks.
 */
public class LotrHangingSignBlockEntity extends HangingSignBlockEntity {
    public LotrHangingSignBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public BlockEntityType<?> getType() {
        return WoodSets.HANGING_SIGN.get();
    }
}
