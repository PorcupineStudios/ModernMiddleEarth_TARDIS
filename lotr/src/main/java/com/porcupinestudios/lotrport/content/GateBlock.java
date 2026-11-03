package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LOTR's gates: round hobbit doors, portcullises, wooden and stone gates. A 4 px panel across the doorway, facing
 * {@code facing}; using one opens or shuts the whole gate (every touching block of the same kind, up to
 * {@link #MAX_GATE}), and an open gate vanishes so you walk through.
 */
public class GateBlock extends DirectionalBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    static final int MAX_GATE = 64;
    private static final VoxelShape ACROSS_Z = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape ACROSS_X = Block.box(6, 0, 0, 10, 16, 16);
    private static final VoxelShape FLAT = Block.box(0, 6, 0, 16, 10, 16);

    public GateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return simpleCodec(GateBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) {
            return Shapes.empty();
        }
        return switch (state.getValue(FACING).getAxis()) {
            case X -> ACROSS_X;
            case Z -> ACROSS_Z;
            default -> FLAT;
        };
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN) ? Shapes.block() : getShape(state, level, pos, CollisionContext.empty());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean open = !state.getValue(OPEN);
            for (BlockPos p : gate(level, pos, state.getBlock())) {
                level.setBlock(p, level.getBlockState(p).setValue(OPEN, open), Block.UPDATE_ALL);
            }
            level.playSound(null, pos, open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE,
                    SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The blocks of one gate: this one and every touching block of the same kind. */
    static Set<BlockPos> gate(Level level, BlockPos start, Block block) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start);
        seen.add(start);
        while (!todo.isEmpty() && seen.size() < MAX_GATE) {
            BlockPos p = todo.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && level.getBlockState(n).is(block)) {
                    seen.add(n);
                    todo.add(n);
                }
            }
        }
        return seen;
    }
}
