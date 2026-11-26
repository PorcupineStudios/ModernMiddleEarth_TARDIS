package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * A stairwell through several levels of one cell, or the Endless Stair: a square shaft with a solid newel, a helix of
 * steps (full blocks and slabs, never more than half a block per step) round it, and at every level a gallery along
 * the walls that the corridors open onto, railed except where the helix meets it (the east side). Each flight turns a
 * whole number of times, so the helix meets every gallery at the same place; a flight climbs at most
 * {@code max_rise} a turn. The Endless Stair goes on past its last level up through the mountain to Durin's Tower,
 * a chamber just under the summit with a passage out to the mountainside.
 */
final class ShaftPiece extends Piece {
    private final List<Node> landings;
    private final Map<Integer, List<int[]>> doors;
    private final DelveSpec.Style style;
    private final int cx;
    private final int cz;
    private final int half;
    private final int newel;
    private final int rail;
    private final int gap;
    private final int maxRise;
    private final int[] floors;
    private final int topFloor;
    private final int ceiling;
    private final int exitSide;
    private final int exitLength;
    private final int exitHalf;
    private final int exitHeight;

    ShaftPiece(DelveSpec spec, List<Node> landings, Map<Integer, List<int[]>> doors, DelveSpec.SetPiece sp, int tower,
               int exitPacked) {
        super(1);
        this.landings = landings;
        this.doors = doors;
        Node first = landings.get(0);
        this.style = first.level.style();
        this.cx = first.cx;
        this.cz = first.cz;
        if (sp != null) {
            JsonObject p = sp.params();
            half = J.i(p, "half");
            newel = J.i(p, "newel");
            rail = J.i(p, "rail");
            gap = J.i(p, "gap");
            maxRise = J.i(p, "max_rise");
            exitHalf = J.i(p, "exit_half");
            exitHeight = J.i(p, "exit_height");
            int towerHeight = J.i(p, "tower_height");
            Node last = landings.get(landings.size() - 1);
            topFloor = Math.max(tower, last.y + last.h + 4);
            ceiling = topFloor + towerHeight;
            exitSide = exitPacked & 3;
            exitLength = (exitPacked >> 2) + 6;
        } else {
            DelveSpec.Stairwell s = spec.stairwell;
            half = s.half();
            newel = s.newel();
            rail = s.rail();
            gap = s.gap();
            maxRise = s.maxRise();
            Node last = landings.get(landings.size() - 1);
            topFloor = last.y;
            ceiling = last.y + last.h;
            exitSide = -1;
            exitLength = 0;
            exitHalf = 0;
            exitHeight = 0;
        }
        floors = new int[landings.size() + (sp != null ? 1 : 0)];
        for (int i = 0; i < landings.size(); i++) {
            floors[i] = landings.get(i).y;
        }
        if (sp != null) {
            floors[floors.length - 1] = topFloor;
        }
        bounds(cx - half - 1, floors[0] - 1, cz - half - 1, cx + half + 1, ceiling + 1, cz + half + 1);
        if (exitSide >= 0) {
            int ex = cx + DelveSpec.DX[exitSide] * (half + 1 + exitLength);
            int ez = cz + DelveSpec.DZ[exitSide] * (half + 1 + exitLength);
            grow(ex - exitHalf - 1, topFloor - 1, ez - exitHalf - 1, ex + exitHalf + 1, topFloor + exitHeight + 1,
                    ez + exitHalf + 1);
        }
    }

    @Override
    void build(Draw d) {
        d.palette(style.palette());
        int bottom = floors[0];
        for (int dx = -half - 1; dx <= half + 1; dx++) {
            for (int dz = -half - 1; dz <= half + 1; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (!d.in(x, z)) {
                    continue;
                }
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                if (cheb == half + 1) {
                    for (int y = bottom - 1; y <= ceiling; y++) {
                        d.wall(x, y, z, "wall");
                    }
                    continue;
                }
                d.role(x, bottom - 1, z, "floor");
                d.role(x, ceiling, z, "ceiling");
                if (cheb <= newel) {
                    d.fill(x, bottom, z, x, ceiling - 1, z, "pillar");
                    continue;
                }
                d.air(x, bottom, z, x, ceiling - 1, z);
                if (cheb < rail) {
                    helix(d, x, z, dx, dz);
                } else {
                    for (int f : floors) {
                        d.role(x, f - 1, z, "floor");
                        if (cheb == rail && !(dx > 0 && Math.abs(dz) <= gap)) {
                            d.role(x, f, z, "rail");
                        }
                    }
                }
            }
        }
        for (Node n : landings) {
            for (int[] door : doors.getOrDefault(n.id, List.of())) {
                RoomPiece.doorway(d, n, door);
            }
            // a lamp in each corner of the gallery
            for (int k = 0; k < 4; k++) {
                int x = cx + ((k & 1) == 0 ? -half - 1 : half + 1);
                int z = cz + ((k & 2) == 0 ? -half : half);
                if (d.u(x, n.y, z, 31) < style.lights()) {
                    d.role(x, n.y + 2, z, "light");
                }
            }
        }
        if (exitSide >= 0) {
            exit(d);
        }
    }

    private void helix(Draw d, int x, int z, int dx, int dz) {
        double theta = Math.atan2(dz, dx);
        if (theta < 0) {
            theta += 2 * Math.PI;
        }
        double t = theta / (2 * Math.PI);
        for (int i = 0; i + 1 < floors.length; i++) {
            int rise = floors[i + 1] - floors[i];
            if (rise <= 0) {
                continue;
            }
            int turns = Math.max(1, (int) Math.ceil(rise / (double) maxRise));
            for (int j = 0; j < turns; j++) {
                step(d, x, z, floors[i] + rise * (t + j) / turns);
            }
        }
    }

    private static void step(Draw d, int x, int z, double s) {
        double q = Math.round(s * 2) / 2.0;
        int fl = (int) Math.floor(q);
        if (q == fl) {
            d.role(x, fl - 1, z, "floor");
        } else {
            d.role(x, fl, z, "slab", "type", "bottom");
        }
    }

    /** Durin's Tower's door: a passage from the top chamber out through the summit's flank. */
    private void exit(Draw d) {
        int s = exitSide;
        boolean ew = s == DelveSpec.E || s == DelveSpec.W;
        for (int k = half + 1; k <= half + 1 + exitLength; k++) {
            for (int v = -exitHalf - 1; v <= exitHalf + 1; v++) {
                int x = cx + DelveSpec.DX[s] * k + (ew ? 0 : v);
                int z = cz + DelveSpec.DZ[s] * k + (ew ? v : 0);
                if (Math.abs(v) <= exitHalf) {
                    d.air(x, topFloor, z, x, topFloor + exitHeight - 1, z);
                    d.role(x, topFloor - 1, z, k == half + 1 ? "floor_trim" : "floor");
                    d.fillOpen(x, topFloor + exitHeight, z, x, topFloor + exitHeight, z, "rock");
                } else {
                    d.fillOpen(x, topFloor - 1, z, x, topFloor + exitHeight, z, "rock");
                }
            }
        }
    }
}
