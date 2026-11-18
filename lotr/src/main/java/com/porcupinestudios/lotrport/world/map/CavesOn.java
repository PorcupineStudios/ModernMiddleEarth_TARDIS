package com.porcupinestudios.lotrport.world.map;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function {@code lotr_port:caves_on}: 1, or 0 while a ground scan runs ({@link #scanning}). Middle-earth's
 * final density takes its noise-cave branch only when it is 1 (converter/noisecaves.py), so finding a column's ground
 * (structures, spawning, getBaseHeight; mixin NoiseGeneratorMixin) skips the cave noises: they cost 15x a scan's
 * whole column, and caves are under the ground a scan looks for. Real chunks always get their caves.
 */
public final class CavesOn implements DensityFunction.SimpleFunction {
    public static final CavesOn INSTANCE = new CavesOn();
    public static final MapCodec<CavesOn> MAP_CODEC = MapCodec.unit(INSTANCE);
    private static final KeyDispatchDataCodec<CavesOn> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);
    private static final ThreadLocal<Boolean> SCANNING = ThreadLocal.withInitial(() -> false);

    private CavesOn() {}

    /** Runs a ground scan with the caves off on this thread. */
    public static <T> T scanning(Supplier<T> scan) {
        boolean was = SCANNING.get();
        SCANNING.set(true);
        try {
            return scan.get();
        } finally {
            SCANNING.set(was);
        }
    }

    @Override
    public double compute(FunctionContext context) {
        return SCANNING.get() ? 0 : 1;
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return 1;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
