package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A plain block with a fixed box shape, used for converted blocks that have no closer vanilla kind yet. Their own
 * behaviour (tables, forges, beds...) is ported system by system in later phases.
 */
public class ShapedBlock extends Block {
    private final VoxelShape shape;

    public ShapedBlock(Properties properties, float[] box) {
        super(properties);
        this.shape = box == null ? Shapes.block()
                : Block.box(box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new ShapedBlock(p, null));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
