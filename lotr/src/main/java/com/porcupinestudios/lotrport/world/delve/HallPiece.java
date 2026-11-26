package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * A great hall (the Dwarrowdelf, the Twenty-first Hall, a gate's First Hall): rows of massive pillars on plinths with
 * flared capitals up into a coffered roof (beams between the capitals), an aisle down the long axis kept clear,
 * pilasters on the walls in line with the rows, trim bands, lamps in the pillars and chandeliers in the coffers (as
 * many as the ruin leaves lit). Ruin breaks some pillars off (a stump in a heap of rubble). Doorways get a grand
 * frame. All its numbers are the piece's {@code params}.
 */
final class HallPiece extends Piece {
    private final Node n;
    private final List<int[]> doors;
    private final JsonObject p;
    private final DelveSpec.Style style;
    private final int spacing;
    private final int ph;
    private final int aisle;
    private final int plinth;
    private final int capital;
    private final int band;
    private final double lights;
    private final double chandeliers;

    HallPiece(DelveSpec spec, Node n, List<int[]> doors, JsonObject params) {
        super(2);
        this.n = n;
        this.doors = new ArrayList<>(doors);
        this.p = params;
        this.style = n.level.style();
        spacing = J.i(p, "pillar_spacing");
        ph = J.i(p, "pillar_half");
        aisle = J.i(p, "aisle");
        plinth = J.i(p, "plinth");
        capital = J.i(p, "capital");
        band = J.i(p, "band");
        lights = J.d(p, "lights");
        chandeliers = J.d(p, "chandeliers");
        bounds(n.x0() - 3, n.y - 2, n.z0() - 3, n.x1() + 3, n.y + n.h + 1, n.z1() + 3);
    }

    HallPiece(DelveSpec spec, Node n, List<int[]> doors) {
        this(spec, n, doors, n.set.params());
    }

    void addDoor(int[] door) {
        doors.add(door);
    }

    private boolean longX() {
        return n.hx >= n.hz;
    }

    private Palette palette(Draw d) {
        if (p.has("palette")) {
            return d.spec.palettes.get(p.get("palette").getAsString());
        }
        return style.palette();
    }

    @Override
    void build(Draw d) {
        d.palette(palette(d));
        int y = n.y;
        int top = y + n.h - 1;
        d.air(n.x0(), y, n.z0(), n.x1(), top, n.z1());
        d.shell(n.x0(), y, n.z0(), n.x1(), top, n.z1(), "floor", "wall", "ceiling");
        floor(d);
        walls(d);
        List<int[]> pillars = pillarSpots();
        for (int[] pc : pillars) {
            pillar(d, pc[0], pc[1]);
        }
        beams(d, pillars);
        for (int[] door : doors) {
            RoomPiece.doorway(d, n, door);
            frame(d, door);
        }
        d.rubble(n.x0(), n.z0(), n.x1(), n.z1(), y, 2);
        d.webs(n.x0(), n.z0(), n.x1(), n.z1(), top);
    }

    private void floor(Draw d) {
        int fy = n.y - 1;
        for (int x = Math.max(n.x0(), d.c.minX()); x <= Math.min(n.x1(), d.c.maxX()); x++) {
            for (int z = Math.max(n.z0(), d.c.minZ()); z <= Math.min(n.z1(), d.c.maxZ()); z++) {
                int lat = longX() ? z - n.cz : x - n.cx;
                String role;
                if (Math.abs(lat) == aisle + 1) {
                    role = "floor_trim";
                } else if (Math.abs(lat) <= aisle) {
                    role = Math.floorMod(longX() ? x : z, 4) == 0 ? "floor_trim" : "floor_alt";
                } else {
                    role = Math.floorMod(x + z, 2) == 0 ? "floor" : "floor_alt";
                }
                d.set(x, fy, z, d.pick(role, x, fy, z));
            }
        }
    }

    private void walls(Draw d) {
        int y = n.y;
        int top = y + n.h - 1;
        for (int x = n.x0(); x <= n.x1(); x++) {
            boolean pil = Math.floorMod(x - n.cx, spacing) == 0;
            for (int z : new int[] {n.z0() - 1, n.z1() + 1}) {
                wallColumn(d, x, z, pil, y, top);
            }
        }
        for (int z = n.z0(); z <= n.z1(); z++) {
            boolean pil = Math.floorMod(z - n.cz, spacing) == 0;
            for (int x : new int[] {n.x0() - 1, n.x1() + 1}) {
                wallColumn(d, x, z, pil, y, top);
            }
        }
    }

    private void wallColumn(Draw d, int x, int z, boolean pilaster, int y, int top) {
        if (!d.in(x, z)) {
            return;
        }
        for (int yy = y; yy <= top; yy++) {
            if (pilaster) {
                d.set(x, yy, z, d.pick("pillar", x, yy, z));
            } else if (band > 0 && Math.floorMod(yy - y, band) == band - 1) {
                d.set(x, yy, z, d.pick("trim", x, yy, z));
            }
        }
        if (pilaster && d.u(x, y, z, 37) < lights) {
            d.role(x, y + Math.min(4, n.h / 3), z, "light");
        }
    }

    private List<int[]> pillarSpots() {
        List<int[]> out = new ArrayList<>();
        int reach = ph + 3;
        for (int px = n.cx - ((n.hx - reach) / spacing) * spacing; px <= n.x1() - reach; px += spacing) {
            for (int pz = n.cz - ((n.hz - reach) / spacing) * spacing; pz <= n.z1() - reach; pz += spacing) {
                int lat = longX() ? pz - n.cz : px - n.cx;
                if (Math.abs(lat) <= aisle + ph + 1 || beforeDoor(px, pz)) {
                    continue;
                }
                out.add(new int[] {px, pz});
            }
        }
        return out;
    }

    private boolean beforeDoor(int x, int z) {
        for (int[] door : doors) {
            boolean ew = door[0] == DelveSpec.E || door[0] == DelveSpec.W;
            int hw = door[3] / 2 + ph + 2;
            int depth = ph + 6;
            int edge = n.edgeAt(door[0]);
            if (ew ? Math.abs(z - door[1]) <= hw && Math.abs(x - edge) <= depth
                    : Math.abs(x - door[1]) <= hw && Math.abs(z - edge) <= depth) {
                return true;
            }
        }
        return false;
    }

    private void pillar(Draw d, int px, int pz) {
        int y = n.y;
        int top = y + n.h - 1;
        boolean broken = d.u(px, y, pz, 43) < d.spec.ruin.brokenPillars();
        int stump = y + plinth + Rng.range(d.h(px, y, pz, 47), 2, Math.max(3, n.h / 2));
        int reachCap = ph + capital;
        for (int dx = -reachCap - 3; dx <= reachCap + 3; dx++) {
            for (int dz = -reachCap - 3; dz <= reachCap + 3; dz++) {
                int x = px + dx;
                int z = pz + dz;
                if (!d.in(x, z)) {
                    continue;
                }
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                if (cheb <= ph) {
                    int colTop = broken ? stump : top;
                    for (int yy = y; yy <= colTop; yy++) {
                        d.set(x, yy, z, d.pick("pillar", x, yy, z));
                    }
                    if (!broken && cheb == ph && (dx == 0 || dz == 0) && d.u(x, y, z, 53) < lights) {
                        d.role(x, y + n.h / 3, z, "light");
                    }
                }
                if (cheb <= ph + 1) {
                    d.fill(x, y, z, x, y + plinth - 1, z, "trim");
                }
                if (!broken && cheb > ph && cheb <= reachCap) {
                    int under = top - capital + (cheb - ph);
                    int inward = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? DelveSpec.W : DelveSpec.E)
                            : (dz > 0 ? DelveSpec.N : DelveSpec.S);
                    d.set(x, under, z, d.pick("capital", x, under, z).with("facing", Draw.facing(inward))
                            .with("half", "top"));
                    for (int yy = under + 1; yy <= top; yy++) {
                        d.set(x, yy, z, d.pick("trim", x, yy, z));
                    }
                }
                if (broken && cheb <= ph + 3) {
                    int heap = Math.max(0, (ph + 4 - cheb) - Rng.range(d.h(x, y, z, 59), 0, 2));
                    for (int yy = y; yy < y + heap; yy++) {
                        d.set(x, yy, z, d.pick("rubble", x, yy, z));
                    }
                    if (heap > 0) {
                        d.set(x, y + heap, z, d.pick("rubble_top", x, y + heap, z));
                    }
                }
            }
        }
    }

    /** Beams along the pillar rows under the roof, and chandeliers in the coffers between them. */
    private void beams(Draw d, List<int[]> pillars) {
        int top = n.y + n.h - 1;
        for (int x = Math.max(n.x0(), d.c.minX()); x <= Math.min(n.x1(), d.c.maxX()); x++) {
            for (int z = Math.max(n.z0(), d.c.minZ()); z <= Math.min(n.z1(), d.c.maxZ()); z++) {
                boolean rowX = Math.floorMod(z - n.cz, spacing) == 0;
                boolean rowZ = Math.floorMod(x - n.cx, spacing) == 0;
                if (rowX || rowZ) {
                    d.set(x, top, z, d.pick("trim", x, top, z));
                } else if (Math.floorMod(x - n.cx, spacing) == spacing / 2 && Math.floorMod(z - n.cz, spacing) == spacing / 2
                        && d.u(x, top, z, 67) < chandeliers) {
                    d.role(x, top - 1, z, "chandelier");
                    d.role(x, top, z, "chain");
                }
            }
        }
    }

    /** A grand doorway frame: thick jambs and a two-high lintel. */
    private void frame(Draw d, int[] door) {
        int side = door[0];
        int lane = door[1];
        int y = door[2];
        int hw = door[3] / 2;
        int hh = door[4];
        boolean ew = side == DelveSpec.E || side == DelveSpec.W;
        int wallAt = ew ? (side == DelveSpec.E ? n.x1() + 1 : n.x0() - 1) : (side == DelveSpec.S ? n.z1() + 1 : n.z0() - 1);
        int inner = ew ? (side == DelveSpec.E ? wallAt - 1 : wallAt + 1) : (side == DelveSpec.S ? wallAt - 1 : wallAt + 1);
        for (int v = -hw - 2; v <= hw + 2; v++) {
            if (Math.abs(v) <= hw) {
                continue;
            }
            int x = ew ? inner : lane + v;
            int z = ew ? lane + v : inner;
            for (int yy = y; yy < y + hh + 2; yy++) {
                d.role(x, yy, z, "pillar");
            }
        }
        for (int v = -hw - 2; v <= hw + 2; v++) {
            int x = ew ? inner : lane + v;
            int z = ew ? lane + v : inner;
            d.role(x, y + hh, z, "trim");
            d.role(x, y + hh + 1, z, "carved");
        }
    }
}
