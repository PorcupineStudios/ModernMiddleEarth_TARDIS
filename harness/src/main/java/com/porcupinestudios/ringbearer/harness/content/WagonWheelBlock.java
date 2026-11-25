package com.porcupinestudios.ringbearer.harness.content;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.ringbearer.harness.HarnessMod;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A wagon wheel: place it against the side of a vehicle's chassis; it faces out (its axle runs from the chassis to
 * here) and the wheel turns round this block's centre. Only the axle and hub are solid; the rim is drawn, and the
 * wheel's contact with the ground is {@link WagonWheelBlockEntity}'s.
 */
public class WagonWheelBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<WagonWheelBlock> CODEC = simpleCodec(WagonWheelBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // the axle from the inner face to the hub, and the hub
        SHAPES.put(Direction.EAST, Shapes.or(Block.box(0, 7, 7, 8, 9, 9), Block.box(6, 6, 6, 10, 10, 10)));
        SHAPES.put(Direction.WEST, Shapes.or(Block.box(8, 7, 7, 16, 9, 9), Block.box(6, 6, 6, 10, 10, 10)));
        SHAPES.put(Direction.SOUTH, Shapes.or(Block.box(7, 7, 0, 9, 9, 8), Block.box(6, 6, 6, 10, 10, 10)));
        SHAPES.put(Direction.NORTH, Shapes.or(Block.box(7, 7, 8, 9, 9, 16), Block.box(6, 6, 6, 10, 10, 10)));
    }

    public WagonWheelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Against a side: facing out from it. On top or underneath: across the way you look (wheels roll ahead). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction out = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getClockWise();
        return defaultBlockState().setValue(FACING, out);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WagonWheelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide && type == HarnessMod.WAGON_WHEEL_BE.get()
                ? (l, p, s, be) -> ((WagonWheelBlockEntity) be).clientTick() : null;
    }
}
