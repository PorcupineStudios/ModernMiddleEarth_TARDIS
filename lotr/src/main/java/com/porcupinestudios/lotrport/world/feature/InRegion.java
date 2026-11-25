package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code lotr_port:in_region}: keeps a position only inside one of the named regions of the region
 * layer ({@link com.porcupinestudios.lotrport.world.map.Regions}), or with {@code "outside": true} only outside all
 * of them. {@code {"type": "lotr_port:in_region", "regions": ["mirkwood_spider_nests"]}}.
 */
public final class InRegion extends PlacementFilter {
    public static final MapCodec<InRegion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.listOf().fieldOf("regions").forGetter(f -> f.regions),
            Codec.BOOL.optionalFieldOf("outside", false).forGetter(f -> f.outside)).apply(i, InRegion::new));
    private final List<String> regions;
    private final boolean outside;

    public InRegion(List<String> regions, boolean outside) {
        this.regions = List.copyOf(regions);
        this.outside = outside;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        MiddleEarthMap map = MiddleEarthMap.get();
        for (String r : regions) {
            if (map.inRegion(r, pos.getX(), pos.getZ())) {
                return !outside;
            }
        }
        return outside;
    }

    @Override
    public PlacementModifierType<?> type() {
        return com.porcupinestudios.lotrport.world.LotrWorldgen.IN_REGION.get();
    }
}
