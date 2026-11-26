package com.porcupinestudios.lotrport.world.delve;

/**
 * Drawing helpers over a {@link Canvas} for one piece: boxes and shells by palette role, clipped to the canvas, with
 * the delve's ruin (rough patches and cracked blocks in walls, rubble heaps, cobwebs) worked in. Every choice is a hash
 * of the world position, so it doesn't matter which chunk draws first.
 */
final class Draw {
    final Canvas c;
    final DelveSpec spec;
    final long seed;
    Palette p;

    Draw(Canvas c, DelveSpec spec, long seed) {
        this.c = c;
        this.spec = spec;
        this.seed = seed;
    }

    Draw palette(Palette palette) {
        this.p = palette;
        return this;
    }

    double u(int x, int y, int z, int salt) {
        return Rng.unit(Rng.hash(seed + salt, x, y, z));
    }

    long h(int x, int y, int z, int salt) {
        return Rng.hash(seed + salt * 7919L, x, y, z);
    }

    boolean inX(int x) {
        return x >= c.minX() && x <= c.maxX();
    }

    boolean inZ(int z) {
        return z >= c.minZ() && z <= c.maxZ();
    }

    boolean in(int x, int z) {
        return inX(x) && inZ(z);
    }

    void set(int x, int y, int z, Mat m) {
        if (m != Mat.KEEP && in(x, z) && y >= c.minY() && y <= c.maxY()) {
            c.set(x, y, z, m);
        }
    }

    Mat pick(String role, int x, int y, int z) {
        return p.pick(role, h(x, y, z, role.hashCode()));
    }

    void role(int x, int y, int z, String role) {
        if (in(x, z)) {
            set(x, y, z, pick(role, x, y, z));
        }
    }

    /** The role's block with properties set (stairs facing...), ignored by blocks without them. */
    void role(int x, int y, int z, String role, String k1, String v1) {
        if (in(x, z)) {
            set(x, y, z, pick(role, x, y, z).with(k1, v1));
        }
    }

    void role(int x, int y, int z, String role, String k1, String v1, String k2, String v2) {
        if (in(x, z)) {
            set(x, y, z, pick(role, x, y, z).with(k1, v1).with(k2, v2));
        }
    }

    boolean solid(int x, int y, int z) {
        return in(x, z) && c.solid(x, y, z);
    }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, Mat m) {
        int ax = Math.max(Math.min(x0, x1), c.minX());
        int bx = Math.min(Math.max(x0, x1), c.maxX());
        int az = Math.max(Math.min(z0, z1), c.minZ());
        int bz = Math.min(Math.max(z0, z1), c.maxZ());
        int ay = Math.max(Math.min(y0, y1), c.minY());
        int by = Math.min(Math.max(y0, y1), c.maxY());
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                for (int y = ay; y <= by; y++) {
                    c.set(x, y, z, m);
                }
            }
        }
    }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, String role) {
        int ax = Math.max(Math.min(x0, x1), c.minX());
        int bx = Math.min(Math.max(x0, x1), c.maxX());
        int az = Math.max(Math.min(z0, z1), c.minZ());
        int bz = Math.min(Math.max(z0, z1), c.maxZ());
        int ay = Math.max(Math.min(y0, y1), c.minY());
        int by = Math.min(Math.max(y0, y1), c.maxY());
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                for (int y = ay; y <= by; y++) {
                    set(x, y, z, pick(role, x, y, z));
                }
            }
        }
    }

    void air(int x0, int y0, int z0, int x1, int y1, int z1) {
        fill(x0, y0, z0, x1, y1, z1, Mat.AIR);
    }

    /** Fills only where there is no solid ground (air, water, lava, plants): hewn walls and foundations. */
    void fillOpen(int x0, int y0, int z0, int x1, int y1, int z1, String role) {
        int ax = Math.max(Math.min(x0, x1), c.minX());
        int bx = Math.min(Math.max(x0, x1), c.maxX());
        int az = Math.max(Math.min(z0, z1), c.minZ());
        int bz = Math.min(Math.max(z0, z1), c.maxZ());
        int ay = Math.max(Math.min(y0, y1), c.minY());
        int by = Math.min(Math.max(y0, y1), c.maxY());
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                for (int y = ay; y <= by; y++) {
                    if (!c.solid(x, y, z)) {
                        set(x, y, z, pick(role, x, y, z));
                    }
                }
            }
        }
    }

    /** A wall block with the ruin: some are bare rock where the facing fell away. */
    void wall(int x, int y, int z, String role) {
        if (!in(x, z)) {
            return;
        }
        if (u(x, y, z, 11) < spec.ruin.rough()) {
            set(x, y, z, pick("rough", x, y, z));
        } else {
            set(x, y, z, pick(role, x, y, z));
        }
    }

    /**
     * The shell around an interior box: floor under it, ceiling over it, walls round it (corners too), each a block
     * thick. The interior itself is not touched.
     */
    void shell(int x0, int y0, int z0, int x1, int y1, int z1, String floor, String wall, String ceiling) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            if (!inX(x)) {
                continue;
            }
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (!inZ(z)) {
                    continue;
                }
                boolean side = x < x0 || x > x1 || z < z0 || z > z1;
                if (side) {
                    for (int y = y0 - 1; y <= y1 + 1; y++) {
                        wall(x, y, z, wall);
                    }
                } else {
                    set(x, y0 - 1, z, pick(floor, x, y0 - 1, z));
                    set(x, y1 + 1, z, pick(ceiling, x, y1 + 1, z));
                }
            }
        }
    }

    /**
     * Rubble on a floor: heaps where a slow noise is high (more with more ruin), at most {@code cap} blocks, topped by
     * a slab now and then. Only where the floor below is solid and the block free.
     */
    void rubble(int x0, int z0, int x1, int z1, int floorY, int cap) {
        for (int x = Math.max(x0, c.minX()); x <= Math.min(x1, c.maxX()); x++) {
            for (int z = Math.max(z0, c.minZ()); z <= Math.min(z1, c.maxZ()); z++) {
                rubbleAt(x, z, floorY, cap);
            }
        }
    }

    /** One column of {@link #rubble}. */
    void rubbleAt(int x, int z, int floorY, int cap) {
        double r = spec.ruin.rubble();
        if (r <= 0 || !in(x, z)) {
            return;
        }
        double n = Rng.noise(seed + 3, x, floorY, z, 5);
        double t = (n - (1 - r)) / r;
        if (t <= 0) {
            return;
        }
        int height = Math.min(cap, (int) (t * 3.2));
        for (int i = 0; i < height; i++) {
            set(x, floorY + i, z, pick("rubble", x, floorY + i, z));
        }
        if (u(x, floorY, z, 5) < 0.5 + t * 0.5) {
            set(x, floorY + height, z, pick("rubble_top", x, floorY + height, z));
        }
    }

    /** Cobwebs along the top of an interior's walls. */
    void webs(int x0, int z0, int x1, int z1, int topY) {
        double w = spec.ruin.webs();
        if (w <= 0) {
            return;
        }
        for (int x = Math.max(x0, c.minX()); x <= Math.min(x1, c.maxX()); x++) {
            for (int z = Math.max(z0, c.minZ()); z <= Math.min(z1, c.maxZ()); z++) {
                boolean edgeX = x == x0 || x == x1;
                boolean edgeZ = z == z0 || z == z1;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                double chance = edgeX && edgeZ ? w : w * 0.12;
                if (u(x, topY, z, 7) < chance) {
                    set(x, topY, z, pick("web", x, topY, z));
                    if (edgeX && edgeZ && u(x, topY - 1, z, 7) < w) {
                        set(x, topY - 1, z, pick("web", x, topY - 1, z));
                    }
                }
            }
        }
    }

    static String facing(int side) {
        return DelveSpec.SIDES[side];
    }
}
