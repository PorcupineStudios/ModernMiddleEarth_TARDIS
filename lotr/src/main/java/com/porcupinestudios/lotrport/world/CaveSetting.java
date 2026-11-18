package com.porcupinestudios.lotrport.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function {@code lotr_port:cave_setting}: a biome's cave setting at a column, from the biome config
 * ({@link BiomeCaveConfig#value}). {@code {"type": "lotr_port:cave_setting", "setting": "entrance_pits"}}; settings
 * {@link BiomeCaveConfig#SETTING_NAMES}. Middle-earth's final density (converter/noisecaves.py) holds its entrances
 * back by it and switches its cheese, spaghetti and noodle caves with it; wrap it in {@code minecraft:cache_2d}.
 */
public record CaveSetting(String setting) implements DensityFunction.SimpleFunction {
    public static final MapCodec<CaveSetting> MAP_CODEC = Codec.STRING.validate(s -> BiomeCaveConfig.SETTING_NAMES
            .contains(s) ? DataResult.success(s) : DataResult.error(() -> "unknown cave setting " + s))
            .fieldOf("setting").xmap(CaveSetting::new, CaveSetting::setting);
    private static final KeyDispatchDataCodec<CaveSetting> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        return BiomeCaveConfig.value(setting, context.blockX(), context.blockZ());
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return 64;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
