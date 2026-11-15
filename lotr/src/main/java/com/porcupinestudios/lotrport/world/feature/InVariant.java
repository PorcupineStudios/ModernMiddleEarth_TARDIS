package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code lotr_port:in_variant}: keeps a position only inside one of the named biome variants
 * ({@link com.porcupinestudios.lotrport.world.map.Variants}; {@code "plain"} = the biome without a variant). So a
 * biome's flower meadows, woods and orchards get their own plants, as LOTR's variants did.
 * {@code {"type": "lotr_port:in_variant", "variants": ["forest", "hills_forest"]}}.
 */
public final class InVariant extends PlacementFilter {
    public static final MapCodec<InVariant> CODEC = Codec.STRING.listOf().fieldOf("variants")
            .xmap(InVariant::new, f -> f.variants);
    private static final int FEATHER = 20;
    private final List<String> variants;

    public InVariant(List<String> variants) {
        this.variants = List.copyOf(variants);
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        // looked up up to FEATHER blocks away, so a wood or orchard thins out over its edge instead of stopping on the
        // straight line between two patches (2026-11-14 preview: square-edged forests)
        String v = MiddleEarthMap.get().variantAt(pos.getX() + random.nextInt(2 * FEATHER + 1) - FEATHER,
                pos.getZ() + random.nextInt(2 * FEATHER + 1) - FEATHER);
        return variants.contains(v == null ? "plain" : v);
    }

    @Override
    public PlacementModifierType<?> type() {
        return com.porcupinestudios.lotrport.world.LotrWorldgen.IN_VARIANT.get();
    }
}
