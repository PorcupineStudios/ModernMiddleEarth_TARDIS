package com.porcupinestudios.lotrport.world.delve;

import java.util.List;
import java.util.Set;

/**
 * The pieces a goblin town is built from ({@link GoblinPlanner}): the rift and its pool and falls, rough tunnels, the
 * scaffolding (platforms, walkways along the walls, bridges across, ladders between levels, huts, hanging cages), the
 * Great Goblin's throne on its rock spire, the Front Porch and its chute, the landing, Gollum's lake and the back door.
 * Platforms and bridges ask the rift's shape where rock is, never the world, so every chunk builds the same scaffold.
 */
final class GoblinPieces {
    private GoblinPieces() {}

    static String dir(int side) {
        return DelveSpec.SIDES[side];
    }

    static String axisOf(int side) {
        return side == DelveSpec.E || side == DelveSpec.W ? "x" : "z";
    }

    /** A rough tunnel along a 3D path (floor heights interpolated, half-block steps), sealed where it meets air. */
    static final class TunnelPiece extends Piece {
        private final double[][] pts;
        private final double[] cum;
        private final int half;
        private final int height;
        private final double rough;
        private final int water;
        private final Palette pal;

        TunnelPiece(List<double[]> points, int half, int height, double rough, int water, Palette pal) {
            super(0);
            this.pts = points.toArray(new double[0][]);
            this.half = half;
            this.height = height;
            this.rough = rough;
            this.water = water;
            this.pal = pal;
            cum = new double[pts.length];
            double minX = 1e9;
            double minZ = 1e9;
            double maxX = -1e9;
            double maxZ = -1e9;
            double minY = 1e9;
            double maxY = -1e9;
            for (int i = 0; i < pts.length; i++) {
                if (i > 0) {
                    cum[i] = cum[i - 1] + Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][2] - pts[i - 1][2]);
                }
                minX = Math.min(minX, pts[i][0]);
                maxX = Math.max(maxX, pts[i][0]);
                minZ = Math.min(minZ, pts[i][2]);
                maxZ = Math.max(maxZ, pts[i][2]);
                minY = Math.min(minY, pts[i][1]);
                maxY = Math.max(maxY, pts[i][1]);
            }
            int pad = half + 3 + (int) Math.ceil(rough);
            bounds((int) Math.floor(minX) - pad, (int) Math.floor(minY) - 3, (int) Math.floor(minZ) - pad,
                    (int) Math.ceil(maxX) + pad, (int) Math.ceil(maxY) + height + 3, (int) Math.ceil(maxZ) + pad);
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            for (int x = Math.max(x0, d.c.minX()); x <= Math.min(x1, d.c.maxX()); x++) {
                for (int z = Math.max(z0, d.c.minZ()); z <= Math.min(z1, d.c.maxZ()); z++) {
                    double best = Double.MAX_VALUE;
                    double floor = 0;
                    for (int i = 0; i + 1 < pts.length; i++) {
                        double ax = pts[i][0];
                        double az = pts[i][2];
                        double dx = pts[i + 1][0] - ax;
                        double dz = pts[i + 1][2] - az;
                        double len = dx * dx + dz * dz;
                        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((x + 0.5 - ax) * dx + (z + 0.5 - az) * dz) / len));
                        double dist = Math.hypot(ax + t * dx - x - 0.5, az + t * dz - z - 0.5);
                        if (dist < best) {
                            best = dist;
                            floor = pts[i][1] + t * (pts[i + 1][1] - pts[i][1]);
                        }
                    }
                    double hw = half + (Rng.noise(d.seed + 81, x, 0, z, 6) - 0.5) * 2 * rough;
                    if (best > hw + 1.6) {
                        continue;
                    }
                    double q = Math.round(floor * 2) / 2.0;
                    int fl = (int) Math.floor(q);
                    boolean halfStep = q != fl;
                    int top = fl + height + (int) Math.round((Rng.noise(d.seed + 83, x, 1, z, 5) - 0.5) * 2 * rough);
                    if (best <= hw) {
                        for (int y = fl; y < top; y++) {
                            d.set(x, y, z, y < water ? d.pick("water", x, y, z) : Mat.AIR);
                        }
                        d.set(x, fl - 1, z, d.pick("tunnel_floor", x, fl - 1, z));
                        if (halfStep && fl >= water) {
                            d.role(x, fl, z, "tunnel_slab", "type", "bottom");
                        }
                        d.fillOpen(x, top, z, x, top, z, "rock");
                    } else {
                        d.fillOpen(x, fl - 1, z, x, top, z, "rock");
                    }
                }
            }
        }
    }

    /** The rift itself: carved out, a pool at the bottom, waterfalls spilling from the walls into it. */
    static final class RiftPiece extends Piece {
        private final RiftShape rift;
        private final int poolTop;
        private final int[][] falls;
        private final Palette pal;

        RiftPiece(RiftShape rift, int poolTop, int[][] falls, Palette pal) {
            super(1);
            this.rift = rift;
            this.poolTop = poolTop;
            this.falls = falls;
            this.pal = pal;
            int reach = (int) Math.ceil(rift.halfWidth * (1 + rift.roughness) + 3);
            bounds(rift.x(0, -reach), rift.bottom - 1, rift.z(0, -reach), rift.x(rift.length, reach), rift.top + 1,
                    rift.z(rift.length, reach));
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            double limit = rift.halfWidth * (1 + rift.roughness) + 1;
            for (int x = Math.max(x0, d.c.minX()); x <= Math.min(x1, d.c.maxX()); x++) {
                for (int z = Math.max(z0, d.c.minZ()); z <= Math.min(z1, d.c.maxZ()); z++) {
                    if (Math.abs(rift.v(x, z)) > limit) {
                        continue;
                    }
                    for (int y = rift.bottom; y <= rift.top; y++) {
                        if (rift.inside(x, y, z)) {
                            d.set(x, y, z, y < poolTop ? d.pick("water", x, y, z) : Mat.AIR);
                        }
                    }
                }
            }
            for (int[] f : falls) {
                int u = f[0];
                int sign = f[1];
                int ys = f[2];
                int a = rift.wallFrom(u, ys, sign, 0, (int) limit + 4);
                d.set(rift.x(u, sign * a), ys, rift.z(u, sign * a), d.pick("water", 0, 0, 0));
                int fx = rift.x(u, sign * (a - 1));
                int fz = rift.z(u, sign * (a - 1));
                d.set(fx, ys, fz, d.pick("water", 0, 0, 0));
                for (int y = ys - 1; y >= poolTop && rift.inside(fx, y, fz); y--) {
                    d.set(fx, y, fz, d.pick("falling_water", fx, y, fz));
                }
            }
        }
    }

    /** One platform against the rift wall: a deck out to the rock, rails on the chasm edge, posts or braces under it. */
    static final class PlatformPiece extends Piece {
        final RiftShape rift;
        final int y;
        final int sign;
        final int u0;
        final int u1;
        final int inner;
        final boolean hut;
        final boolean brazier;
        final int maxPost;
        final Set<Integer> openings;
        final Palette pal;

        PlatformPiece(RiftShape rift, int y, int sign, int u0, int u1, int inner, boolean hut, boolean brazier,
                      int maxPost, Set<Integer> openings, Palette pal) {
            super(2);
            this.rift = rift;
            this.y = y;
            this.sign = sign;
            this.u0 = u0;
            this.u1 = u1;
            this.inner = inner;
            this.hut = hut;
            this.brazier = brazier;
            this.maxPost = maxPost;
            this.openings = openings;
            this.pal = pal;
            int far = inner + 14 + maxPost;
            bounds(rift.x(u0, sign * inner), y - maxPost - 2, rift.z(u0, sign * inner), rift.x(u1, sign * far), y + 5,
                    rift.z(u1, sign * far));
            zone("town");
        }

        int x(int u, int a) {
            return rift.x(u, sign * a);
        }

        int z(int u, int a) {
            return rift.z(u, sign * a);
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            String along = axisOf(rift.plusU());
            int outward = sign > 0 ? rift.plusV() : (rift.plusV() + 2) % 4;
            int wallMax = inner + 14;
            for (int u = u0; u <= u1; u++) {
                int outer = inner;
                for (int a = inner; a <= wallMax; a++) {
                    int x = x(u, a);
                    int z = z(u, a);
                    if (!rift.inside(x, y - 1, z) && a > inner) {
                        break;
                    }
                    d.set(x, y - 1, z, d.pick("deck", x, y - 1, z));
                    outer = a;
                }
                boolean post = u == u0 || u == u1 || (u - u0) % 4 == 0;
                if (!openings.contains(u)) {
                    d.set(x(u, inner), y, z(u, inner), post ? d.pick("post", 0, 0, 0).with("axis", "y")
                            : d.pick("rail", x(u, inner), y, z(u, inner)).with("axis", along));
                    if (post) {
                        d.set(x(u, inner), y + 1, z(u, inner), d.pick("post", 0, 0, 0).with("axis", "y"));
                    }
                }
                if (post) {
                    support(d, u, inner);
                }
                if (u == (u0 + u1) / 2 && outer > inner + 1) {
                    if (brazier) {
                        d.role(x(u, inner + 1), y, z(u, inner + 1), "brazier");
                    } else if (d.u(x(u, inner), y, z(u, inner), 3) < 0.5) {
                        d.role(x(u, inner + 1), y - 2, z(u, inner + 1), "lantern");
                    }
                }
                // a little junk against the wall
                if (outer > inner + 2 && d.u(x(u, outer), y, z(u, outer), 5) < 0.22 && !hut) {
                    Mat m = d.pick("junk", x(u, outer), y, z(u, outer));
                    d.set(x(u, outer), y, z(u, outer), m.with("facing", dir((outward + 2) % 4)));
                }
            }
            if (hut) {
                hut(d, outward);
            }
        }

        /** A post down to the rock below, or a diagonal brace into the wall if the drop is too deep. */
        void support(Draw d, int u, int a) {
            int depth = -1;
            for (int k = 2; k <= maxPost; k++) {
                if (!rift.inside(x(u, a), y - k, z(u, a))) {
                    depth = k;
                    break;
                }
            }
            if (depth > 0) {
                for (int k = 2; k < depth; k++) {
                    d.set(x(u, a), y - k, z(u, a), d.pick("post", 0, 0, 0).with("axis", "y"));
                }
                return;
            }
            for (int j = 1; j <= maxPost; j++) {
                int x = x(u, a + j);
                int z = z(u, a + j);
                if (!rift.inside(x, y - 1 - j, z)) {
                    break;
                }
                d.set(x, y - 1 - j, z, d.pick("beam", x, y - 1 - j, z).with("axis", "y"));
                if (j > 1) {
                    d.set(x(u, a + j - 1), y - 1 - j, z(u, a + j - 1),
                            d.pick("beam", x, y - 1 - j, z).with("axis", axisOf(rift.plusV())));
                }
            }
        }

        /** A lean-to shack against the wall: plank walls with gaps, a sloping stair roof, a doorway to the edge. */
        void hut(Draw d, int outward) {
            int uc = (u0 + u1) / 2;
            int a0 = inner + 1;
            int a1 = inner + 5;
            for (int u = uc - 2; u <= uc + 2; u++) {
                for (int a = a0; a <= a1; a++) {
                    int x = x(u, a);
                    int z = z(u, a);
                    boolean wall = u == uc - 2 || u == uc + 2 || a == a0 || a == a1;
                    for (int yy = y; yy < y + 3; yy++) {
                        boolean door = a == a0 && u == uc && yy < y + 2;
                        d.set(x, yy, z, wall && !door ? d.pick("hut_wall", x, yy, z) : Mat.AIR);
                    }
                    d.set(x, y + 3, z, d.pick("roof", x, y + 3, z).with("facing", dir(outward)).with("half", "bottom"));
                }
            }
            d.role(x(uc - 1, a1 - 1), y, z(uc - 1, a1 - 1), "straw");
            Mat chest = d.pick("hut_store", x(uc + 1, a1 - 1), y, z(uc + 1, a1 - 1));
            d.set(x(uc + 1, a1 - 1), y, z(uc + 1, a1 - 1), chest.with("facing", dir((outward + 2) % 4)));
            d.role(x(uc, a0 + 2), y + 2, z(uc, a0 + 2), "lantern");
        }
    }

    /** A walkway hugging the wall between two platforms: three planks wide, railed, braced into the rock. */
    static final class WalkwayPiece extends Piece {
        private final RiftShape rift;
        private final int y;
        private final int sign;
        private final int ua;
        private final int ub;
        private final int width;
        private final Palette pal;

        WalkwayPiece(RiftShape rift, int y, int sign, int ua, int ub, int width, Palette pal) {
            super(2);
            this.rift = rift;
            this.y = y;
            this.sign = sign;
            this.ua = ua;
            this.ub = ub;
            this.width = width;
            this.pal = pal;
            int far = (int) Math.ceil(rift.halfWidth * (1 + rift.roughness)) + 4;
            bounds(rift.x(ua, 0), y - 14, rift.z(ua, 0), rift.x(ub, sign * far), y + 2, rift.z(ub, sign * far));
            zone("town");
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            String along = axisOf(rift.plusU());
            int max = (int) Math.ceil(rift.halfWidth * (1 + rift.roughness)) + 3;
            for (int u = ua; u <= ub; u++) {
                int wall = rift.wallFrom(u, y, sign, 0, max);
                int in = wall - width;
                if (in < 1) {
                    continue;
                }
                for (int a = in; a < wall + 2; a++) {
                    int x = rift.x(u, sign * a);
                    int z = rift.z(u, sign * a);
                    if (a >= wall && !rift.inside(x, y - 1, z)) {
                        break;
                    }
                    if (d.u(x, y - 1, z, 23) < d.spec.ruin.holes()) {
                        continue;
                    }
                    d.set(x, y - 1, z, d.pick("deck", x, y - 1, z));
                }
                int rx = rift.x(u, sign * in);
                int rz = rift.z(u, sign * in);
                boolean post = (u - ua) % 5 == 0;
                d.set(rx, y, rz, post ? d.pick("post", 0, 0, 0).with("axis", "y")
                        : d.pick("rail", rx, y, rz).with("axis", along));
                if (post) {
                    for (int j = 1; j <= 12; j++) {
                        int x = rift.x(u, sign * (in + j));
                        int z = rift.z(u, sign * (in + j));
                        if (!rift.inside(x, y - 1 - j, z)) {
                            break;
                        }
                        d.set(x, y - 1 - j, z, d.pick("beam", x, y - 1 - j, z).with("axis", "y"));
                    }
                    if (d.u(rx, y, rz, 29) < 0.3) {
                        d.role(rift.x(u, sign * (in + 1)), y - 2, rift.z(u, sign * (in + 1)), "lantern");
                    }
                }
            }
        }
    }

    /**
     * A bridge across the rift at u, from {@code -aLeft} to {@code +aRight} (or on to the rock with {@code toWall}):
     * a sagging rope bridge (half-block steps) or a flat plank one, railed with rope and posted at its ends.
     */
    static final class BridgePiece extends Piece {
        private final RiftShape rift;
        private final int y;
        private final int u;
        private final int aLeft;
        private final int aRight;
        private final int half;
        private final double sag;
        private final boolean rope;
        private final Palette pal;

        BridgePiece(RiftShape rift, int y, int u, int aLeft, int aRight, int half, double sag, boolean rope,
                    Palette pal) {
            super(2);
            this.rift = rift;
            this.y = y;
            this.u = u;
            this.aLeft = aLeft;
            this.aRight = aRight;
            this.half = half;
            this.sag = sag;
            this.rope = rope;
            this.pal = pal;
            bounds(rift.x(u - half - 1, -aLeft), y - (int) Math.ceil(sag) - 2, rift.z(u - half - 1, -aLeft),
                    rift.x(u + half + 1, aRight), y + 3, rift.z(u + half + 1, aRight));
            zone("town");
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            String across = axisOf(rift.plusV());
            int span = aLeft + aRight;
            for (int v = -aLeft; v <= aRight; v++) {
                double t = (v + aLeft) / (double) Math.max(1, span);
                double s = y - sag * 4 * t * (1 - t);
                double q = Math.round(s * 2) / 2.0;
                int fl = (int) Math.floor(q);
                boolean raised = q != fl;
                for (int w = -half; w <= half; w++) {
                    int x = rift.x(u + w, v);
                    int z = rift.z(u + w, v);
                    if (rope) {
                        if (d.u(x, fl, z, 31) < d.spec.ruin.holes() * 0.5) {
                            continue;
                        }
                        d.set(x, fl, z, d.pick("bridge", x, fl, z).with("axis", across)
                                .with("half", raised ? "top" : "bottom"));
                    } else if (raised) {
                        d.set(x, fl, z, d.pick("deck_slab", x, fl, z).with("type", "bottom"));
                    } else {
                        d.set(x, fl - 1, z, d.pick("deck_slab", x, fl - 1, z).with("type", "top"));
                    }
                }
                for (int w : new int[] {-half - 1, half + 1}) {
                    int x = rift.x(u + w, v);
                    int z = rift.z(u + w, v);
                    boolean end = v == -aLeft || v == aRight;
                    int ry = rope && raised ? fl + 1 : fl;
                    if (end) {
                        d.set(x, ry, z, d.pick("post", 0, 0, 0).with("axis", "y"));
                        d.set(x, ry + 1, z, d.pick("post", 0, 0, 0).with("axis", "y"));
                    } else {
                        d.set(x, ry, z, d.pick("rail", x, ry, z).with("axis", across));
                    }
                }
            }
        }
    }

    /** A plank pillar with a ladder from a lower deck up through a hatch in the one above. */
    static final class LadderPiece extends Piece {
        private final RiftShape rift;
        private final int sign;
        private final int u;
        private final int a;
        private final int yLow;
        private final int yHigh;
        private final Palette pal;

        LadderPiece(RiftShape rift, int sign, int u, int a, int yLow, int yHigh, Palette pal) {
            super(3);
            this.rift = rift;
            this.sign = sign;
            this.u = u;
            this.a = a;
            this.yLow = yLow;
            this.yHigh = yHigh;
            this.pal = pal;
            bounds(rift.x(u, sign * a), yLow - 1, rift.z(u, sign * a), rift.x(u, sign * (a + 1)), yHigh + 1,
                    rift.z(u, sign * (a + 1)));
            zone("town");
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            int inward = sign > 0 ? (rift.plusV() + 2) % 4 : rift.plusV();
            int px = rift.x(u, sign * (a + 1));
            int pz = rift.z(u, sign * (a + 1));
            int lx = rift.x(u, sign * a);
            int lz = rift.z(u, sign * a);
            for (int yy = yLow; yy < yHigh; yy++) {
                d.set(px, yy, pz, d.pick("ladder_post", px, yy, pz));
                d.set(lx, yy, lz, d.pick("ladder", lx, yy, lz).with("facing", dir(inward)));
            }
        }
    }

    /** A cage (or two) hanging on a chain from the rift's roof. */
    static final class CagePiece extends Piece {
        private final int x;
        private final int z;
        private final int yCage;
        private final int yTop;
        private final Palette pal;

        CagePiece(int x, int z, int yCage, int yTop, Palette pal) {
            super(3);
            this.x = x;
            this.z = z;
            this.yCage = yCage;
            this.yTop = yTop;
            this.pal = pal;
            bounds(x, yCage - 1, z, x, yTop, z);
            zone("town");
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            d.role(x, yCage, z, "cage");
            if (d.u(x, yCage, z, 37) < 0.5) {
                d.role(x, yCage - 1, z, "cage");
            }
            for (int y = yCage + 1; y <= yTop; y++) {
                d.set(x, y, z, d.pick("chain", x, y, z).with("axis", "y"));
            }
        }
    }

    /**
     * The Great Goblin's court: a broad deck on a rock spire rising out of the pool, braziers, a dais and a throne of
     * logs and skulls, cages hanging round it.
     */
    static final class ThronePiece extends Piece {
        private final RiftShape rift;
        private final int y;
        private final int uc;
        private final int hl;
        private final int hw;
        private final int spire;
        private final int poolBottom;
        private final int bridgeHalf;
        private final Palette pal;

        ThronePiece(RiftShape rift, int y, int uc, int hl, int hw, int spire, int bridgeHalf, Palette pal) {
            super(3);
            this.rift = rift;
            this.y = y;
            this.uc = uc;
            this.hl = hl;
            this.hw = hw;
            this.spire = spire;
            this.bridgeHalf = bridgeHalf;
            this.poolBottom = rift.bottom;
            this.pal = pal;
            bounds(rift.x(uc - hl - 2, -hw - 2), rift.bottom, rift.z(uc - hl - 2, -hw - 2),
                    rift.x(uc + hl + 2, hw + 2), y + 6, rift.z(uc + hl + 2, hw + 2));
            zone("throne");
        }

        private double spireRadius(int yy) {
            double flareFrom = y - 16;
            double t = Math.max(0, (yy - flareFrom) / 15.0);
            return spire + (Math.min(hw, hl) * 0.8 - spire) * Math.pow(Math.min(1, t), 1.6);
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            String along = axisOf(rift.plusU());
            String across = axisOf(rift.plusV());
            for (int u = uc - hl - 2; u <= uc + hl + 2; u++) {
                for (int v = -hw - 2; v <= hw + 2; v++) {
                    int x = rift.x(u, v);
                    int z = rift.z(u, v);
                    if (!d.in(x, z)) {
                        continue;
                    }
                    double r = Math.hypot(u - uc, v) + (Rng.noise(d.seed + 91, x, 0, z, 5) - 0.5) * 2;
                    for (int yy = poolBottom; yy < y - 1; yy++) {
                        if (r < spireRadius(yy)) {
                            d.set(x, yy, z, d.pick("spire", x, yy, z));
                        }
                    }
                    if (Math.abs(u - uc) <= hl && Math.abs(v) <= hw) {
                        d.set(x, y - 1, z, d.pick("deck", x, y - 1, z));
                        for (int yy = y; yy < y + 5; yy++) {
                            d.set(x, yy, z, Mat.AIR);
                        }
                        boolean edgeU = Math.abs(u - uc) == hl;
                        boolean edgeV = Math.abs(v) == hw;
                        boolean bridgeGap = edgeV && Math.abs(u - uc) <= bridgeHalf;
                        if ((edgeU || edgeV) && !bridgeGap) {
                            boolean corner = edgeU && edgeV;
                            boolean post = corner || (edgeU ? Math.abs(v) % 4 == 0 : Math.abs(u - uc) % 4 == 0);
                            if (post) {
                                d.set(x, y, z, d.pick("post", 0, 0, 0).with("axis", "y"));
                                d.set(x, y + 1, z, d.pick("post", 0, 0, 0).with("axis", "y"));
                                if (corner) {
                                    d.role(x, y + 2, z, "brazier");
                                }
                            } else {
                                d.set(x, y, z, d.pick("rail", x, y, z).with("axis", edgeU ? across : along));
                            }
                        }
                    }
                }
            }
            // the dais and the throne at the far end
            int far = uc + hl - 1;
            for (int u = far - 4; u <= far; u++) {
                for (int v = -4; v <= 4; v++) {
                    int x = rift.x(u, v);
                    int z = rift.z(u, v);
                    d.set(x, y, z, d.pick(u == far - 4 ? "throne_step" : "throne_floor", x, y, z)
                            .with("facing", dir((rift.plusU() + 2) % 4)).with("half", "bottom"));
                }
            }
            int sx = rift.x(far - 1, 0);
            int sz = rift.z(far - 1, 0);
            d.set(sx, y + 1, sz, d.pick("throne_seat", sx, y + 1, sz).with("facing", dir(rift.plusU()))
                    .with("half", "bottom"));
            for (int yy = y + 1; yy <= y + 4; yy++) {
                d.set(rift.x(far, 0), yy, rift.z(far, 0), d.pick("throne_back", 0, yy, 0).with("axis", "y"));
            }
            for (int v : new int[] {-1, 1}) {
                d.set(rift.x(far - 1, v), y + 1, rift.z(far - 1, v), d.pick("deck_slab", 0, 0, 0).with("type", "bottom"));
                d.set(rift.x(far, v * 2), y + 1, rift.z(far, v * 2), d.pick("post", 0, 0, 0).with("axis", "y"));
                d.set(rift.x(far, v * 2), y + 2, rift.z(far, v * 2), d.pick("post", 0, 0, 0).with("axis", "y"));
                d.set(rift.x(far, v * 2), y + 3, rift.z(far, v * 2), d.pick("skull", 0, 0, 0));
                d.role(rift.x(far - 4, v * 4), y + 1, rift.z(far - 4, v * 4), "brazier");
            }
            d.role(rift.x(far, 0), y + 5, rift.z(far, 0), "skull");
            // bones and junk strewn round the dais
            for (int i = 0; i < 10; i++) {
                int u = uc - hl + 2 + Rng.range(Rng.hash(d.seed, 141, i, uc), 0, 2 * hl - 8);
                int v = Rng.range(Rng.hash(d.seed, 143, i, uc), -hw + 2, hw - 2);
                d.role(rift.x(u, v), y, rift.z(u, v), "court_junk");
            }
        }
    }

    /**
     * A shallow cave in a cliff face, the Front Porch: dry, sheltered, inviting. Its floor's back part is porch floor
     * (one block everywhere): over a chute down to the landing for the real one, which makes it a trap, on solid rock
     * for a decoy; everything else is built the same, so nobody can tell them apart by looking.
     */
    static final class PorchPiece extends Piece {
        private final int px;
        private final int pz;
        private final int floor;
        private final int in;
        private final boolean armed;
        private final int landingTop;
        private final int width;
        private final int height;
        private final int depth;
        private final int from;
        private final int to;
        private final int floorHalf;
        private final Palette pal;

        PorchPiece(int px, int pz, int floor, int in, boolean armed, int landingTop, int width, int height, int depth,
                   int from, int to, int floorHalf, Palette pal) {
            super(1);
            this.px = px;
            this.pz = pz;
            this.floor = floor;
            this.in = in;
            this.armed = armed;
            this.landingTop = landingTop;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.from = from;
            this.to = to;
            this.floorHalf = floorHalf;
            this.pal = pal;
            int r = width / 2 + 3;
            int[] a = xz(-3, -r);
            int[] b = xz(depth + 4, r);
            bounds(a[0], landingTop == Integer.MIN_VALUE ? floor - 2 : landingTop, a[1], b[0], floor + height + 2, b[1]);
            zone("porch");
        }

        int[] xz(int u, int v) {
            boolean ew = in == DelveSpec.E || in == DelveSpec.W;
            return ew ? new int[] {px + DelveSpec.DX[in] * u, pz + v} : new int[] {px + v, pz + DelveSpec.DZ[in] * u};
        }

        boolean trapFloor(int u, int v) {
            return u >= from && u <= to && Math.abs(v) <= floorHalf;
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            int r = width / 2 + 3;
            for (int u = -3; u <= depth + 3; u++) {
                for (int v = -r; v <= r; v++) {
                    int[] p = xz(u, v);
                    int x = p[0];
                    int z = p[1];
                    if (!d.in(x, z)) {
                        continue;
                    }
                    // the shape depends only on the porch's place: a real porch and a decoy at one spot are twins
                    double f = Math.max(0, u) / (double) depth;
                    double hw = width / 2.0 * (1 - 0.3 * f * f) + (Rng.noise(d.seed + 101, x, floor, z, 4) - 0.5) * 1.4;
                    int hh = (int) Math.round(height - 2 * f + (Rng.noise(d.seed + 103, x, floor, z, 4) - 0.5) * 1.6);
                    boolean crack = v == 0 && u > depth && u <= depth + 3;
                    if (u > depth && !crack) {
                        continue;
                    }
                    if (crack) {
                        d.air(x, floor, z, x, floor + 3, z);
                        continue;
                    }
                    if (Math.abs(v) <= hw) {
                        for (int yy = floor; yy < floor + hh; yy++) {
                            d.set(x, yy, z, Mat.AIR);
                        }
                        if (trapFloor(u, v)) {
                            d.set(x, floor - 1, z, d.pick("porch_floor", x, floor - 1, z));
                        } else if (u >= 0) {
                            d.set(x, floor - 1, z, d.pick("porch_rock", x, floor - 1, z));
                        } else {
                            d.fillOpen(x, floor - 4, z, x, floor - 1, z, "porch_rock");
                        }
                        if (u >= 0) {
                            d.fillOpen(x, floor + hh, z, x, floor + hh, z, "rock");
                        }
                    } else if (u >= 0 && Math.abs(v) <= hw + 1.5) {
                        d.fillOpen(x, floor - 1, z, x, floor + hh, z, "rock");
                    }
                    // the same few stones and cobwebs in every porch, never on the trap floor
                    if (u >= 1 && u < from && Math.abs(v) <= hw - 1 && d.u(x, floor, z, 107) < 0.08) {
                        d.role(x, floor, z, "porch_decor");
                    }
                    if (u >= 2 && Math.abs(Math.abs(v) - Math.floor(hw)) < 1 && d.u(x, floor + hh, z, 109) < 0.3) {
                        d.role(x, floor + hh - 1, z, "web");
                    }
                    if (landingTop != Integer.MIN_VALUE) {
                        chute(d, x, z, u, v);
                    } else if (trapFloor(u, v)) {
                        // a decoy's floor never gives way, even over a cave the land happens to have there
                        d.fillOpen(x, floor - 7, z, x, floor - 2, z, "rock");
                    }
                }
            }
        }

        /** Under the trap floor: an open shaft down to the landing cave, sealed round its sides. */
        private void chute(Draw d, int x, int z, int u, int v) {
            boolean shaft = trapFloor(u, v);
            boolean ring = !shaft && u >= from - 1 && u <= to + 1 && Math.abs(v) <= floorHalf + 1;
            if (shaft) {
                d.air(x, landingTop, z, x, floor - 2, z);
            } else if (ring) {
                d.fillOpen(x, landingTop, z, x, floor - 2, z, "rock");
            }
        }

        /** The centre of the trap floor (for the landing below it). */
        int[] centre() {
            return xz((from + to) / 2, 0);
        }
    }

    /** The landing at the bottom of the chute: a low cave with a heap of filthy straw that breaks the fall. */
    static final class LandingPiece extends Piece {
        private final int cx;
        private final int cz;
        private final int y;
        private final int radius;
        private final int height;
        private final int straw;
        private final int strawRadius;
        private final Palette pal;

        LandingPiece(int cx, int cz, int y, int radius, int height, int straw, int strawRadius, Palette pal) {
            super(1);
            this.cx = cx;
            this.cz = cz;
            this.y = y;
            this.radius = radius;
            this.height = height;
            this.straw = straw;
            this.strawRadius = strawRadius;
            this.pal = pal;
            bounds(cx - radius - 2, y - 2, cz - radius - 2, cx + radius + 2, y + height + 2, cz + radius + 2);
            zone("landing");
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            for (int x = Math.max(x0, d.c.minX()); x <= Math.min(x1, d.c.maxX()); x++) {
                for (int z = Math.max(z0, d.c.minZ()); z <= Math.min(z1, d.c.maxZ()); z++) {
                    double r = Math.hypot(x - cx, z - cz) + (Rng.noise(d.seed + 111, x, y, z, 4) - 0.5) * 2;
                    if (r <= radius) {
                        int top = y + (int) Math.round(height * Math.sqrt(Math.max(0.2, 1 - (r / radius) * (r / radius))));
                        d.air(x, y, z, x, top - 1, z);
                        d.set(x, y - 1, z, d.pick("tunnel_floor", x, y - 1, z));
                        d.fillOpen(x, top, z, x, top, z, "rock");
                        int heap = Math.max(Math.abs(x - cx), Math.abs(z - cz)) <= strawRadius
                                ? straw - (Math.max(Math.abs(x - cx), Math.abs(z - cz)) == strawRadius ? 1 : 0) : 0;
                        for (int k = 0; k < heap; k++) {
                            d.role(x, y + k, z, "straw");
                        }
                    } else if (r <= radius + 1.5) {
                        d.fillOpen(x, y - 1, z, x, y + height, z, "rock");
                    }
                }
            }
        }
    }

    /**
     * Gollum's lake: a dark domed cave, most of its floor deep still water round a rocky island, a shore ring, glow
     * lichen in the roof.
     */
    static final class LakePiece extends Piece {
        private final int cx;
        private final int cz;
        private final int water;
        private final int radius;
        private final int height;
        private final int depth;
        private final int island;
        private final Palette pal;

        LakePiece(int cx, int cz, int water, int radius, int height, int depth, int island, Palette pal) {
            super(1);
            this.cx = cx;
            this.cz = cz;
            this.water = water;
            this.radius = radius;
            this.height = height;
            this.depth = depth;
            this.island = island;
            this.pal = pal;
            bounds(cx - radius - 3, water - depth - 2, cz - radius - 3, cx + radius + 3, water + height + 2,
                    cz + radius + 3);
            zone("gollum_lake");
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            for (int x = Math.max(x0, d.c.minX()); x <= Math.min(x1, d.c.maxX()); x++) {
                for (int z = Math.max(z0, d.c.minZ()); z <= Math.min(z1, d.c.maxZ()); z++) {
                    double r = Math.hypot(x - cx, z - cz) * (1 + (Rng.noise(d.seed + 121, x, water, z, 9) - 0.5) * 0.3);
                    if (r > radius + 1.5) {
                        continue;
                    }
                    if (r > radius) {
                        d.fillOpen(x, water - depth - 1, z, x, water + height, z, "rock");
                        continue;
                    }
                    double k = r / radius;
                    int top = water + 1 + (int) Math.round(height * Math.sqrt(Math.max(0, 1 - k * k)));
                    boolean lake = k < 0.74;
                    int bed = water - depth + (int) Math.round((Rng.noise(d.seed + 123, x, 0, z, 6) - 0.5) * 2);
                    if (r <= island + (Rng.noise(d.seed + 125, x, 1, z, 3) - 0.5) * 2) {
                        d.fill(x, bed - 1, z, x, water, z, "island");
                        d.air(x, water + 1, z, x, top - 1, z);
                        if (d.u(x, water, z, 127) < 0.25) {
                            d.role(x, water + 1, z, "island_junk");
                        }
                    } else if (lake) {
                        d.set(x, bed - 1, z, d.pick("lake_floor", x, bed - 1, z));
                        for (int y = bed; y <= water; y++) {
                            d.set(x, y, z, d.pick("water", x, y, z));
                        }
                        d.air(x, water + 1, z, x, top - 1, z);
                    } else {
                        d.set(x, water, z, d.pick("shore", x, water, z));
                        d.fillOpen(x, water - depth - 1, z, x, water - 1, z, "rock");
                        d.air(x, water + 1, z, x, top - 1, z);
                    }
                    d.fillOpen(x, top, z, x, top, z, "rock");
                    if (top > water + 3 && d.u(x, top, z, 129) < 0.05) {
                        d.set(x, top - 1, z, d.pick("glow", x, top - 1, z).with("up", "true"));
                    }
                }
            }
        }
    }

    /** The back door: a small door left ajar in a rock face on the far side of the mountain, a passage behind it. */
    static final class BackDoorPiece extends Piece {
        private final int px;
        private final int pz;
        private final int floor;
        private final int out;
        private final int passage;
        private final Palette pal;

        BackDoorPiece(int px, int pz, int floor, int out, int passage, Palette pal) {
            super(2);
            this.px = px;
            this.pz = pz;
            this.floor = floor;
            this.out = out;
            this.passage = passage;
            this.pal = pal;
            int[] a = xz(-4, -6);
            int[] b = xz(passage + 1, 6);
            bounds(a[0], floor - 6, a[1], b[0], floor + 9, b[1]);
            zone("back_door");
        }

        /** u: inward from the doorway; v: across. */
        int[] xz(int u, int v) {
            int in = (out + 2) % 4;
            boolean ew = in == DelveSpec.E || in == DelveSpec.W;
            return ew ? new int[] {px + DelveSpec.DX[in] * u, pz + v} : new int[] {px + v, pz + DelveSpec.DZ[in] * u};
        }

        @Override
        void build(Draw d) {
            d.palette(pal);
            // a rough face of rock round the doorway
            for (int u = 0; u <= 3; u++) {
                for (int v = -5; v <= 5; v++) {
                    int[] p = xz(u, v);
                    int top = floor + 7 - Math.max(0, Math.abs(v) - 2);
                    d.fillOpen(p[0], floor - 1, p[1], p[0], top, p[1], "rock");
                }
            }
            for (int u = 0; u <= passage; u++) {
                for (int v = -2; v <= 2; v++) {
                    int[] p = xz(u, v);
                    if (Math.abs(v) <= 1 && (u > 0 || v == 0)) {
                        d.air(p[0], floor, p[1], p[0], floor + 2 + (u > 0 ? 1 : 0), p[1]);
                        d.set(p[0], floor - 1, p[1], d.pick("tunnel_floor", p[0], floor - 1, p[1]));
                    } else if (u > 0) {
                        d.fillOpen(p[0], floor - 1, p[1], p[0], floor + 4, p[1], "rock");
                    }
                }
            }
            int[] door = xz(0, 0);
            Mat lower = d.pick("door", door[0], floor, door[1]).with("facing", dir(out)).with("half", "lower")
                    .with("open", "true").with("hinge", "left");
            d.set(door[0], floor, door[1], lower);
            d.set(door[0], floor + 1, door[1], lower.with("half", "upper"));
            for (int v : new int[] {-1, 1}) {
                int[] p = xz(0, v);
                d.role(p[0], floor, p[1], "door_frame");
                d.role(p[0], floor + 1, p[1], "door_frame");
            }
            int[] lintel = xz(0, 0);
            d.role(lintel[0], floor + 2, lintel[1], "door_frame");
            // a ledge outside
            for (int u = -3; u <= -1; u++) {
                for (int v = -2; v <= 2; v++) {
                    int[] p = xz(u, v);
                    d.fillOpen(p[0], floor - 4, p[1], p[0], floor - 1, p[1], "porch_rock");
                    d.air(p[0], floor, p[1], p[0], floor + 3, p[1]);
                }
            }
        }
    }
}
