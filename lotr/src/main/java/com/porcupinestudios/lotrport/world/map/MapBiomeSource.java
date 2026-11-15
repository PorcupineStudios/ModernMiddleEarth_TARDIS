package com.porcupinestudios.lotrport.world.map;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Biomes from the Middle-earth map: one array lookup per call, no climate noise. JSON: {@code {"type":
 * "lotr_port:middle_earth"}}; the biome list comes from the bundled map.
 */
public final class MapBiomeSource extends BiomeSource {
    public static final MapCodec<MapBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(RegistryOps.retrieveGetter(Registries.BIOME))
            .apply(instance, instance.stable(MapBiomeSource::new)));

    private final MiddleEarthMap map;
    private final List<Holder<Biome>> biomes;

    public MapBiomeSource(HolderGetter<Biome> lookup) {
        this.map = MiddleEarthMap.get();
        List<Holder<Biome>> list = new ArrayList<>();
        for (MiddleEarthMap.Entry entry : map.biomes()) {
            list.add(lookup.getOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(entry.id()))));
        }
        this.biomes = List.copyOf(list);
    }

    public MiddleEarthMap map() {
        return map;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream().distinct();
    }

    /** Coordinates are quarts (4 blocks); the biome is the same at every height. */
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // Middle-earth's biomes don't change with height, but a chunk asks for every 4 x 4 x 4 block of all its 44
        // sections: the last 16 columns asked on this thread are kept (2026-11-15: fast chunk loading)
        ColumnCache cache = columns.get();
        int slot = (x & 3) | (z & 3) << 2;
        long key = (long) x << 32 | (z & 0xFFFFFFFFL);
        if (cache.keys[slot] != key || cache.biomes[slot] == null) {
            cache.keys[slot] = key;
            cache.biomes[slot] = biomeAtBlock(x << 2, z << 2);
        }
        return cache.biomes[slot];
    }

    /** Per source (a new world's registries give new biome holders; worker threads outlive worlds). */
    private final ThreadLocal<ColumnCache> columns = ThreadLocal.withInitial(ColumnCache::new);

    /** The biomes of the last 16 quart columns looked up on a thread, by the column's x and z mod 4. */
    private static final class ColumnCache {
        final long[] keys = new long[16];
        @SuppressWarnings("unchecked")
        final Holder<Biome>[] biomes = new Holder[16];
    }

    public Holder<Biome> biomeAtBlock(int blockX, int blockZ) {
        return biomes.get(map.biomeIndexAt(blockX, blockZ));
    }
}
