package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code lotr_port:outside_enclosures}: drops a position inside an enclosure (a fenced orchard or
 * field, {@link com.porcupinestudios.lotrport.world.map.Enclosures}) or within {@code margin} blocks of its fence, so
 * a biome's own trees don't grow in the fields. {@code {"type": "lotr_port:outside_enclosures", "margin": 3}}.
 */
public final class OutsideEnclosures extends PlacementFilter {
    /** Default blocks kept clear round a fence (a tree's trunk this far out leans its crown over the fence at most). */
    public static final int MARGIN = 3;
    public static final MapCodec<OutsideEnclosures> CODEC = Codec.intRange(0, 16).optionalFieldOf("margin", MARGIN)
            .xmap(OutsideEnclosures::new, f -> f.margin);
    private final int margin;

    public OutsideEnclosures(int margin) {
        this.margin = margin;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return MiddleEarthMap.get().enclosureNear(pos.getX(), pos.getZ(), margin) == null;
    }

    @Override
    public PlacementModifierType<?> type() {
        return com.porcupinestudios.lotrport.world.LotrWorldgen.OUTSIDE_ENCLOSURES.get();
    }
}
