package com.porcupinestudios.tardisrb.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An invisible block that only blocks movement, inside a part of the console room drawn as a model: a post in each
 * arch pillar ({@code collision_post}), the console's pedestal on the lower deck ({@code collision_pillar}). It has no
 * outline, so it can't be clicked or broken and never hides a console control from a click.
 */
public class CollisionOnlyBlock extends Block {
    private final VoxelShape shape;

    public CollisionOnlyBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
    }

    protected VoxelShape collision(BlockState state) {
        return shape;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return collision(state);
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return collision(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }
}
