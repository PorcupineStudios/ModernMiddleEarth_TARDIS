package com.porcupinestudios.lotrport.world.delve;

/**
 * A goblin town's cavern: a long rift under the mountain along x or z, {@code length} long, up to {@code halfWidth}
 * either side of its axis, from a narrow crevasse at {@code bottom} (where a pool collects) widening to its middle
 * and closing in a dome at {@code top}; blunt at both ends. Its walls are roughened by 3D noise. A pure function, so
 * every chunk and the planner agree where rock is.
 */
final class RiftShape {
    final boolean alongX;
    /** World coordinate along the axis at u = 0, and of the axis line across it. */
    final int start;
    final int line;
    final int length;
    final int bottom;
    final int top;
    final double halfWidth;
    final double roughness;
    final long seed;

    RiftShape(boolean alongX, int start, int line, int length, int bottom, int top, double halfWidth,
              double roughness, long seed) {
        this.alongX = alongX;
        this.start = start;
        this.line = line;
        this.length = length;
        this.bottom = bottom;
        this.top = top;
        this.halfWidth = halfWidth;
        this.roughness = roughness;
        this.seed = seed;
    }

    int x(double u, double v) {
        return (int) Math.floor(alongX ? start + u : line + v);
    }

    int z(double u, double v) {
        return (int) Math.floor(alongX ? line + v : start + u);
    }

    double u(int x, int z) {
        return alongX ? x - start : z - start;
    }

    double v(int x, int z) {
        return alongX ? z - line : x - line;
    }

    /** Direction index (DelveSpec N/E/S/W) of +u and of +v. */
    int plusU() {
        return alongX ? DelveSpec.E : DelveSpec.S;
    }

    int plusV() {
        return alongX ? DelveSpec.S : DelveSpec.E;
    }

    /** The wall's distance from the axis at (u, y), before the 3D roughness. */
    double halfAt(double u, double y) {
        double s = u / length;
        if (s <= 0 || s >= 1 || y < bottom || y > top) {
            return 0;
        }
        double taper = Math.pow(Math.sin(Math.PI * s), 0.3);
        double h = (y - bottom) / (double) (top - bottom);
        double profile = h < 0.55 ? 0.3 + 0.7 * Math.sin(Math.PI * h / 1.1)
                : Math.pow(Math.max(0, Math.cos((h - 0.55) / 0.45 * Math.PI / 2)), 0.6);
        double wobble = 1 + 0.24 * (Rng.noise(seed + 5, u, y, 0, 23) - 0.5);
        return halfWidth * taper * profile * wobble;
    }

    boolean inside(int x, int y, int z) {
        double half = halfAt(u(x, z), y);
        if (half <= 0) {
            return false;
        }
        double rough = 1 + roughness * (Rng.noise(seed + 9, x, y, z, 7) - 0.5);
        return Math.abs(v(x, z)) < half * rough;
    }

    /** How far out from the axis (|v|) the first rock is at (u, y) on side {@code sign}, scanning from {@code from}. */
    int wallFrom(double u, int y, int sign, int from, int max) {
        for (int a = from; a <= max; a++) {
            if (!inside(x(u, sign * a), y, z(u, sign * a))) {
                return a;
            }
        }
        return max + 1;
    }
}
