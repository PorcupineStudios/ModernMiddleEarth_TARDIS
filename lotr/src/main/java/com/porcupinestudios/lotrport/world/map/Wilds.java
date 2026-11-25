package com.porcupinestudios.lotrport.world.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * The wild lands' terrain and noise (Aaron 2026-11-25: the Lone-lands as a "gritty wilderness ... rocky ... boulder
 * formations, moss growth, dried up creek beds, ravines"), set in {@code converter/wilds.py} -> map.json {@code wilds}.
 *
 * <p>Gorges: seeded, winding cuts in the ground of the listed biomes, where a smooth noise crosses zero (so they run
 * as long lines), kept only where a second, broad noise is over {@code mask} (so they are sparse: a gorge here and
 * there, not a maze). Each has a flat floor (the inner {@code floor} share of its width) and steep walls, is up to
 * {@code depth} blocks deep, never cuts under sea level + 3 (no flooded trenches) and fades out near roads and rivers
 * and over the biome's border (its smoothed share of the map). The terrain samples the map's height every 4 blocks,
 * so a gorge is at least ~12 blocks wide; the dry creek beds, a block or two deep, are cut by
 * {@code world/feature/GroundCoverFeature} instead.
 *
 * <p>{@link #noise} is the seeded smooth noise the wild-land features share (ground patches, groves, creeks), so a
 * world's groves, rock and creeks lie differently in every world (Aaron 2026-11-25: "seeded").
 */
public final class Wilds {
    private final float[] share;
    private final double scale;
    private final double halfWidth;
    private final double depth;
    private final double floor;
    private final double maskScale;
    private final double mask;
    private final boolean any;

    Wilds(JsonObject json, List<MiddleEarthMap.Entry> biomes) {
        share = new float[Math.max(biomes.size(), 256)];
        JsonObject g = json == null || !json.has("gorges") ? null : json.getAsJsonObject("gorges");
        boolean found = false;
        if (g != null) {
            for (JsonElement e : g.getAsJsonArray("biomes")) {
                for (int i = 0; i < biomes.size(); i++) {
                    if (biomes.get(i).id().equals(e.getAsString())) {
                        share[i] = 1;
                        found = true;
                    }
                }
            }
        }
        any = found;
        scale = g == null ? 1000 : g.get("scale").getAsDouble();
        halfWidth = g == null ? 8 : g.get("width").getAsDouble() / 2;
        depth = g == null ? 0 : g.get("depth").getAsDouble();
        floor = g == null ? 0.3 : g.get("floor").getAsDouble();
        maskScale = g == null ? 1000 : g.get("mask_scale").getAsDouble();
        mask = g == null ? 1 : g.get("mask").getAsDouble();
    }

    boolean any() {
        return any && depth > 0;
    }

    float[] shareTable() {
        return share;
    }

    /**
     * How much of a gorge is at a column, 0..1 (1 = its floor), before the biome share and road/river fades. The
     * distance to the noise's zero line is |n| over the noise's slope there (estimated from two neighbours).
     */
    double gorge(int x, int z) {
        double m = smoothstep((noise(x / maskScale, z / maskScale, 0x474F5247L) - mask) / 0.12);
        if (m <= 0) {
            return 0;
        }
        double s = scale;
        double n = winding(x / s, z / s);
        double gx = (winding((x + 4) / s, z / s) - n) / 4;
        double gz = (winding(x / s, (z + 4) / s) - n) / 4;
        double slope = Math.sqrt(gx * gx + gz * gz);
        if (slope < 1e-9) {
            return 0;
        }
        double t = Math.abs(n) / slope / halfWidth;
        if (t >= 1) {
            return 0;
        }
        double wall = t <= floor ? 1 : 1 - smoothstep((t - floor) / (1 - floor));
        return wall * m;
    }

    double depth() {
        return depth;
    }

    /** The gorges' line noise: a broad octave and a smaller one, so they wander. */
    private static double winding(double x, double z) {
        return 0.8 * noise(x, z, 0x57494E44L) + 0.2 * noise(x * 3.1, z * 3.1, 0x57494E45L);
    }

    /** Smooth seeded value noise, about [-1, 1], at unit scale (divide coordinates by the size you want). */
    public static double noise(double x, double z, long salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = smooth(x - x0);
        double tz = smooth(z - z0);
        long s = salt ^ worldSalt();
        double a = lattice(x0, z0, s);
        double b = lattice(x0 + 1, z0, s);
        double c = lattice(x0, z0 + 1, s);
        double d = lattice(x0 + 1, z0 + 1, s);
        double top = a + (b - a) * tx;
        return top + (c + (d - c) * tx - top) * tz;
    }

    /** Two octaves of {@link #noise}: the size given and a third of it at a quarter strength. */
    public static double fbm(double x, double z, double size, long salt) {
        return 0.8 * noise(x / size, z / size, salt) + 0.3 * noise(x * 3 / size, z * 3 / size, salt * 31 + 7);
    }

    /** A seeded number in [0, 1) per column and purpose. */
    public static double unit(int x, int z, long salt) {
        return (MiddleEarthMap.mixHash(x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt ^ worldSalt())
                >>> 11) * 0x1.0p-53;
    }

    private static double lattice(int x, int z, long salt) {
        return (MiddleEarthMap.mixHash(x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt) >>> 11)
                * 0x1.0p-53 * 2 - 1;
    }

    private static long worldSalt() {
        return MiddleEarthMap.mixHash(MiddleEarthMap.seed() ^ 0x57494C44L);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    static double smoothstep(double t) {
        return t <= 0 ? 0 : t >= 1 ? 1 : smooth(t);
    }
}
