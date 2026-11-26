package com.porcupinestudios.lotrport.world.delve;

/**
 * Position hashes for delves. Every random choice while building is a hash of the world position (and the plan's
 * seed), never a running random, so a block comes out the same whichever chunk asks first.
 */
final class Rng {
    private Rng() {}

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static long hash(long seed, long a, long b, long c) {
        return mix(seed ^ mix(a * 0x9E3779B97F4A7C15L + mix(b * 0xC2B2AE3D27D4EB4FL + mix(c * 0x165667B19E3779F9L))));
    }

    static long hash(long seed, long a, long b, long c, long d) {
        return hash(hash(seed, a, b, c), d, 17, 31);
    }

    /** A hash as a double in [0, 1). */
    static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    static double unit(long seed, long a, long b, long c) {
        return unit(hash(seed, a, b, c));
    }

    /** A hash as an int in [lo, hi]. */
    static int range(long h, int lo, int hi) {
        return hi <= lo ? lo : lo + (int) Math.floorMod(h, (long) (hi - lo + 1));
    }

    /** Smooth value noise in [0, 1): trilinear between lattice points {@code scale} blocks apart. */
    static double noise(long seed, double x, double y, double z, double scale) {
        double fx = x / scale;
        double fy = y / scale;
        double fz = z / scale;
        long ix = (long) Math.floor(fx);
        long iy = (long) Math.floor(fy);
        long iz = (long) Math.floor(fz);
        double tx = smooth(fx - ix);
        double ty = smooth(fy - iy);
        double tz = smooth(fz - iz);
        double v = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1;
            int dy = (c >> 1) & 1;
            int dz = (c >> 2) & 1;
            double w = (dx == 1 ? tx : 1 - tx) * (dy == 1 ? ty : 1 - ty) * (dz == 1 ? tz : 1 - tz);
            v += w * unit(seed, ix + dx, iy + dy, iz + dz);
        }
        return v;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}
