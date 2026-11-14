package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LOTR's reeds (LOTRBlockReed, dried: LOTRBlockReedDry). In LOTR a reed stood on still water and its renderer drew
 * its stem on down through the water to the bed (the "lower" icon on the bed, "mid" in the water). Here the stem is
 * real: reeds stand waterlogged on the bed of still water, or on another reed, and rise out of it. {@code part} picks
 * LOTR's icons: lower on the bed, upper at the top, mid between. Live reeds grow up to 3 blocks above the water in
 * random ticks (LOTR's MAX_GROW_HEIGHT and 15 growth steps, kept in {@code age}); dried reeds don't grow.
 */
public class ReedBlock extends Block implements SimpleWaterloggedBlock {
    public enum Part implements StringRepresentable {
        LOWER, MIDDLE, UPPER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty AGE = BlockStateProperties.AGE_15;
    /** LOTR's MAX_GROW_HEIGHT: reed blocks above the water. */
    public static final int MAX_ABOVE_WATER = 3;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    private final boolean grows;

    public ReedBlock(Properties properties, boolean grows) {
        super(properties);
        this.grows = grows;
        registerDefaultState(stateDefinition.any().setValue(PART, Part.UPPER).setValue(WATERLOGGED, false)
                .setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new ReedBlock(p, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, WATERLOGGED, AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /** On another reed, or in still water on a solid bed (LOTR: on a reed or on still water). */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.getBlock() == this) {
            return true;
        }
        return state.getValue(WATERLOGGED) && below.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean water = level.getFluidState(pos).getType() == Fluids.WATER;
        BlockState state = defaultBlockState().setValue(WATERLOGGED, water);
        return state.canSurvive(level, pos) ? withPart(state, level, pos) : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level,
            BlockPos pos, BlockPos otherPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (dir == Direction.DOWN && !state.canSurvive(level, pos)) {
            level.scheduleTick(pos, this, 1);
        }
        return withPart(state, level, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    public BlockState withPart(BlockState state, BlockGetter level, BlockPos pos) {
        boolean above = level.getBlockState(pos.above()).getBlock() == this;
        boolean below = level.getBlockState(pos.below()).getBlock() == this;
        return state.setValue(PART, !below && (above || state.getValue(WATERLOGGED)) ? Part.LOWER
                : above ? Part.MIDDLE : Part.UPPER);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return grows;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!grows || !level.isEmptyBlock(pos.above()) || state.getValue(WATERLOGGED)) {
            return;
        }
        int dry = 1;
        while (level.getBlockState(pos.below(dry)).getBlock() == this
                && !level.getBlockState(pos.below(dry)).getValue(WATERLOGGED)) {
            dry++;
        }
        if (dry >= MAX_ABOVE_WATER) {
            return;
        }
        int age = state.getValue(AGE);
        if (age == 15) {
            level.setBlockAndUpdate(pos.above(), defaultBlockState());
            level.setBlock(pos, state.setValue(AGE, 0), 4);
        } else {
            level.setBlock(pos, state.setValue(AGE, age + 1), 4);
        }
    }
}
