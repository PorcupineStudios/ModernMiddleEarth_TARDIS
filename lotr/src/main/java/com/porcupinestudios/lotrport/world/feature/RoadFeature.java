package com.porcupinestudios.lotrport.world.feature;

import com.porcupinestudios.lotrport.world.map.Roads;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Feature {@code lotr_port:roads}: lays LOTR's roads through the chunk. Soil becomes dirt path, sand becomes
 * sandstone, anything else gravel; plants on the road are cleared. Roads stop at water for now (no bridges yet).
 * Placed once per chunk (no placement modifiers), from the chunk's corner.
 */
public final class RoadFeature extends Feature<NoneFeatureConfiguration> {
    public RoadFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        Roads roads = Roads.get();
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int x0 = origin.getX() & ~15;
        int z0 = origin.getZ() & ~15;
        if (roads.near(x0, z0).isEmpty() && roads.near(x0 + 15, z0 + 15).isEmpty()) {
            return false;
        }
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                double d = roads.distance(x, z);
                // ragged edges: a fixed per-column jitter of up to half a block
                double jitter = (((x * 73428767L) ^ (z * 912931L)) & 15) / 32.0;
                if (d > roads.width() + jitter) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                pos.set(x, y, z);
                BlockState top = level.getBlockState(pos);
                while (top.getFluidState().isEmpty() && isPlant(top) && y > level.getMinBuildHeight()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    pos.setY(--y);
                    top = level.getBlockState(pos);
                }
                if (!top.getFluidState().isEmpty()) {
                    continue;
                }
                BlockState road = roadBlock(top, level, pos);
                if (road != null) {
                    level.setBlock(pos, road, Block.UPDATE_CLIENTS);
                    placed = true;
                }
            }
        }
        return placed;
    }

    private static boolean isPlant(BlockState state) {
        return state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS);
    }

    private static BlockState roadBlock(BlockState top, WorldGenLevel level, BlockPos pos) {
        if (top.is(BlockTags.DIRT)) {
            return Blocks.DIRT_PATH.defaultBlockState();
        }
        if (top.is(BlockTags.SAND)) {
            return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        }
        if (top.is(BlockTags.LEAVES) || top.is(BlockTags.LOGS)) {
            return null;
        }
        return top.isSolidRender(level, pos) ? Blocks.GRAVEL.defaultBlockState() : null;
    }
}
