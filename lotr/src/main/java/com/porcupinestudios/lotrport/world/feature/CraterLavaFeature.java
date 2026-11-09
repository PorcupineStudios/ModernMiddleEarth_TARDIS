package com.porcupinestudios.lotrport.world.feature;

import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Feature {@code lotr_port:crater_lava}: fills a volcano's crater (Mount Doom) with lava, from LOTR's lava level down
 * to the first solid block of each column ({@link MiddleEarthMap#lavaAt}). Placed once per chunk from its corner.
 */
public final class CraterLavaFeature extends Feature<NoneFeatureConfiguration> {
    public CraterLavaFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        MiddleEarthMap map = MiddleEarthMap.get();
        WorldGenLevel level = context.level();
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int lava = map.lavaAt(x0 + dx, z0 + dz);
                for (int y = lava; lava > 0 && y > level.getMinBuildHeight(); y--) {
                    pos.set(x0 + dx, y, z0 + dz);
                    if (level.getBlockState(pos).isSolid()) {
                        break;
                    }
                    level.setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
                    placed = true;
                }
            }
        }
        return placed;
    }
}
