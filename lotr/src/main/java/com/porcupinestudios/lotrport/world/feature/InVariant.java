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
 * {@code {"type": "lotr_port:in_variant", "variants": ["forest", "hills_forest"]}}. Nothing is placed inside an
 * enclosure (a fenced orchard or field, {@link com.porcupinestudios.lotrport.world.map.Enclosures}) or within
 * {@link OutsideEnclosures#MARGIN} of its fence.
 */
public final class InVariant extends PlacementFilter {
    public static final MapCodec<InVariant> CODEC = Codec.STRING.listOf().fieldOf("variants")
            .xmap(InVariant::new, f -> f.variants);
    private final List<String> variants;

    public InVariant(List<String> variants) {
        this.variants = List.copyOf(variants);
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        MiddleEarthMap map = MiddleEarthMap.get();
        if (map.enclosureNear(pos.getX(), pos.getZ(), OutsideEnclosures.MARGIN) != null) {
            return false;
        }
        // looked up up to the biome's feather (20 blocks; Eriador's big patches more) away, so a wood thins out over
        // its edge instead of stopping on the line between two patches (2026-11-14 preview: square-edged forests)
        int feather = map.variantFeather(pos.getX(), pos.getZ());
        String v = map.variantAt(pos.getX() + random.nextInt(2 * feather + 1) - feather,
                pos.getZ() + random.nextInt(2 * feather + 1) - feather);
        return variants.contains(v == null ? "plain" : v);
    }

    @Override
    public PlacementModifierType<?> type() {
        return com.porcupinestudios.lotrport.world.LotrWorldgen.IN_VARIANT.get();
    }
}
