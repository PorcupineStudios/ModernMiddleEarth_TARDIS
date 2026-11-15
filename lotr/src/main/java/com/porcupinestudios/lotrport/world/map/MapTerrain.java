package com.porcupinestudios.lotrport.world.map;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function {@code lotr_port:map_terrain}: the map's ground height ({@code "value": "height"}, a y level) or
 * hilliness ({@code "value": "variation"}, in blocks) at a column. The noise settings JSON turns these into terrain,
 * so the shape stays tunable in data. Wrap it in {@code minecraft:flat_cache}: it only depends on x and z.
 */
public record MapTerrain(Value value) implements DensityFunction.SimpleFunction {
    public static final MapCodec<MapTerrain> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Value.CODEC.fieldOf("value").forGetter(MapTerrain::value))
            .apply(instance, MapTerrain::new));
    public static final KeyDispatchDataCodec<MapTerrain> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    public enum Value implements StringRepresentable {
        HEIGHT("height"),
        VARIATION("variation"),
        /** 1 / (hilliness + 2): (ground level - y) times this says how far into the noise band a point is. */
        BAND("band");

        public static final com.mojang.serialization.Codec<Value> CODEC = StringRepresentable.fromEnum(Value::values);
        private final String name;

        Value(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    public double compute(FunctionContext context) {
        MiddleEarthMap map = MiddleEarthMap.get();
        return switch (value) {
            case HEIGHT -> map.heightAt(context.blockX(), context.blockZ());
            case VARIATION -> map.variationAt(context.blockX(), context.blockZ());
            // 0 puts every point in the band: the full formula, for NoiseBandTests' reference
            case BAND -> NoiseTop.disabled() ? 0 : 1 / (map.variationAt(context.blockX(), context.blockZ()) + 2);
        };
    }

    @Override
    public double minValue() {
        return value == Value.HEIGHT ? -64 : 0;
    }

    @Override
    public double maxValue() {
        return switch (value) {
            case HEIGHT -> 640; // the world's top (converter/world.py TOP_Y)
            case VARIATION -> 256;
            case BAND -> 0.5;
        };
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
