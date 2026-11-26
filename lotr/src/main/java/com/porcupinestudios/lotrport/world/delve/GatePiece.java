package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * A gate of the delve at the mountain front: a sheer face of rock built out of the slope round a carved doorway
 * (pillared jambs, a lintel) holding the doors (role {@code door}: the West-gate's ithildin doors) or the broken
 * remains of a great gate; a stone landing outside and, where the ground falls away, steps down to it; inside, a
 * passage with broad shallow steps (half a block a step) up or down to its level, and a pillared hall
 * ({@link HallPiece}) in the gate's innermost cell where the maze begins. Numbers are the piece's {@code params}.
 */
final class GatePiece extends Piece {
    private final Node n;
    private final JsonObject p;
    private final HallPiece hall;
    private final int doorX;
    private final int doorY;
    private final int doorZ;
    private final int facing;
    private final int in;
    private final int doorHalf;
    private final int doorHeight;
    private final int facadeHalf;
    private final int facadeHeight;
    private final int facadeDepth;
    private final int landing;
    private final int passHalf;
    private final int passHeight;
    private final int entry;
    private final int run;
    private final int stepsHalf;
    private final boolean broken;
    private final int length;
    /** Floor heights of the steps down outside, from the landing outward. */
    private final int[] steps;

    GatePiece(DelveSpec spec, Node n, List<int[]> doors, int[] gate, Ground ground) {
        super(2);
        this.n = n;
        this.p = n.set.params();
        doorX = gate[0];
        doorY = gate[1];
        doorZ = gate[2];
        facing = gate[3];
        in = (facing + 2) % 4;
        doorHalf = J.i(p, "door_half");
        doorHeight = J.i(p, "door_height");
        facadeHalf = J.i(p, "facade_half");
        facadeHeight = J.i(p, "facade_height");
        facadeDepth = J.i(p, "facade_depth");
        landing = J.i(p, "landing");
        passHalf = J.i(p, "passage_half");
        passHeight = J.i(p, "passage_height");
        entry = J.i(p, "entry");
        run = J.i(p, "run");
        stepsHalf = J.i(p, "steps_half");
        broken = J.b(p, "broken");
        int edge = n.edgeAt(facing);
        length = Math.abs((in == DelveSpec.E || in == DelveSpec.W ? edge - doorX : edge - doorZ)) + 1;
        List<int[]> hallDoors = new ArrayList<>(doors);
        hallDoors.add(new int[] {facing, in == DelveSpec.E || in == DelveSpec.W ? doorZ : doorX, n.y, passHalf * 2 + 1,
                passHeight});
        hall = new HallPiece(spec, n, hallDoors, J.o(p, "hall"));
        int maxSteps = J.i(p, "max_steps");
        List<Integer> st = new ArrayList<>();
        int f = doorY;
        for (int i = 1; i <= maxSteps; i++) {
            int u = -landing - i;
            int g = (int) Math.floor(ground.height(xAt(u, 0), zAt(u, 0))) + 1;
            if (f <= g) {
                break;
            }
            f = Math.max(g, f - 1);
            st.add(f);
        }
        steps = st.stream().mapToInt(Integer::intValue).toArray();
        int reach = Math.max(facadeHalf, stepsHalf) + 2;
        int out = landing + steps.length + 2;
        int[] a = {xAt(-out, -reach), zAt(-out, -reach)};
        int[] b = {xAt(Math.max(length + 1, facadeDepth + facadeHeight + 1), reach),
                zAt(Math.max(length + 1, facadeDepth + facadeHeight + 1), reach)};
        int lowest = doorY - 2 - 12;
        for (int s : steps) {
            lowest = Math.min(lowest, s - 13);
        }
        bounds(a[0], lowest, a[1], b[0], Math.max(doorY + facadeHeight, n.y + passHeight + 4) + 1, b[1]);
        grow(hall.x0, hall.y0, hall.z0, hall.x1, hall.y1, hall.z1);
    }

    /** World x of a point {@code u} blocks inward from the doorway and {@code v} to its side. */
    private int xAt(int u, int v) {
        return in == DelveSpec.E || in == DelveSpec.W ? doorX + DelveSpec.DX[in] * u : doorX + v;
    }

    private int zAt(int u, int v) {
        return in == DelveSpec.E || in == DelveSpec.W ? doorZ + v : doorZ + DelveSpec.DZ[in] * u;
    }

    /** Floor surface of the passage {@code u} blocks in (broad steps half a block each, after the entry). */
    private double floorAt(int u) {
        int rise = n.y - doorY;
        if (u < entry || rise == 0) {
            return doorY;
        }
        double s = (u - entry) / (double) run * 0.5;
        return doorY + Math.signum(rise) * Math.min(Math.abs(rise), s);
    }

    @Override
    void build(Draw d) {
        d.palette(p.has("palette") ? d.spec.palettes.get(p.get("palette").getAsString()) : n.level.style().palette());
        facade(d);
        passage(d);
        hall.build(d);
        d.palette(p.has("palette") ? d.spec.palettes.get(p.get("palette").getAsString()) : n.level.style().palette());
        doorway(d);
        outside(d);
    }

    /**
     * The rock face round the doorway: full height for {@code facade_depth} blocks in, then falling back one block per
     * block until it sinks into the mountainside (a buttress, not a free-standing slab); lower towards its sides.
     */
    private void facade(Draw d) {
        for (int u = 0; u <= facadeDepth + facadeHeight; u++) {
            for (int v = -facadeHalf; v <= facadeHalf; v++) {
                int taper = Math.max(0, Math.abs(v) - facadeHalf / 2) * 2;
                int top = doorY + facadeHeight - taper - Math.max(0, u - facadeDepth);
                if (top >= doorY) {
                    d.fillOpen(xAt(u, v), doorY - 1, zAt(u, v), xAt(u, v), top, zAt(u, v), "rock");
                }
            }
        }
    }

    private void passage(Draw d) {
        for (int u = 1; u <= length; u++) {
            double s = floorAt(u);
            double q = Math.round(s * 2) / 2.0;
            int fl = (int) Math.floor(q);
            boolean halfStep = q != fl;
            int roof = (int) Math.ceil(q) + passHeight;
            for (int v = -passHalf - 1; v <= passHalf + 1; v++) {
                int x = xAt(u, v);
                int z = zAt(u, v);
                if (Math.abs(v) <= passHalf) {
                    d.air(x, fl, z, x, roof - 1, z);
                    d.role(x, fl - 1, z, Math.abs(v) == passHalf ? "floor_trim" : "floor");
                    if (halfStep) {
                        d.role(x, fl, z, "slab", "type", "bottom");
                    }
                    d.role(x, roof, z, u % 6 == 0 ? "trim" : "ceiling");
                    d.fillOpen(x, roof + 1, z, x, roof + 3, z, "rock");
                    d.rubbleAt(x, z, halfStep ? fl + 1 : fl, 1);
                } else {
                    boolean rib = u % 6 == 0;
                    for (int y = fl - 1; y <= roof; y++) {
                        if (rib) {
                            d.role(x, y, z, "pillar");
                        } else {
                            d.wall(x, y, z, "wall");
                        }
                    }
                    if (rib && d.u(x, fl, z, 79) < n.level.style().lights()) {
                        d.role(x, fl + 3, z, "light");
                    }
                    d.fillOpen(x, roof + 1, z, x, roof + 3, z, "rock");
                }
            }
        }
    }

    /** The carved doorway in the face (u = 0): doors (or broken gates), jambs and a lintel. */
    private void doorway(Draw d) {
        String out = Draw.facing(facing);
        for (int v = -doorHalf - 2; v <= doorHalf + 2; v++) {
            int x = xAt(0, v);
            int z = zAt(0, v);
            for (int y = doorY - 1; y <= doorY + doorHeight + 1; y++) {
                if (Math.abs(v) <= doorHalf && y >= doorY && y < doorY + doorHeight) {
                    if (broken) {
                        boolean piece = y < doorY + 2 && d.u(x, y, z, 83) < 0.35;
                        d.set(x, y, z, piece ? d.pick("door", x, y, z).with("open", "true") : Mat.AIR);
                    } else {
                        d.set(x, y, z, d.pick("door", x, y, z).with("facing", out));
                    }
                } else if (y == doorY - 1) {
                    d.role(x, y, z, "floor_trim");
                } else if (Math.abs(v) > doorHalf) {
                    d.role(x, y, z, y >= doorY + doorHeight ? "trim" : "pillar");
                } else {
                    d.role(x, y, z, y == doorY + doorHeight ? "trim" : "carved");
                }
            }
        }
    }

    private void outside(Draw d) {
        for (int u = -landing; u <= -1; u++) {
            for (int v = -doorHalf - 3; v <= doorHalf + 3; v++) {
                int x = xAt(u, v);
                int z = zAt(u, v);
                d.role(x, doorY - 1, z, "floor");
                d.fillOpen(x, doorY - 13, z, x, doorY - 2, z, "rock");
                d.air(x, doorY, z, x, doorY + doorHeight + 2, z);
            }
        }
        String up = Draw.facing(in);
        for (int i = 0; i < steps.length; i++) {
            int u = -landing - 1 - i;
            int f = steps[i];
            for (int v = -stepsHalf; v <= stepsHalf; v++) {
                int x = xAt(u, v);
                int z = zAt(u, v);
                d.role(x, f, z, "stairs", "facing", up, "half", "bottom");
                d.fillOpen(x, f - 12, z, x, f - 1, z, "rock");
                d.air(x, f + 1, z, x, f + 4, z);
            }
        }
    }
}
