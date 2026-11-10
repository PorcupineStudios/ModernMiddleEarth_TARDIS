package com.porcupinestudios.lotrport.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LOTR's weapon rack (LOTRBlockWeaponRack): stands on the floor along the way the placer looks, or hangs on the side of
 * a block ({@code wall}, facing the block it hangs on). LOTR's bounds: 0.9 high, 0.6 deep on floors and walls.
 */
public class WeaponRackBlock extends DisplayBlock {
    public static final BooleanProperty WALL = BooleanProperty.create("wall");
    private static final VoxelShape FLOOR_NS = Block.box(0, 0, 3.2, 16, 14.4, 12.8);
    private static final VoxelShape FLOOR_EW = Block.box(3.2, 0, 0, 12.8, 14.4, 16);
    private static final VoxelShape WALL_N = Block.box(0, 0, 0, 16, 16, 9.6);
    private static final VoxelShape WALL_S = Block.box(0, 0, 6.4, 16, 16, 16);
    private static final VoxelShape WALL_E = Block.box(6.4, 0, 0, 16, 16, 16);
    private static final VoxelShape WALL_W = Block.box(0, 0, 0, 9.6, 16, 16);

    public WeaponRackBlock(Properties properties) {
        super(properties, null, "weapon_rack");
        registerDefaultState(defaultBlockState().setValue(WALL, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(WeaponRackBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WALL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState state = face.getAxis().isHorizontal()
                ? defaultBlockState().setValue(WALL, true).setValue(FACING, face.getOpposite())
                : defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction to = state.getValue(WALL) ? state.getValue(FACING) : Direction.DOWN;
        BlockPos on = pos.relative(to);
        return level.getBlockState(on).isFaceSturdy(level, on, to.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction d = state.getValue(FACING);
        if (!state.getValue(WALL)) {
            return d.getAxis() == Direction.Axis.Z ? FLOOR_NS : FLOOR_EW;
        }
        return switch (d) {
            case NORTH -> WALL_N;
            case SOUTH -> WALL_S;
            case EAST -> WALL_E;
            default -> WALL_W;
        };
    }

    public static boolean onWall(BlockState state) {
        return state.hasProperty(WALL) && state.getValue(WALL);
    }
}
