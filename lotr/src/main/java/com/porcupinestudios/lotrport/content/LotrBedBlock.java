package com.porcupinestudios.lotrport.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LOTR's beds (straw, fur, elven, dwarven...): vanilla's two-part bed (facing, head/foot, sleeping, respawn), drawn
 * from block models like 1.7.10 beds rather than vanilla's coloured bed renderer. Its block entity is an empty one of our own (vanilla's
 * BedBlockEntity type doesn't accept our blocks).
 */
public class LotrBedBlock extends BedBlock {
    public LotrBedBlock(Properties properties) {
        super(DyeColor.RED, properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.porcupinestudios.lotrport.machine.LotrBedBlockEntity(pos, state);
    }
}
