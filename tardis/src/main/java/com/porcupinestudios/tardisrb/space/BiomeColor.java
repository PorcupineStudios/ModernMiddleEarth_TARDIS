package com.porcupinestudios.tardisrb.space;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * A biome's colour on a globe map, worked out on the server (the client's grass colour maps aren't there): water,
 * snow, sand and badlands by tag or climate, otherwise a green from temperature and rainfall, or the biome's own
 * foliage colour.
 */
final class BiomeColor {
    private BiomeColor() {}

    static int of(Holder<Biome> holder) {
        Biome biome = holder.value();
        float temp = biome.getBaseTemperature();
        if (holder.is(BiomeTags.IS_DEEP_OCEAN)) {
            return mix(biome.getWaterColor(), 0x0A1A40, 0.55);
        }
        if (holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_RIVER)) {
            return mix(biome.getWaterColor(), 0x14306A, 0.35);
        }
        if (temp < 0.15f) {
            return 0xE6EDF2;
        }
        if (holder.is(BiomeTags.IS_BADLANDS)) {
            return 0xB8643A;
        }
        float rain = biome.getModifiedClimateSettings().downfall();
        if (temp >= 1.5f && !biome.hasPrecipitation()) {
            return 0xD8C88E;
        }
        if (holder.is(BiomeTags.IS_MOUNTAIN)) {
            return 0x8A8A80;
        }
        var foliage = biome.getSpecialEffects().getFoliageColorOverride();
        if (foliage.isPresent()) {
            return foliage.get();
        }
        // dry and cold -> pale olive, warm and wet -> deep green
        double t = Math.max(0, Math.min(1, temp));
        double r = Math.max(0, Math.min(1, rain));
        int dry = mix(0x9AA45E, 0xB7B060, t);
        int wet = mix(0x4E7A3A, 0x2F7A28, t);
        return mix(dry, wet, r);
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
