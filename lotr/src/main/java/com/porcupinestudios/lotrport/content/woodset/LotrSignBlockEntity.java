package com.porcupinestudios.lotrport.content.woodset;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A LOTR wood's sign: vanilla's sign under this mod's block entity type. */
public class LotrSignBlockEntity extends SignBlockEntity {
    public LotrSignBlockEntity(BlockPos pos, BlockState state) {
        super(WoodSets.SIGN.get(), pos, state);
    }
}
