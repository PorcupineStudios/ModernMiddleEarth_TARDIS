package com.porcupinestudios.tardisrb.block;

import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.look.Looks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Placing and lifting the police box. Only flight, the crash site and commands call these. */
public final class Exteriors {
    private Exteriors() {}

    public static void place(ServerLevel level, BlockPos base, Direction facing, UUID tardis) {
        int look = TardisManager.get(level.getServer()).get(tardis)
                .map(t -> Looks.exterior(level.getServer(), t)).orElse(0);
        BlockState lower = TardisBlocks.EXTERIOR.get().defaultBlockState().setValue(ExteriorBlock.FACING, facing)
                .setValue(ExteriorBlock.LOOK, look);
        level.setBlock(base, lower, Block.UPDATE_ALL);
        level.setBlock(base.above(), lower.setValue(ExteriorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (level.getBlockEntity(base) instanceof ExteriorBlockEntity be) {
            be.setTardis(tardis);
        }
    }

    /** Removes the box at {@code base}; returns the way it faced (north if it wasn't there). */
    public static Direction lift(ServerLevel level, BlockPos base) {
        BlockState state = level.getBlockState(base);
        Direction facing = state.getBlock() instanceof ExteriorBlock ? state.getValue(ExteriorBlock.FACING)
                : Direction.NORTH;
        if (level.getBlockState(base.above()).getBlock() instanceof ExteriorBlock) {
            level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        if (state.getBlock() instanceof ExteriorBlock) {
            level.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return facing;
    }

    public static Direction facing(ServerLevel level, BlockPos base) {
        BlockState state = level.getBlockState(base);
        return state.getBlock() instanceof ExteriorBlock ? state.getValue(ExteriorBlock.FACING) : Direction.NORTH;
    }
}
