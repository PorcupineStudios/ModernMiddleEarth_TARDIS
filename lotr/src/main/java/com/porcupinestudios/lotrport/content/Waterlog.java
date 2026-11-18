package com.porcupinestudios.lotrport.content;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Waterlogging for LOTR's own block shapes (1.7.10 had none; converter/waterlog.py marks the blocks that hold water
 * like their vanilla kin: chests, gates, chandeliers, chains, cages, stalactites, vessels and plates; vanilla's own
 * classes - slabs, stairs, walls, fences, panes, trapdoors, ladders, leaves, signs - already do).
 *
 * <p>A block's properties are fixed while its constructor runs, so {@link LotrBlocks} builds a marked block inside
 * {@link #building}, and {@link ShapedBlock}, {@link FacingBlock} and {@link GateBlock} add {@code waterlogged} to
 * their state definition when {@link #next} says so. The rest is vanilla's SimpleWaterloggedBlock logic, done only
 * for states that have the property.
 */
public final class Waterlog {
    private static final ThreadLocal<Boolean> NEXT = ThreadLocal.withInitial(() -> false);

    private Waterlog() {}

    /** Builds a block that waterlogs ({@code water}) or not. */
    public static <T> T building(boolean water, Supplier<T> block) {
        NEXT.set(water);
        try {
            return block.get();
        } finally {
            NEXT.set(false);
        }
    }

    /** In a block's createBlockStateDefinition: add the property if the block being built waterlogs. */
    public static void define(StateDefinition.Builder<?, ?> builder) {
        if (NEXT.get()) {
            builder.add(BlockStateProperties.WATERLOGGED);
        }
    }

    public static boolean has(BlockState state) {
        return state != null && state.hasProperty(BlockStateProperties.WATERLOGGED);
    }

    /** The default state, dry. */
    public static BlockState dry(BlockState state) {
        return has(state) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
    }

    /** A placed state: waterlogged if placed into water. */
    public static BlockState placed(BlockState state, BlockPlaceContext context) {
        return has(state) ? state.setValue(BlockStateProperties.WATERLOGGED,
                context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER) : state;
    }

    public static FluidState fluid(BlockState state, FluidState dry) {
        return has(state) && state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : dry;
    }

    /** In updateShape: water in the block keeps flowing. */
    public static void tick(BlockState state, LevelAccessor level, BlockPos pos) {
        if (has(state) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
    }
}
