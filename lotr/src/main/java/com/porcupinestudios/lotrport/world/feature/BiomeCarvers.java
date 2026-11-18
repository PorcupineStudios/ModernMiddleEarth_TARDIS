package com.porcupinestudios.lotrport.world.feature;

import com.porcupinestudios.lotrport.world.BiomeCaveConfig;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CaveWorldCarver;
import org.apache.commons.lang3.mutable.MutableBoolean;

/**
 * Carver types {@code lotr_port:cave} and {@code lotr_port:canyon}: vanilla's cave and canyon carvers that ask the
 * biome config ({@link BiomeCaveConfig}) of the biome they start in how many of them to keep (carverCaves, ravines)
 * and stay surfaceCover blocks under the ground of each column they cut. Middle-earth's biomes use them in place of
 * vanilla's (converter/biomeconfig.py), and the port's mountain caves and canyons are of these types too.
 */
public final class BiomeCarvers {
    private BiomeCarvers() {}

    public static final class Cave extends CaveWorldCarver {
        public Cave() {
            super(CaveCarverConfiguration.CODEC);
        }

        @Override
        public boolean carve(CarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk,
                Function<BlockPos, Holder<Biome>> biomes, RandomSource random, Aquifer aquifer, ChunkPos start,
                CarvingMask mask) {
            return BiomeCaveConfig.carves(false, start) && super.carve(context, config, chunk, biomes, random, aquifer,
                    start, mask);
        }

        @Override
        protected boolean carveBlock(CarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk,
                Function<BlockPos, Holder<Biome>> biomes, CarvingMask mask, BlockPos.MutableBlockPos pos,
                BlockPos.MutableBlockPos check, Aquifer aquifer, MutableBoolean reachedSurface) {
            return !BiomeCaveConfig.covered(chunk, pos) && super.carveBlock(context, config, chunk, biomes, mask, pos,
                    check, aquifer, reachedSurface);
        }
    }

    public static final class Canyon extends CanyonWorldCarver {
        public Canyon() {
            super(CanyonCarverConfiguration.CODEC);
        }

        @Override
        public boolean carve(CarvingContext context, CanyonCarverConfiguration config, ChunkAccess chunk,
                Function<BlockPos, Holder<Biome>> biomes, RandomSource random, Aquifer aquifer, ChunkPos start,
                CarvingMask mask) {
            return BiomeCaveConfig.carves(true, start) && super.carve(context, config, chunk, biomes, random, aquifer,
                    start, mask);
        }

        @Override
        protected boolean carveBlock(CarvingContext context, CanyonCarverConfiguration config, ChunkAccess chunk,
                Function<BlockPos, Holder<Biome>> biomes, CarvingMask mask, BlockPos.MutableBlockPos pos,
                BlockPos.MutableBlockPos check, Aquifer aquifer, MutableBoolean reachedSurface) {
            return !BiomeCaveConfig.covered(chunk, pos) && super.carveBlock(context, config, chunk, biomes, mask, pos,
                    check, aquifer, reachedSurface);
        }
    }
}
