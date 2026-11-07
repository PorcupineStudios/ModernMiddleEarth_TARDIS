package com.porcupinestudios.lotrport.content;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LOTR's torches (elven, mallorn, Morgul...): one block that stands ({@code facing=up}) or hangs on a wall facing away
 * from it, as 1.7.10 torches did with metadata, instead of vanilla's separate standing and wall torch blocks.
 */
public class LotrTorchBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.create("facing",
            d -> d != Direction.DOWN);
    private static final VoxelShape STANDING = Block.box(6, 0, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> WALL = Map.of(
            Direction.NORTH, Block.box(5.5, 3, 11, 10.5, 13, 16), Direction.SOUTH, Block.box(5.5, 3, 0, 10.5, 13, 5),
            Direction.WEST, Block.box(11, 3, 5.5, 16, 13, 10.5), Direction.EAST, Block.box(0, 3, 5.5, 5, 13, 10.5));

    public LotrTorchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction d = state.getValue(FACING);
        return d == Direction.UP ? STANDING : WALL.get(d);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        for (Direction d : ctx.getNearestLookingDirections()) {
            Direction facing = d.getOpposite();
            if (facing == Direction.DOWN) {
                continue;
            }
            BlockState s = defaultBlockState().setValue(FACING, facing);
            if (s.canSurvive(ctx.getLevel(), ctx.getClickedPos())) {
                return s;
            }
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction d = state.getValue(FACING);
        BlockPos on = pos.relative(d.getOpposite());
        return d == Direction.UP ? Block.canSupportCenter(level, on, Direction.UP)
                : level.getBlockState(on).isFaceSturdy(level, on, d);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        return dir == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction d = state.getValue(FACING);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.7, z = pos.getZ() + 0.5;
        if (d != Direction.UP) {
            Direction o = d.getOpposite();
            x += 0.27 * o.getStepX();
            y += 0.22;
            z += 0.27 * o.getStepZ();
        }
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
    }
}
