package com.porcupinestudios.lotrport.world.map;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.registries.Registries;

/**
 * How high Middle-earth's terrain can reach over a chunk or a column, so chunk filling and ground scans skip the
 * sky above it (Aaron 2026-11-15: "make it as cheap as possible ... very fast chunk loading"; the world is 704 blocks
 * tall since the mountain ranges).
 * <p>
 * The noise settings work the hills' noise out only within a band of the ground (converter/world.py NOISE_BAND_*):
 * above {@code level + above x (hilliness + 2)} the density is the plain shape, {@code level - y}, which is negative.
 * The density is interpolated between cell corners (every 4 blocks across, 8 up), and the map's values are cached at
 * those corners too, so the highest corner of a chunk or column bounds where anything can be solid. Mixin
 * {@code NoiseGeneratorMixin} cuts the noise settings' height there (never under the sea, which still fills with
 * water), for filling chunks, their noise chunk, and the ground scans of structures and spawning.
 */
public final class NoiseTop {
    public static final ResourceKey<NoiseGeneratorSettings> SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS,
            LotrPort.id("middle_earth"));
    private static final int CELL_WIDTH = 4;
    private static final ThreadLocal<Boolean> DISABLED = ThreadLocal.withInitial(() -> false);

    private NoiseTop() {}

    /** True on a thread that asked for the full formula and height (NoiseBandTests' reference). */
    public static boolean disabled() {
        return DISABLED.get();
    }

    /** Runs {@code task} on this thread with the band and the top cut off: the full formula and height. */
    public static void withoutCuts(Runnable task) {
        DISABLED.set(true);
        try {
            task.run();
        } finally {
            DISABLED.set(false);
        }
    }

    public static boolean isMiddleEarth(Holder<NoiseGeneratorSettings> settings) {
        return settings.is(SETTINGS);
    }

    /**
     * {@code noise} cut to the highest y anything can be solid over the corners from (x0, z0) to (x0 + span,
     * z0 + span) (multiples of 4), rounded up to a whole cell; unchanged when disabled or there's no band.
     */
    public static NoiseSettings cut(NoiseSettings noise, int x0, int z0, int span) {
        MiddleEarthMap map = MiddleEarthMap.get();
        double[] band = map.noiseBand;
        if (band == null || disabled()) {
            return noise;
        }
        double top = band[3] + 1; // water fills under the sea's level wherever the ground is lower
        for (int x = x0; x <= x0 + span; x += CELL_WIDTH) {
            for (int z = z0; z <= z0 + span; z += CELL_WIDTH) {
                double variation = map.variationAt(x, z);
                double level = map.heightAt(x, z) - band[2] * variation;
                top = Math.max(top, level + band[0] * (variation + 2));
            }
        }
        int cell = noise.getCellHeight();
        // the first corner over the top (density there is negative) closes the cell under it
        int topY = Math.floorDiv((int) Math.ceil(top) - noise.minY(), cell) * cell + cell + noise.minY();
        int height = Math.min(noise.height(), topY - noise.minY());
        return height >= noise.height() ? noise
                : new NoiseSettings(noise.minY(), height, noise.noiseSizeHorizontal(), noise.noiseSizeVertical());
    }

    /** A chunk's cut ({@link #cut}). */
    public static NoiseSettings forChunk(NoiseSettings noise, int chunkX, int chunkZ) {
        return cut(noise, chunkX << 4, chunkZ << 4, 16);
    }

    /** A block column's cut: the corners of its cell. */
    public static NoiseSettings forColumn(NoiseSettings noise, int x, int z) {
        return cut(noise, Math.floorDiv(x, CELL_WIDTH) * CELL_WIDTH, Math.floorDiv(z, CELL_WIDTH) * CELL_WIDTH,
                CELL_WIDTH);
    }
}
