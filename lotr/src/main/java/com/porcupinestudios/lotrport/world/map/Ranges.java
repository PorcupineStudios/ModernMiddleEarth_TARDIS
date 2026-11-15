package com.porcupinestudios.lotrport.world.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * The mountain ranges' own terrain (Aaron 2026-11-15: the Misty Mountains' 3D noise made overhangs and floating rock).
 * A height field, so it can't overhang: the range biomes' noise hilliness is replaced by a massif read off the map
 * (prototyped and tuned on shaded reliefs of the map, 2026-11-15):
 * <ul>
 * <li><b>depth</b>: how far a point is in from the range's edge on the map (an exact distance transform of the range
 * pixels, capped at 20 pixels, blurred twice 3x3). Narrow stretches of a range stay low; wide ones rise to a crest.
 * <li><b>envelope</b> s: depth over {@code reach} pixels, smoothstepped; everything is scaled by it and by the biome's
 * LOTR hilliness (its measured spread, so taller ranges stay taller), so the range rises out of its foothills.
 * <li><b>spurs</b>: long ridges on a jittered {@code cell}-block grid, each turned to run down the slope of the depth
 * field (from the crest to the foothills), with valleys between: the pattern of real ranges. In a range's middle,
 * where the depth is flat, they are round summits instead.
 * <li><b>relief</b>: warped ridged noise, the crags and gullies on top.
 * <li><b>peaks</b>: LOTR's named mountains inside a range (Caradhras, Gundabad, the dwarf mountains of the Blue
 * Mountains...) rise as broad summits over the rest.
 * <li><b>passes</b>: within {@code pass} blocks of a road (the East-West road's High Pass, the Blue Mountains' dwarf
 * road...) the massif sinks to a fifth, a valley for the road.
 * </ul>
 * Rivers drawn on the map are not range pixels, so they cut valleys into it (the Bruinen, the Gladden).
 */
final class Ranges {
    private static final int TILE = 64;
    private static final int CAP = 20;
    private static final int MARGIN = CAP + 3;

    private final int width;
    private final int height;
    private final int blocksPerPixel;
    private final int originX;
    private final int originZ;
    private final boolean[] isRange = new boolean[256];
    /** Tiles of the blurred depth field in pixels; null = no range near, -0.5 throughout. */
    private final float[][] tiles;
    private final int tilesX;
    private final double reach;
    private final double cell;
    private final double crest;
    private final double spurs;
    private final double relief;
    private final double sink;
    private final double wave;
    private final double peak;
    private final double peakReach;
    private final double pass;
    private final double passFloor;
    private final List<double[]> peaks = new ArrayList<>();

    Ranges(JsonObject o, List<MiddleEarthMap.Entry> biomes, byte[] pixels, int width, int height, int blocksPerPixel,
            int originX, int originZ) {
        this.width = width;
        this.height = height;
        this.blocksPerPixel = blocksPerPixel;
        this.originX = originX;
        this.originZ = originZ;
        for (JsonElement e : o.getAsJsonArray("biomes")) {
            for (int i = 0; i < biomes.size(); i++) {
                if (biomes.get(i).id().equals(e.getAsString())) {
                    isRange[i] = true;
                }
            }
        }
        reach = o.get("reach").getAsDouble();
        cell = o.get("cell").getAsDouble();
        crest = o.get("crest").getAsDouble();
        spurs = o.get("spurs").getAsDouble();
        relief = o.get("relief").getAsDouble();
        sink = o.get("sink").getAsDouble();
        wave = o.get("wave").getAsDouble();
        peak = o.get("peak").getAsDouble();
        peakReach = o.get("peak_reach").getAsDouble();
        pass = o.get("pass").getAsDouble();
        passFloor = o.get("pass_floor").getAsDouble();
        tilesX = (width + TILE - 1) / TILE;
        int tilesZ = (height + TILE - 1) / TILE;
        tiles = new float[tilesX * tilesZ][];
        for (int tz = 0; tz < tilesZ; tz++) {
            for (int tx = 0; tx < tilesX; tx++) {
                tiles[tz * tilesX + tx] = tile(pixels, tx * TILE, tz * TILE);
            }
        }
    }

    /** How far from a road (blocks) the massif sinks towards a pass. */
    double passWidth() {
        return pass;
    }

    boolean isRange(int biome) {
        return isRange[biome];
    }

    /** LOTR's mountains inside a range become its summits (the caller keeps the rest's cones). */
    boolean takePeak(int x, int z, int range) {
        if (depth(x + 0.5, z + 0.5) <= 0) {
            return false;
        }
        peaks.add(new double[] {x, z, range * peakReach});
        return true;
    }

    private boolean range(byte[] pixels, int px, int pz) {
        if (px < 0 || pz < 0 || px >= width || pz >= height) {
            return false;
        }
        int v = pixels[pz * width + px] & 0xFF;
        return v != 255 && isRange[v];
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
     * Blocks the ground rises over the range biome's own height, for a range whose LOTR hilliness here is {@code std}
     * (smoothed, so it fades out over the border) and that is {@code road} blocks from a road.
     */
    double massif(int x, int z, double std, double road) {
        double d = depth(x, z);
        double s = Math.max(0, Math.min(1, d / reach));
        s = s * s * (3 - 2 * s);
        if (s == 0) {
            return 0;
        }
        double m = s * std * (crest * s + spurs * spurs(x, z) + relief * relief(x, z) + peak * peaks(x, z) - sink);
        if (road < pass) {
            double r = road / pass;
            m *= passFloor + (1 - passFloor) * r * r * (3 - 2 * r);
        }
        return m;
    }

    private double spurs(double x, double z) {
        double cx = Math.floor(x / cell);
        double cz = Math.floor(z / cell);
        double sum = 0;
        // 5 x 5 cells: the longest spur (2 cells) can't reach from further
        for (int i = -2; i <= 2; i++) {
            for (int k = -2; k <= 2; k++) {
                long h = cellHash((long) cx + i, (long) cz + k, 0x5350555253L);
                double hx = (cx + i + 0.1 + 0.8 * unit(h, 1)) * cell;
                double hz = (cz + k + 0.1 + 0.8 * unit(h, 2)) * cell;
                // downhill here: the slope of the depth field (pixels per pixel)
                double gx = depth(hx + 64, hz) - depth(hx - 64, hz);
                double gz = depth(hx, hz + 64) - depth(hx, hz - 64);
                double g = Math.sqrt(gx * gx + gz * gz);
                if (g < 1e-6) {
                    gx = 1;
                    g = 1;
                }
                double along = Math.min(1, g); // 0 in the range's middle: a round summit
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
                double r2 = a * a + b * b;
                if (r2 < 1) {
                    double u = 1 - r2;
                    double hill = tall * u * u;
                    sum += hill * hill;
                }
            }
        }
        return Math.sqrt(sum);
    }

    private double relief(double x, double z) {
        double wx = x + 120 * perlin(x / 700, z / 700, 11);
        double wz = z + 120 * perlin(x / 700, z / 700, 12);
        double total = 0;
        double norm = 0;
        double amp = 1;
        double weight = 1;
        double f = 1 / wave;
        for (int o = 0; o < 4; o++) {
            double n = perlin(wx * f, wz * f, 100 + o);
            double r = Math.max(0, Math.min(1, 1 - Math.sqrt(n * n + 0.004)));
            r = r * r * weight;
            weight = Math.max(0, Math.min(1, r * 1.6));
            total += r * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2.03;
        }
        return total / norm * 1.6;
    }

    private double peaks(double x, double z) {
        double best = 0;
        for (double[] p : peaks) {
            double dx = x - p[0];
            double dz = z - p[1];
            double r2 = (dx * dx + dz * dz) / (p[2] * p[2]);
            if (r2 < 1) {
                double u = 1 - r2;
                best = Math.max(best, u * u);
            }
        }
        return best;
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

    /** 2D gradient noise, about -1 to 1. */
    private static double perlin(double x, double z, long salt) {
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
