package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A converted block with a front, like a furnace (LOTR's forges, ovens, barrels, chests): {@code facing} is the way
 * the front faces, set from the old metadata in structures and towards the player when placed. An optional box shape
 * turns with it.
 */
public class FacingBlock extends HorizontalDirectionalBlock {
    private final VoxelShape[] shapes = new VoxelShape[4];

    public FacingBlock(Properties properties, float[] box) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            shapes[d.get2DDataValue()] = box == null ? Shapes.block() : turned(box, d);
        }
    }

    /** The box as exported (front to the south), turned so the front faces {@code d}. */
    static VoxelShape turned(float[] b, Direction d) {
        return switch (d) {
            case SOUTH -> Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
            case NORTH -> Block.box(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
            case WEST -> Block.box(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
            default -> Block.box(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
        };
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new FacingBlock(p, null));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(FACING).get2DDataValue()];
    }
}
