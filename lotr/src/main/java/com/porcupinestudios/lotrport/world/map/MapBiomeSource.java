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
        return biomeAtBlock(x << 2, z << 2);
    }

    public Holder<Biome> biomeAtBlock(int blockX, int blockZ) {
        return biomes.get(map.biomeIndexAt(blockX, blockZ));
    }
}
