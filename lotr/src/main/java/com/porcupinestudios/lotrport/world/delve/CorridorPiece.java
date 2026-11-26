package com.porcupinestudios.lotrport.world.delve;

import java.util.List;

/**
 * A corridor between two nodes: straight legs along the grid (a dog-leg where the two doorways' lanes differ). A
 * dwarven corridor is walled, floored and roofed in its palette, chamfered under the roof, with a pillar rib every
 * {@code rib} blocks and now and then a lamp in a rib; a hewn one (the deeps) is rough rock, wider and lower in places,
 * only sealed where it meets air or water, with stone supports at the ribs. A change of floor between its ends is
 * stairs in the middle of its longest leg. A collapsed corridor (only ever one leading to a dead end) is choked with
 * rubble halfway.
 */
final class CorridorPiece extends Piece {
    private final int[][] legs;
    private final int yA;
    private final int yB;
    private final DelveSpec.Style style;
    private final boolean collapsed;
    private final boolean route;
    private final int half;
    private final int h;
    private final int slopeLeg;

    CorridorPiece(List<int[]> legs, int yA, int yB, DelveSpec.Style style, boolean collapsed, boolean route) {
        super(0);
        this.legs = legs.toArray(new int[0][]);
        this.yA = yA;
        this.yB = yB;
        this.style = style;
        this.collapsed = collapsed;
        this.route = route;
        this.half = style.width() / 2;
        this.h = style.height();
        int longest = 0;
        for (int i = 1; i < this.legs.length; i++) {
            if (length(this.legs[i]) > length(this.legs[longest])) {
                longest = i;
            }
        }
        this.slopeLeg = longest;
        int pad = half + 2 + (int) Math.ceil(style.roughness());
        int bx0 = Integer.MAX_VALUE;
        int bz0 = Integer.MAX_VALUE;
        int bx1 = Integer.MIN_VALUE;
        int bz1 = Integer.MIN_VALUE;
        for (int[] l : this.legs) {
            bx0 = Math.min(bx0, Math.min(l[0], l[2]) - pad);
            bx1 = Math.max(bx1, Math.max(l[0], l[2]) + pad);
            bz0 = Math.min(bz0, Math.min(l[1], l[3]) - pad);
            bz1 = Math.max(bz1, Math.max(l[1], l[3]) + pad);
        }
        bounds(bx0, Math.min(yA, yB) - 2, bz0, bx1, Math.max(yA, yB) + h + 2 + (int) Math.ceil(style.roughness()), bz1);
    }

    private static int length(int[] l) {
        return Math.abs(l[2] - l[0]) + Math.abs(l[3] - l[1]);
    }

    private static boolean alongX(int[] l) {
        return l[1] == l[3];
    }

    /** Distance along the leg from its start (negative before it, past its length after). */
    private static int along(int[] l, int x, int z) {
        return alongX(l) ? (x - l[0]) * Integer.signum(l[2] - l[0] == 0 ? 1 : l[2] - l[0])
                : (z - l[1]) * Integer.signum(l[3] - l[1] == 0 ? 1 : l[3] - l[1]);
    }

    private static int across(int[] l, int x, int z) {
        return alongX(l) ? z - l[1] : x - l[0];
    }

    private int floorAt(int leg, int t) {
        if (leg < slopeLeg) {
            return yA;
        }
        if (leg > slopeLeg) {
            return yB;
        }
        int dy = yB - yA;
        int n = Math.abs(dy);
        if (n == 0) {
            return yA;
        }
        int len = length(legs[leg]);
        int t0 = (len - n) / 2;
        int k = Math.max(0, Math.min(n, t - t0));
        return yA + Integer.signum(dy) * k;
    }

    /** Half width of the interior at a point of a leg: wider and narrower in a hewn corridor. */
    private int halfAt(Draw d, int x, int z) {
        if (!style.hewn() || style.roughness() <= 0) {
            return half;
        }
        double n = Rng.noise(d.seed + 41, x, 0, z, 7);
        return half + (int) Math.round((n - 0.5) * 2 * style.roughness());
    }

    private int heightAt(Draw d, int x, int z) {
        if (!style.hewn() || style.roughness() <= 0) {
            return h;
        }
        double n = Rng.noise(d.seed + 43, x, 0, z, 6);
        return h + (int) Math.round((n - 0.5) * 2 * style.roughness());
    }

    /** The leg whose interior holds the column, or -1. */
    private int legAt(Draw d, int x, int z, int widen) {
        for (int i = 0; i < legs.length; i++) {
            int[] l = legs[i];
            int t = along(l, x, z);
            int len = length(l);
            int hw = halfAt(d, x, z) + widen;
            if (t >= -hw && t <= len + hw && Math.abs(across(l, x, z)) <= hw) {
                return i;
            }
        }
        return -1;
    }

    @Override
    void build(Draw d) {
        d.palette(style.palette());
        int ax = Math.max(x0, d.c.minX());
        int bx = Math.min(x1, d.c.maxX());
        int az = Math.max(z0, d.c.minZ());
        int bz = Math.min(z1, d.c.maxZ());
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                int leg = legAt(d, x, z, 0);
                if (leg >= 0) {
                    interior(d, x, z, leg);
                    continue;
                }
                int shellLeg = legAt(d, x, z, 1);
                if (shellLeg >= 0) {
                    wall(d, x, z, shellLeg);
                }
            }
        }
    }

    private void interior(Draw d, int x, int z, int leg) {
        int[] l = legs[leg];
        int t = along(l, x, z);
        int f = floorAt(leg, t);
        int top = f + heightAt(d, x, z);
        d.air(x, f, z, x, top - 1, z);
        int lat = across(l, x, z);
        boolean rib = style.rib() > 0 && Math.floorMod(t, style.rib()) == 0;
        if (style.hewn()) {
            d.set(x, f - 1, z, d.pick("floor", x, f - 1, z));
            d.fillOpen(x, top, z, x, top, z, "rock");
            if (rib && Math.abs(lat) == halfAt(d, x, z)) {
                d.fill(x, f, z, x, top - 1, z, "support");
            } else if (rib) {
                d.set(x, top - 1, z, d.pick("support", x, top - 1, z));
            }
        } else {
            d.set(x, f - 1, z, d.pick(rib ? "floor_trim" : "floor", x, f - 1, z));
            d.set(x, top, z, d.pick(rib ? "trim" : "ceiling", x, top, z));
            if (Math.abs(lat) == half) {
                int side = alongX(l) ? (lat > 0 ? DelveSpec.S : DelveSpec.N) : (lat > 0 ? DelveSpec.E : DelveSpec.W);
                d.set(x, top - 1, z, d.pick("chamfer", x, top - 1, z).with("facing", Draw.facing(side))
                        .with("half", "top"));
            }
        }
        // stairs where the floor steps up to the next column along the leg
        if (leg == slopeLeg && yA != yB) {
            int fwd = alongX(l) ? (l[2] >= l[0] ? DelveSpec.E : DelveSpec.W) : (l[3] >= l[1] ? DelveSpec.S : DelveSpec.N);
            if (floorAt(leg, t + 1) == f + 1) {
                d.set(x, f, z, d.pick("stairs", x, f, z).with("facing", Draw.facing(fwd)).with("half", "bottom"));
            } else if (floorAt(leg, t - 1) == f + 1) {
                d.set(x, f, z, d.pick("stairs", x, f, z).with("facing", Draw.facing((fwd + 2) % 4))
                        .with("half", "bottom"));
            }
        }
        if (collapsed && leg == slopeLeg) {
            int len = length(l);
            int mid = len / 2;
            int reach = Math.max(2, len / 4);
            int dist = Math.abs(t - mid);
            if (dist < reach) {
                int fillTo = f + (int) Math.round((h + 1) * (1 - (double) dist / reach)) - 1;
                for (int y = f; y <= Math.min(fillTo, top - 1); y++) {
                    d.set(x, y, z, d.pick("rubble", x, y, z));
                }
                return;
            }
        }
        if (yA == yB || leg != slopeLeg) {
            d.rubbleAt(x, z, f, route ? 1 : 2);
        }
        if (Math.abs(lat) == half && d.u(x, top, z, 13) < d.spec.ruin.webs() * 0.06) {
            d.set(x, top - 2, z, d.pick("web", x, top - 2, z));
        }
    }

    private void wall(Draw d, int x, int z, int leg) {
        int[] l = legs[leg];
        int t = along(l, x, z);
        int f = floorAt(leg, Math.max(0, Math.min(length(l), t)));
        int lo = Math.min(f, Math.min(floorAt(leg, t - 1), floorAt(leg, t + 1))) - 1;
        int hi = Math.max(f, Math.max(floorAt(leg, t - 1), floorAt(leg, t + 1))) + heightAt(d, x, z);
        if (style.hewn()) {
            d.fillOpen(x, lo, z, x, hi, z, "rock");
            return;
        }
        boolean rib = style.rib() > 0 && Math.floorMod(t, style.rib()) == 0 && t > 0 && t < length(l);
        for (int y = lo; y <= hi; y++) {
            if (rib) {
                d.set(x, y, z, d.pick(y == hi || y == lo ? "trim" : "pillar", x, y, z));
            } else {
                d.wall(x, y, z, "wall");
            }
        }
        if (rib && d.u(x, f, z, 17) < style.lights()) {
            d.set(x, f + h / 2, z, d.pick("light", x, f + h / 2, z));
        }
    }
}
