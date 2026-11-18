package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/**
 * Feature {@code lotr_port:mountain_ores}: LOTR's ores in the rock of mountains and high hills (Aaron 2026-11-17:
 * "add ores ... to mountains again"). The biome's own ore features are placed in a band under y {@code base} (48),
 * as LOTR's were in a 256-high world; since the ranges rise to y 640, everything above was bare stone. Once per chunk,
 * this runs the biome's {@code lotr_port:ore/*} features again once per {@code band} blocks of ground over the base,
 * and lifts each position into the rock between the base and {@code skin} blocks under the surface, keeping its depth
 * rank (deep ores like mithril stay near the mountain's roots). Ore targets (stone, Mordor rock...) are the biome's.
 */
public final class MountainOresFeature extends Feature<MountainOresFeature.Config> {
    public record Config(int base, int band, int skin, int bottom, int maxRuns) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("base").forGetter(Config::base),
                Codec.INT.fieldOf("band").forGetter(Config::band),
                Codec.INT.fieldOf("skin").forGetter(Config::skin),
                Codec.INT.fieldOf("bottom").forGetter(Config::bottom),
                Codec.INT.fieldOf("max_runs").forGetter(Config::maxRuns)).apply(i, Config::new));
    }

    public MountainOresFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config c = context.config();
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x0 + 8, z0 + 8);
        double extra = surface - c.skin() - c.base();
        if (extra <= 8) {
            return false;
        }
        Holder<Biome> biome = level.getBiome(new BlockPos(x0 + 8, Math.min(surface, c.base()), z0 + 8));
        if (!biome.unwrapKey().map(k -> k.location().getNamespace().equals("lotr_port")).orElse(false)) {
            return false;
        }
        double want = extra / c.band();
        int runs = Math.min(c.maxRuns(), (int) want + (random.nextDouble() < want - (int) want ? 1 : 0));
        if (runs <= 0) {
            return false;
        }
        List<HolderSet<PlacedFeature>> steps = biome.value().getGenerationSettings().features();
        int step = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
        if (steps.size() <= step) {
            return false;
        }
        PlacementContext placement = new PlacementContext(level, context.chunkGenerator(), Optional.empty());
        BlockPos origin = new BlockPos(x0, 0, z0);
        boolean placed = false;
        for (Holder<PlacedFeature> holder : steps.get(step)) {
            Optional<ResourceKey<PlacedFeature>> key = holder.unwrapKey();
            if (key.isEmpty() || !key.get().location().getNamespace().equals("lotr_port")
                    || !key.get().location().getPath().startsWith("ore/")) {
                continue;
            }
            PlacedFeature feature = holder.value();
            for (int r = 0; r < runs; r++) {
                Stream<BlockPos> positions = Stream.of(origin);
                for (PlacementModifier m : feature.placement()) {
                    if (!(m instanceof BiomeFilter)) {
                        positions = positions.flatMap(p -> m.getPositions(placement, random, p));
                    }
                }
                for (BlockPos p : (Iterable<BlockPos>) positions::iterator) {
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, p.getX(), p.getZ()) - c.skin();
                    if (top <= c.base()) {
                        continue;
                    }
                    double rank = Math.max(0, Math.min(1, (p.getY() - c.bottom()) / (double) (c.base() - c.bottom())));
                    int y = c.base() + (int) (rank * (top - c.base()));
                    placed |= feature.feature().value().place(level, context.chunkGenerator(), random,
                            new BlockPos(p.getX(), y, p.getZ()));
                }
            }
        }
        return placed;
    }
}
