package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonObject;
import java.util.List;

/**
 * The Bridge of Khazad-dûm: a tall hall split by a chasm that runs across the way (north to south when its doors are
 * east and west), down to a floor of fire far below and on out past the hall's walls as a narrowing fissure. One
 * slender stone arch without a kerb or rail spans it, thin at the crown and thickening to its springings. Every
 * number is the piece's {@code params}; the chasm's depth is the set piece's {@code below}.
 */
final class ChasmPiece extends Piece {
    private final Node n;
    private final List<int[]> doors;
    private final DelveSpec.Style style;
    private final int bottom;
    private final int chasmHalf;
    private final double wobble;
    private final int bridgeHalf;
    private final double arch;
    private final int lava;
    private final int fissure;
    private final boolean alongZ;

    ChasmPiece(DelveSpec spec, Node n, List<int[]> doors, int bottom) {
        super(2);
        this.n = n;
        this.doors = doors;
        this.style = n.level.style();
        this.bottom = bottom;
        JsonObject p = n.set.params();
        chasmHalf = J.i(p, "chasm_half");
        wobble = J.d(p, "wobble");
        bridgeHalf = J.i(p, "bridge_half");
        arch = J.d(p, "arch");
        lava = J.i(p, "lava");
        fissure = J.i(p, "fissure");
        int doorSides = n.set.doors();
        alongZ = (doorSides & ((1 << DelveSpec.E) | (1 << DelveSpec.W))) != 0;
        int fx = alongZ ? 0 : fissure;
        int fz = alongZ ? fissure : 0;
        bounds(n.x0() - 2 - fx, bottom - 2, n.z0() - 2 - fz, n.x1() + 2 + fx, n.y + n.h + 1, n.z1() + 2 + fz);
    }

    /** Half width of the chasm at a point along it (with its wobble), shrinking to nothing out in the fissure. */
    private double widthAt(Draw d, int along, int y) {
        double w = chasmHalf + (Rng.noise(d.seed + 71, along, y, 0, 9) - 0.5) * 2 * wobble;
        int inside = alongZ ? Math.max(n.z0() - along, along - n.z1()) : Math.max(n.x0() - along, along - n.x1());
        if (inside > 0) {
            w *= Math.max(0, 1 - inside / (double) fissure);
        }
        return w;
    }

    @Override
    void build(Draw d) {
        d.palette(style.palette());
        int y = n.y;
        int top = y + n.h - 1;
        d.air(n.x0(), y, n.z0(), n.x1(), top, n.z1());
        d.shell(n.x0(), y, n.z0(), n.x1(), top, n.z1(), "floor", "wall", "ceiling");
        int ax = Math.max(x0, d.c.minX());
        int bx = Math.min(x1, d.c.maxX());
        int az = Math.max(z0, d.c.minZ());
        int bz = Math.min(z1, d.c.maxZ());
        int mid = alongZ ? n.cx : n.cz;
        int bridgeAt = alongZ ? n.cz : n.cx;
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                int along = alongZ ? z : x;
                int lat = (alongZ ? x : z) - mid;
                boolean inHall = x >= n.x0() && x <= n.x1() && z >= n.z0() && z <= n.z1();
                int roof = inHall ? top : y + n.h / 2;
                for (int yy = bottom - 1; yy <= roof; yy++) {
                    double w = widthAt(d, along, yy);
                    if (w <= 0.4) {
                        continue;
                    }
                    double dist = Math.abs(lat) - w;
                    if (dist <= 0) {
                        if (yy < bottom) {
                            d.role(x, yy, z, "chasm_wall");
                        } else if (yy < bottom + lava) {
                            d.role(x, yy, z, "lava");
                        } else {
                            d.set(x, yy, z, Mat.AIR);
                        }
                    } else if (dist <= 2 && yy < y - 1) {
                        if (!d.solid(x, yy, z) || d.u(x, yy, z, 73) < 0.35) {
                            d.role(x, yy, z, "chasm_wall");
                        }
                    }
                }
                // the bridge
                int cross = (alongZ ? z : x) - bridgeAt;
                double w = widthAt(d, along, y);
                if (Math.abs(cross) <= bridgeHalf && Math.abs(lat) <= w + 1) {
                    double t = Math.min(1, Math.abs(lat) / Math.max(1, w));
                    int thick = 1 + (int) Math.round(arch * t * t * t);
                    for (int k = 0; k < thick; k++) {
                        d.role(x, y - 1 - k, z, "bridge");
                    }
                }
            }
        }
        for (int[] door : doors) {
            RoomPiece.doorway(d, n, door);
        }
        d.webs(n.x0(), n.z0(), n.x1(), n.z1(), top);
    }
}
