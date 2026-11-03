package com.porcupinestudios.tardisrb.flight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Finds where a police box can stand: sturdy ground, two free dry blocks above it, nearest to the target first. */
public final class Landing {
    private Landing() {}

    public static Optional<BlockPos> find(ServerLevel level, BlockPos target, int radius, int height) {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 2;
        BlockPos center = new BlockPos(target.getX(), Math.max(minY, Math.min(maxY, target.getY())), target.getZ());
        List<int[]> columns = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                columns.add(new int[] {dx, dz});
            }
        }
        columns.sort(Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1]));
        for (int[] c : columns) {
            for (int step = 0; step <= height * 2; step++) {
                int dy = (step % 2 == 0 ? 1 : -1) * ((step + 1) / 2);
                int y = center.getY() + dy;
                if (y < minY || y > maxY) {
                    continue;
                }
                BlockPos p = new BlockPos(center.getX() + c[0], y, center.getZ() + c[1]);
                if (fits(level, p)) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean fits(ServerLevel level, BlockPos p) {
        BlockPos below = p.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && free(level, p)
                && free(level, p.above());
    }

    private static boolean free(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.canBeReplaced() && s.getFluidState().isEmpty();
    }
}
