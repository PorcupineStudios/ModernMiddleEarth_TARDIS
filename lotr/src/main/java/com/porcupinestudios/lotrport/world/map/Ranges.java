package com.porcupinestudios.lotrport.world.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * The mountain ranges' own terrain (Aaron 2026-11-15: "the misty mountains span tens of thousands of blocks ... should
 * be incredibly hard to navigate on foot ... change depending on seed ... a sense of awe at gigantic mountain ranges
 * with basically no passes, but still huge variety"). A height field, so it can't overhang or float rock: the range
 * biomes' noise hilliness is replaced by this (prototyped in numpy on the map with perspective renders; this class
 * reproduces the prototype exactly):
 * <ul>
 * <li><b>depth</b>: how far a point is in from the range's edge on the map (an exact distance transform of the range
 * pixels per 64-pixel tile, capped at 20 pixels, blurred twice 3x3).
 * <li><b>floor</b>: the valley floors climb with depth, up to {@code floor} blocks at {@code reach} pixels in. That is
 * the wall: every way across the range climbs to its core, so there are no low passes (a crossing of the Misty
 * Mountains tops out at y 340-490 depending on where).
 * <li><b>ridges</b>: up to {@code relief} blocks over the floor, in seeded <b>regions</b> on a jittered
 * {@code region}-block grid, each one of four kinds (blended over a few hundred blocks) with its own height: sawtooth
 * knife ridges; long spurs running down the depth's slope to the foothills; glacial U-valleys under jagged crests; and
 * giants, fewer and bigger summits. No needle spires or mesas (Aaron: "I dont like the pointy spires").
 * <li><b>cliff bands</b>: on ground steeper than 0.35 blocks per block, in patches, the rise gathers into ~12-block
 * rock faces with ledges.
 * <li><b>ceiling</b>: summits eased under {@code ceiling} (the world tops out at y 640).
 * </ul>
 * The world seed salts all of it ({@link #seed}), so every world's ranges differ; the map (where the ranges are, and
 * so their floor) does not. Each range biome has its own height {@code scale} (the Misty Mountains 1).
 */
final class Ranges {
    private static final int TILE = 64;
    private static final int CAP = 20;
    private static final int MARGIN = CAP + 3;
    private static final int STYLES = 4;
    private static final int ARETES = 0;
    private static final int SPURS = 1;
    private static final int GLACIAL = 2;
    private static final int GIANTS = 3;

    private final int width;
    private final int height;
    private final int blocksPerPixel;
    private final int originX;
    private final int originZ;
    private final float[] scale = new float[256];
    /** Tiles of the blurred depth field in pixels; null = no range near, -0.5 throughout. */
    private final float[][] tiles;
    private final int tilesX;
    private final double reach;
    private final double floor;
    private final double relief;
    private final double region;
    private final double blend;
    private final double band;
    private final double sharp;
    private final double strata;
    private final double ceiling;
    /** The world seed, set before any terrain is made ({@link MiddleEarthMap#setSeed}). */
    private volatile long seed;

    Ranges(JsonObject o, List<MiddleEarthMap.Entry> biomes, byte[] pixels, int width, int height, int blocksPerPixel,
            int originX, int originZ) {
        this.width = width;
        this.height = height;
        this.blocksPerPixel = blocksPerPixel;
        this.originX = originX;
        this.originZ = originZ;
        for (var e : o.getAsJsonObject("biomes").entrySet()) {
            for (int i = 0; i < biomes.size(); i++) {
                if (biomes.get(i).id().equals(e.getKey())) {
                    scale[i] = e.getValue().getAsFloat();
                }
            }
        }
        reach = o.get("reach").getAsDouble();
        floor = o.get("floor").getAsDouble();
        relief = o.get("relief").getAsDouble();
        region = o.get("region").getAsDouble();
        blend = o.get("blend").getAsDouble();
        band = o.get("band").getAsDouble();
        sharp = o.get("sharp").getAsDouble();
        strata = o.get("strata").getAsDouble();
        ceiling = o.get("ceiling").getAsDouble();
        tilesX = (width + TILE - 1) / TILE;
        int tilesZ = (height + TILE - 1) / TILE;
        tiles = new float[tilesX * tilesZ][];
        for (int tz = 0; tz < tilesZ; tz++) {
            for (int tx = 0; tx < tilesX; tx++) {
                tiles[tz * tilesX + tx] = tile(pixels, tx * TILE, tz * TILE);
            }
        }
    }

    void seed(long seed) {
        this.seed = seed;
    }

    boolean isRange(int biome) {
        return scale[biome] > 0;
    }

    /** The range biome's height scale (0 for other biomes); MiddleEarthMap smooths it over borders. */
    float scale(int biome) {
        return scale[biome];
    }

    private boolean range(byte[] pixels, int px, int pz) {
        if (px < 0 || pz < 0 || px >= width || pz >= height) {
            return false;
        }
        int v = pixels[pz * width + px] & 0xFF;
        return v != 255 && scale[v] > 0;
    }

    /** One tile of the depth field, worked out on a window MARGIN pixels wider (enough for the cap and the blurs). */
    private float[] tile(byte[] pixels, int x0, int z0) {
        boolean any = false;
        for (int z = z0 - 3; z < z0 + TILE + 3 && !any; z++) {
            for (int x = x0 - 3; x < x0 + TILE + 3 && !any; x++) {
                any = range(pixels, x, z);
            }
        }
        if (!any) {
            return null;
        }
        int n = TILE + 2 * MARGIN;
        float[] f = new float[n * n];
        float inf = 1e9f;
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int px = x0 - MARGIN + x;
                int pz = z0 - MARGIN + z;
                // outside the map counts as range, so the map's edge doesn't make one (as numpy's transform)
                boolean inMap = px >= 0 && pz >= 0 && px < width && pz < height;
                f[z * n + x] = !inMap || range(pixels, px, pz) ? inf : 0;
            }
        }
        // exact squared Euclidean distance transform (Felzenszwalb & Huttenlocher): columns, then rows
        float[] line = new float[n];
        float[] out = new float[n];
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                line[z] = f[z * n + x];
            }
            edt1d(line, out, n);
            for (int z = 0; z < n; z++) {
                f[z * n + x] = out[z];
            }
        }
        for (int z = 0; z < n; z++) {
            System.arraycopy(f, z * n, line, 0, n);
            edt1d(line, out, n);
            System.arraycopy(out, 0, f, z * n, n);
        }
        for (int i = 0; i < f.length; i++) {
            int x = i % n;
            int z = i / n;
            boolean in = range(pixels, x0 - MARGIN + x, z0 - MARGIN + z);
            f[i] = in ? (float) Math.min(Math.sqrt(f[i]), CAP) - 0.5f : -0.5f;
        }
        f = blur(blur(f, n), n);
        float[] t = new float[TILE * TILE];
        for (int z = 0; z < TILE; z++) {
            System.arraycopy(f, (z + MARGIN) * n + MARGIN, t, z * TILE, TILE);
        }
        return t;
    }

    private static void edt1d(float[] f, float[] d, int n) {
        int[] v = new int[n];
        float[] z = new float[n + 1];
        int k = 0;
        v[0] = 0;
        z[0] = Float.NEGATIVE_INFINITY;
        z[1] = Float.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            float s;
            while (true) {
                s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2f * q - 2f * v[k]);
                if (s <= z[k] && k > 0) {
                    k--;
                } else {
                    break;
                }
            }
            if (s <= z[k]) {
                // k == 0 and the new parabola is lower everywhere
                v[0] = q;
                z[0] = Float.NEGATIVE_INFINITY;
                z[1] = Float.POSITIVE_INFINITY;
                continue;
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Float.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) {
                k++;
            }
            d[q] = (q - v[k]) * (q - v[k]) + f[v[k]];
        }
    }

    /** 3 x 3 box blur, edges repeated (numpy's uniform_filter, mode nearest). */
    private static float[] blur(float[] f, int n) {
        float[] o = new float[f.length];
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                float sum = 0;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = Math.max(0, Math.min(n - 1, x + dx));
                        int zz = Math.max(0, Math.min(n - 1, z + dz));
                        sum += f[zz * n + xx];
                    }
                }
                o[z * n + x] = sum / 9f;
            }
        }
        return o;
    }

    private double pixel(int px, int pz) {
        px = Math.max(0, Math.min(width - 1, px));
        pz = Math.max(0, Math.min(height - 1, pz));
        float[] t = tiles[(pz / TILE) * tilesX + px / TILE];
        return t == null ? -0.5 : t[(pz % TILE) * TILE + px % TILE];
    }

    /** Pixels in from the range's edge (blurred; negative outside), read linearly between pixel centres. */
    double depth(double x, double z) {
        double fx = x / blocksPerPixel + originX - 0.5;
        double fz = z / blocksPerPixel + originZ - 0.5;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = fx - x0;
        double tz = fz - z0;
        double top = pixel(x0, z0) * (1 - tx) + pixel(x0 + 1, z0) * tx;
        double bottom = pixel(x0, z0 + 1) * (1 - tx) + pixel(x0 + 1, z0 + 1) * tx;
        return top * (1 - tz) + bottom * tz;
    }

    /**
     * Blocks the ground rises over the range biome's own height, where the range's height scale is {@code k}
     * (smoothed over borders, so it fades out).
     */
    double massif(int x, int z, double k) {
        double d = depth(x, z);
        if (d <= 0) {
            return 0;
        }
        double m = k * shape(x, z, d);
        double sx = (k * shape(x + 2, z, depth(x + 2, z)) - m) / 2;
        double sz = (k * shape(x, z + 2, depth(x, z + 2)) - m) / 2;
        return cliffs(x, z, m, Math.sqrt(sx * sx + sz * sz));
    }

    /** The ground ceiling: heights over {@code ceiling} - 50 are eased so they never reach it. */
    double ceiling(double ground) {
        double k = ceiling - 50;
        return ground > k ? k + 50 * Math.tanh((ground - k) / 50) : ground;
    }

    /** Blocks over the biome's height before cliff bands, for depth d (pixels). */
    double shape(double x, double z, double d) {
        double s = smooth(d / reach);
        double edge = smooth(d / 2); // ridges grow in over the first 2 pixels
        // the regions round here: weights from how much further each cell's centre is than the nearest one
        double cx = Math.floor(x / region);
        double cz = Math.floor(z / region);
        double[] dist = new double[9];
        long[] hash = new long[9];
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 9; i++) {
            double ccx = cx + i / 3 - 1;
            double ccz = cz + i % 3 - 1;
            long h = cellHash((long) ccx, (long) ccz, 0x5354594CL ^ seed);
            double hx = (ccx + 0.15 + 0.7 * unit(h, 1)) * region;
            double hz = (ccz + 0.15 + 0.7 * unit(h, 2)) * region;
            dist[i] = Math.hypot(x - hx, z - hz);
            hash[i] = h;
            nearest = Math.min(nearest, dist[i]);
        }
        double[] weight = new double[STYLES];
        double amp = 0;
        double total = 0;
        double spread = region * blend * 0.5;
        for (int i = 0; i < 9; i++) {
            double t = (dist[i] - nearest) / spread;
            double w = Math.exp(-t * t);
            weight[(int) (unit(hash[i], 3) * STYLES)] += w;
            amp += w * (0.75 + 0.5 * unit(hash[i], 4));
            total += w;
        }
        double ridges = 0;
        for (int style = 0; style < STYLES; style++) {
            double w = weight[style] / total;
            if (w > 0.001) {
                ridges += w * style(style, x, z);
            }
        }
        return floor * s + relief * (amp / total) * edge * (0.6 + 0.4 * s) * ridges;
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Ridge-ness of one region kind: 0 on valley floors, about 1 on summits. */
    private double style(int style, double x, double z) {
        switch (style) {
            case ARETES:
                return Math.pow(ridged(x, z, 520, 5, 100, 0.5, 220), 1.35);
            case SPURS:
                return Math.pow(Math.max(0, Math.min(1.2, 0.75 * spurs(x, z, 420, 0x5350)
                        + 0.4 * ridged(x, z, 300, 4, 110, 0.5, 0))), 1.25);
            case GLACIAL: {
                double r = ridged(x, z, 800, 3, 120, 0.45, 300);
                double u = smooth((r - 0.2) / 0.45);
                return 0.2 * r + u * (0.5 + 0.55 * Math.pow(ridged(x, z, 320, 4, 126, 0.5, 0), 1.2));
            }
            default:
                return Math.max(0, Math.min(1.25, 1.1 * Math.pow(ridged(x, z, 950, 5, 140, 0.5, 300), 1.5)
                        + 0.2 * ridged(x, z, 260, 4, 141, 0.5, 0)));
        }
    }

    private double ridged(double x, double z, double wave, int octaves, long salt, double gain, double warp) {
        if (warp != 0) {
            x += warp * perlin(x / (wave * 2.2), z / (wave * 2.2), salt + 50);
            z += warp * perlin(x / (wave * 2.2), z / (wave * 2.2), salt + 51);
        }
        double total = 0;
        double norm = 0;
        double amp = 1;
        double weight = 1;
        double f = 1 / wave;
        for (int o = 0; o < octaves; o++) {
            double n = perlin(x * f, z * f, salt + o);
            double r = Math.max(0, Math.min(1, 1 - Math.sqrt(n * n + 0.002)));
            r = r * r * weight;
            weight = Math.max(0, Math.min(1, r * 1.7));
            total += r * amp;
            norm += amp;
            amp *= gain;
            f *= 2.03;
        }
        return Math.max(0, Math.min(1.2, total / norm * 1.5));
    }

    /** Long ridges on a jittered grid, each turned down the depth field's slope (from the crest to the foothills). */
    private double spurs(double x, double z, double cell, long salt) {
        double cx = Math.floor(x / cell);
        double cz = Math.floor(z / cell);
        double sum = 0;
        for (int i = -2; i <= 2; i++) {
            for (int k = -2; k <= 2; k++) {
                long h = cellHash((long) cx + i, (long) cz + k, salt ^ seed);
                double hx = (cx + i + 0.1 + 0.8 * unit(h, 1)) * cell;
                double hz = (cz + k + 0.1 + 0.8 * unit(h, 2)) * cell;
                double gx = depth(hx + 64, hz) - depth(hx - 64, hz);
                double gz = depth(hx, hz + 64) - depth(hx, hz - 64);
                double g = Math.sqrt(gx * gx + gz * gz);
                if (g < 1e-6) {
                    gx = 1;
                    g = 1;
                }
                double along = Math.min(1, g);
                double turn = (unit(h, 6) - 0.5) * 0.7 * along;
                double c = Math.cos(turn);
                double sn = Math.sin(turn);
                double ux = (gx * c - gz * sn) / g;
                double uz = (gx * sn + gz * c) / g;
                double wide = cell * (0.45 + 0.15 * unit(h, 4));
                double longest = cell * (1.3 + 0.7 * unit(h, 3));
                double len = wide + (longest - wide) * along;
                double tall = 0.55 + 0.45 * unit(h, 5);
                double dx = x - hx;
                double dz = z - hz;
                double a = (dx * ux + dz * uz) / len;
                double b = (-dx * uz + dz * ux) / wide;
                double u = Math.max(0, 1 - a * a - b * b);
                double hill = tall * u * u;
                sum += hill * hill;
            }
        }
        return Math.sqrt(sum);
    }

    /**
     * Cliff bands: where the ground is steeper than 0.35, and in patches, its rise gathers into rock faces about
     * {@code band} blocks tall with ledges between. Still a height field: steeper, never overhanging.
     */
    private double cliffs(double x, double z, double m, double slope) {
        double k = smooth((slope - 0.35) / 0.5);
        double w = Math.max(0, Math.min(1, 0.45 + 1.3 * perlin(x / 320, z / 320, 33))) * k;
        if (w == 0) {
            return m;
        }
        double t = band * (1 + 0.45 * perlin(x / 400, z / 400, 31));
        double q = (m + t * perlin(x / 250, z / 250, 32)) / t;
        double f = q - Math.floor(q);
        double a = Math.pow(f, sharp);
        double g = a / (a + Math.pow(1 - f, sharp));
        return m + strata * w * t * (g - f);
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    private static long cellHash(long x, long z, long salt) {
        return mix(x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt);
    }

    private static double unit(long h, int n) {
        return (mix(h + n * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** 2D gradient noise, about -1 to 1, salted by the world seed. */
    private double perlin(double x, double z, long salt) {
        return gradientNoise(x, z, salt ^ seed);
    }

    private static double gradientNoise(double x, double z, long salt) {
        double x0 = Math.floor(x);
        double z0 = Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        long ix = (long) x0;
        long iz = (long) z0;
        double a = grad(ix, iz, salt, fx, fz);
        double b = grad(ix + 1, iz, salt, fx - 1, fz);
        double c = grad(ix, iz + 1, salt, fx, fz - 1);
        double e = grad(ix + 1, iz + 1, salt, fx - 1, fz - 1);
        double u = fade(fx);
        double v = fade(fz);
        return (a + (b - a) * u + (c - a) * v + (a - b - c + e) * u * v) * 1.41;
    }

    private static double grad(long ix, long iz, long salt, double dx, double dz) {
        double angle = unit(cellHash(ix, iz, salt), 0) * 2 * Math.PI;
        return Math.cos(angle) * dx + Math.sin(angle) * dz;
    }
}
