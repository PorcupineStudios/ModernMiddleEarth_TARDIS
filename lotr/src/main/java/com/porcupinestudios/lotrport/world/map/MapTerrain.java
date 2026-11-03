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
        VARIATION("variation");

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
        return value == Value.HEIGHT
                ? map.heightAt(context.blockX(), context.blockZ())
                : map.variationAt(context.blockX(), context.blockZ());
    }

    @Override
    public double minValue() {
        return value == Value.HEIGHT ? -64 : 0;
    }

    @Override
    public double maxValue() {
        return value == Value.HEIGHT ? 320 : 256;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
