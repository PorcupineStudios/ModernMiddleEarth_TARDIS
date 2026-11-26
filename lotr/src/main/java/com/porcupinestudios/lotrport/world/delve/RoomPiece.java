package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A junction or a room (and the one-cell set pieces, Mazarbul and the guardroom). Its kind's {@code params} say what
 * it holds, each part optional:
 * <ul>
 * <li>{@code shape}: {@code built} (walled in its palette, the default), {@code mine} (hewn rock with ore veins and
 * supports) or {@code cavern} (a natural cave, rounded and uneven, sealed only where it meets air or water);</li>
 * <li>{@code pillars} {spacing, half}: a grid of pillars clear of the doorways;</li>
 * <li>{@code band}: a trim band round the walls at that height;</li>
 * <li>{@code tomb} {half_x, half_z, height}: a tomb in the middle, its lid in role {@code tomb_top};</li>
 * <li>{@code well} {radius, depth, water}: a round well through the floor;</li>
 * <li>{@code forge} {count: [min, max], flue}: forges along a wall without a doorway, a lava trough along the opposite
 * one, a chimney flue up into the rock;</li>
 * <li>{@code lamps}: chance of a lamp (role {@code light}) in the middle of each wall, {@code chandelier}: of one
 * hanging from the middle of the roof;</li>
 * </ul>
 * and the room's variant its dressing (roles placed by a wall, in corners, on the floor, at the centre or under the
 * roof). Doorways are framed with pillars and a lintel. Then the ruin: rubble and cobwebs.
 */
final class RoomPiece extends Piece {
    private final DelveSpec spec;
    private final Node n;
    private final List<int[]> doors;
    private final JsonObject params;
    private final DelveSpec.Variant variant;
    private final DelveSpec.Style style;
    private final String shape;

    RoomPiece(DelveSpec spec, Node n, List<int[]> doors, JsonObject params, DelveSpec.Variant variant,
              DelveSpec.Style style) {
        super(1);
        this.spec = spec;
        this.n = n;
        this.doors = doors;
        this.params = params == null ? new JsonObject() : params;
        this.variant = variant;
        this.style = style;
        this.shape = this.params.has("shape") ? this.params.get("shape").getAsString() : "built";
        int below = 1;
        if (this.params.has("well")) {
            below = J.i(this.params.getAsJsonObject("well"), "depth") + 2;
        }
        int above = 1;
        if (this.params.has("forge")) {
            above = J.i(this.params.getAsJsonObject("forge"), "flue") + 1;
        }
        int pad = shape.equals("built") ? 1 : 3;
        bounds(n.x0() - pad, n.y - below, n.z0() - pad, n.x1() + pad, n.y + n.h + above + 2, n.z1() + pad);
    }

    private Palette palette() {
        if (params.has("palette")) {
            Palette p = spec.palettes.get(params.get("palette").getAsString());
            if (p == null) {
                throw new IllegalArgumentException("delve " + spec.name + ": no palette " + params.get("palette"));
            }
            return p;
        }
        return style.palette();
    }

    @Override
    void build(Draw d) {
        d.palette(palette());
        switch (shape) {
            case "mine", "cavern" -> rough(d);
            default -> built(d);
        }
        if (variant != null) {
            dress(d);
        }
        if (!shape.equals("built")) {
            return;
        }
        d.rubble(n.x0(), n.z0(), n.x1(), n.z1(), n.y, 3);
        d.webs(n.x0(), n.z0(), n.x1(), n.z1(), n.y + n.h - 1);
    }

    private void built(Draw d) {
        int y = n.y;
        int top = y + n.h - 1;
        d.air(n.x0(), y, n.z0(), n.x1(), top, n.z1());
        d.shell(n.x0(), y, n.z0(), n.x1(), top, n.z1(), "floor", "wall", "ceiling");
        if (params.has("band")) {
            int by = y + params.get("band").getAsInt();
            for (int x = n.x0() - 1; x <= n.x1() + 1; x++) {
                d.role(x, by, n.z0() - 1, "trim");
                d.role(x, by, n.z1() + 1, "trim");
            }
            for (int z = n.z0(); z <= n.z1(); z++) {
                d.role(n.x0() - 1, by, z, "trim");
                d.role(n.x1() + 1, by, z, "trim");
            }
        }
        for (int[] door : doors) {
            doorway(d, n, door);
        }
        if (params.has("pillars")) {
            pillars(d, params.getAsJsonObject("pillars"));
        }
        if (params.has("tomb")) {
            tomb(d, params.getAsJsonObject("tomb"));
        }
        if (params.has("well")) {
            well(d, params.getAsJsonObject("well"));
        }
        if (params.has("forge")) {
            forge(d, params.getAsJsonObject("forge"));
        }
        if (params.has("lamps")) {
            double chance = params.get("lamps").getAsDouble();
            int ly = y + Math.max(1, n.h / 2);
            int[][] spots = {{n.cx, n.z0() - 1}, {n.cx, n.z1() + 1}, {n.x0() - 1, n.cz}, {n.x1() + 1, n.cz}};
            for (int[] s : spots) {
                if (d.u(s[0], ly, s[1], 23) < chance && !inDoor(s[0], s[1])) {
                    d.role(s[0], ly, s[1], "light");
                }
            }
        }
        if (params.has("chandelier") && d.u(n.cx, top, n.cz, 29) < params.get("chandelier").getAsDouble()) {
            d.role(n.cx, top, n.cz, "chandelier");
        }
    }

    /** Whether a wall block is part of a doorway (or its frame). */
    private boolean inDoor(int x, int z) {
        for (int[] door : doors) {
            int side = door[0];
            int lane = door[1];
            int hw = door[3] / 2 + 1;
            boolean ew = side == DelveSpec.E || side == DelveSpec.W;
            int wallAt = ew ? (side == DelveSpec.E ? n.x1() + 1 : n.x0() - 1) : (side == DelveSpec.S ? n.z1() + 1 : n.z0() - 1);
            if (ew ? (x == wallAt && Math.abs(z - lane) <= hw) : (z == wallAt && Math.abs(x - lane) <= hw)) {
                return true;
            }
        }
        return false;
    }

    /** Opens a doorway in a node's wall for a corridor ({side, lane, floor y, width, height}) and frames it. */
    static void doorway(Draw d, Node n, int[] door) {
        int side = door[0];
        int lane = door[1];
        int y = door[2];
        int hw = door[3] / 2;
        int hh = door[4];
        boolean ew = side == DelveSpec.E || side == DelveSpec.W;
        int wallAt = ew ? (side == DelveSpec.E ? n.x1() + 1 : n.x0() - 1) : (side == DelveSpec.S ? n.z1() + 1 : n.z0() - 1);
        for (int v = -hw - 1; v <= hw + 1; v++) {
            int x = ew ? wallAt : lane + v;
            int z = ew ? lane + v : wallAt;
            for (int yy = y; yy < y + hh; yy++) {
                if (Math.abs(v) <= hw) {
                    d.set(x, yy, z, Mat.AIR);
                } else {
                    d.role(x, yy, z, "pillar");
                }
            }
            d.role(x, y + hh, z, "trim");
            if (Math.abs(v) <= hw) {
                d.role(x, y - 1, z, "floor_trim");
            }
        }
    }

    private void pillars(Draw d, JsonObject p) {
        int spacing = J.i(p, "spacing");
        int ph = J.i(p, "half");
        for (int px = n.cx - (n.hx / spacing) * spacing; px <= n.x1() - ph - 2; px += spacing) {
            for (int pz = n.cz - (n.hz / spacing) * spacing; pz <= n.z1() - ph - 2; pz += spacing) {
                if (px - ph - 2 < n.x0() || pz - ph - 2 < n.z0() || nearLane(px, pz, ph + 1)) {
                    continue;
                }
                d.fill(px - ph, n.y, pz - ph, px + ph, n.y + n.h - 1, pz + ph, "pillar");
                d.fill(px - ph, n.y, pz - ph, px + ph, n.y, pz + ph, "trim");
            }
        }
    }

    /** Whether a point lies in front of a doorway's lane (kept clear so the way through stays open). */
    private boolean nearLane(int x, int z, int clear) {
        for (int[] door : doors) {
            boolean ew = door[0] == DelveSpec.E || door[0] == DelveSpec.W;
            int hw = door[3] / 2 + clear;
            if (ew ? Math.abs(z - door[1]) <= hw : Math.abs(x - door[1]) <= hw) {
                return true;
            }
        }
        return false;
    }

    private void tomb(Draw d, JsonObject t) {
        int hx = J.i(t, "half_x");
        int hz = J.i(t, "half_z");
        int th = J.i(t, "height");
        d.fill(n.cx - hx, n.y, n.cz - hz, n.cx + hx, n.y + th - 2, n.cz + hz, "tomb");
        d.fill(n.cx - hx, n.y + th - 1, n.cz - hz, n.cx + hx, n.y + th - 1, n.cz + hz, "tomb_top");
        d.fill(n.cx - hx - 1, n.y - 1, n.cz - hz - 1, n.cx + hx + 1, n.y - 1, n.cz + hz + 1, "floor_trim");
    }

    private void well(Draw d, JsonObject w) {
        int r = J.i(w, "radius");
        int depth = J.i(w, "depth");
        int water = J.i(w, "water");
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                int x = n.cx + dx;
                int z = n.cz + dz;
                if (dist <= r) {
                    d.air(x, n.y - depth, z, x, n.y - 1, z);
                    d.fill(x, n.y - depth, z, x, n.y - depth + water - 1, z, "water");
                    d.role(x, n.y - depth - 1, z, "rock");
                } else if (dist <= r + 1.2) {
                    d.fill(x, n.y - depth, z, x, n.y - 1, z, "wall");
                    d.role(x, n.y, z, "trim");
                }
            }
        }
    }

    private void forge(Draw d, JsonObject f) {
        // forges along the first wall without a doorway, trough along the opposite one
        int side = -1;
        for (int s = 0; s < 4; s++) {
            boolean used = false;
            for (int[] door : doors) {
                if (door[0] == s) {
                    used = true;
                }
            }
            if (!used) {
                side = s;
                break;
            }
        }
        if (side < 0) {
            side = DelveSpec.N;
        }
        int[] range = J.range(f, "count");
        int count = Rng.range(n.hash ^ 0x5F0, range[0], range[1]);
        int flue = J.i(f, "flue");
        boolean ew = side == DelveSpec.E || side == DelveSpec.W;
        int len = ew ? n.hz * 2 + 1 : n.hx * 2 + 1;
        int at = ew ? (side == DelveSpec.E ? n.x1() : n.x0()) : (side == DelveSpec.S ? n.z1() : n.z0());
        int opp = ew ? (side == DelveSpec.E ? n.x0() : n.x1()) : (side == DelveSpec.S ? n.z0() : n.z1());
        String inward = Draw.facing((side + 2) % 4);
        for (int i = 0; i < count; i++) {
            int v = -len / 2 + (i + 1) * len / (count + 1);
            int x = ew ? at : n.cx + v;
            int z = ew ? n.cz + v : at;
            d.role(x, n.y, z, "forge", "facing", inward);
            d.role(x, n.y + 1, z, "trim");
            // the flue: a shaft from over the hood up through the ceiling into the rock
            d.air(x, n.y + 2, z, x, n.y + n.h + flue, z);
            int ax = ew ? x + (side == DelveSpec.E ? -1 : 1) : x;
            int az = ew ? z : z + (side == DelveSpec.S ? -1 : 1);
            if (i % 2 == 0) {
                d.role(ax, n.y, az, "anvil", "facing", Draw.facing(ew ? DelveSpec.N : DelveSpec.E));
            }
        }
        for (int v = -len / 2 + 1; v <= len / 2 - 1; v++) {
            int x = ew ? opp : n.cx + v;
            int z = ew ? n.cz + v : opp;
            if (inDoorInside(x, z)) {
                continue;
            }
            d.role(x, n.y - 1, z, "lava");
            d.role(x, n.y - 2, z, "rock");
        }
    }

    /** Whether an interior block next to a wall stands in front of a doorway. */
    private boolean inDoorInside(int x, int z) {
        for (int[] door : doors) {
            boolean ew = door[0] == DelveSpec.E || door[0] == DelveSpec.W;
            int hw = door[3] / 2 + 1;
            int edge = n.edgeAt(door[0]);
            if (ew ? (x == edge && Math.abs(z - door[1]) <= hw) : (z == edge && Math.abs(x - door[1]) <= hw)) {
                return true;
            }
        }
        return false;
    }

    /** Mines and caverns: a rounded, uneven hollow; mines get ore veins in their walls and supports. */
    private void rough(Draw d) {
        boolean mine = shape.equals("mine");
        double rough = params.has("roughness") ? params.get("roughness").getAsDouble() : style.roughness();
        double veins = params.has("veins") ? params.get("veins").getAsDouble() : 0;
        int ax = Math.max(x0, d.c.minX());
        int bx = Math.min(x1, d.c.maxX());
        int az = Math.max(z0, d.c.minZ());
        int bz = Math.min(z1, d.c.maxZ());
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                double nx = (x - n.cx) / (n.hx + 0.5);
                double nz = (z - n.cz) / (n.hz + 0.5);
                double r2 = nx * nx + nz * nz;
                double wob = (Rng.noise(d.seed + 51, x, n.y, z, 6) - 0.5) * 2 * rough / Math.max(4, Math.min(n.hx, n.hz));
                boolean inside = r2 <= Math.pow(1 + wob, 2) || inDoorInside(x, z) || doorLane(x, z);
                int floor = n.y + (mine ? 0 : (int) Math.round((Rng.noise(d.seed + 53, x, 0, z, 5) - 0.5) * rough));
                int roof = n.y + (int) Math.round(n.h * (mine ? 1 : Math.sqrt(Math.max(0.15, 1 - r2 * 0.7))))
                        + (int) Math.round((Rng.noise(d.seed + 57, x, 1, z, 4) - 0.5) * 2 * rough);
                if (inside) {
                    d.air(x, floor, z, x, Math.max(floor + 2, roof) - 1, z);
                    d.set(x, floor - 1, z, d.pick("floor", x, floor - 1, z));
                    d.fillOpen(x, Math.max(floor + 2, roof), z, x, Math.max(floor + 2, roof), z, "rock");
                    if (mine && Math.floorMod(x - n.cx, 6) == 0 && Math.abs(nz) > 0.6 && r2 < 1) {
                        d.fill(x, floor, z, x, roof - 1, z, "support");
                    }
                } else if (r2 <= Math.pow(1.35 + wob, 2)) {
                    for (int y = n.y - 1; y <= n.y + n.h + 1; y++) {
                        if (veins > 0 && d.u(x, y, z, 61) < veins
                                && Rng.noise(d.seed + 63, x, y, z, 3) > 0.62) {
                            d.role(x, y, z, "vein");
                        } else if (!d.solid(x, y, z)) {
                            d.role(x, y, z, "rock");
                        }
                    }
                }
            }
        }
    }

    /** Whether a column lies on the straight way from a doorway to the room's middle (kept open in caves). */
    private boolean doorLane(int x, int z) {
        int hw = style.width() / 2;
        for (int[] door : doors) {
            boolean ew = door[0] == DelveSpec.E || door[0] == DelveSpec.W;
            if (ew) {
                int lo = Math.min(n.cx, n.edgeAt(door[0]));
                int hi = Math.max(n.cx, n.edgeAt(door[0]) + (door[0] == DelveSpec.E ? 2 : -2));
                if (Math.abs(z - door[1]) <= hw && x >= Math.min(lo, hi) && x <= Math.max(lo, hi)) {
                    return true;
                }
            } else {
                int lo = Math.min(n.cz, n.edgeAt(door[0]));
                int hi = Math.max(n.cz, n.edgeAt(door[0]) + (door[0] == DelveSpec.S ? 2 : -2));
                if (Math.abs(x - door[1]) <= hw && z >= Math.min(lo, hi) && z <= Math.max(lo, hi)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void dress(Draw d) {
        Set<Long> used = new HashSet<>();
        int salt = 0;
        for (DelveSpec.Dressing dr : variant.dressing()) {
            int count = Rng.range(Rng.hash(n.hash, 201, salt, 0), dr.min(), dr.max());
            for (int i = 0; i < count; i++) {
                long h = Rng.hash(n.hash, 202, salt, i);
                int[] spot = spot(dr.at(), h);
                salt++;
                if (spot == null || !used.add(((long) spot[0] << 40) ^ ((long) spot[1] << 20) ^ spot[2])) {
                    continue;
                }
                Mat m = d.pick(dr.role(), spot[0], spot[1], spot[2]);
                if (spot[3] >= 0) {
                    m = m.with("facing", Draw.facing(spot[3]));
                }
                d.set(spot[0], spot[1], spot[2], m);
            }
        }
    }

    /** {x, y, z, facing or -1} for a dressing spot, or null when none fits. */
    private int[] spot(String at, long h) {
        int y = n.y;
        switch (at) {
            case "center" -> {
                return new int[] {n.cx, y, n.cz, -1};
            }
            case "corner" -> {
                int k = Rng.range(h, 0, 3);
                int x = (k & 1) == 0 ? n.x0() : n.x1();
                int z = (k & 2) == 0 ? n.z0() : n.z1();
                return new int[] {x, y, z, (k & 2) == 0 ? DelveSpec.S : DelveSpec.N};
            }
            case "ceiling" -> {
                int x = Rng.range(h, n.x0() + 1, n.x1() - 1);
                int z = Rng.range(Rng.mix(h), n.z0() + 1, n.z1() - 1);
                return new int[] {x, y + n.h - 1, z, -1};
            }
            case "floor" -> {
                if (n.hx < 3 || n.hz < 3) {
                    return null;
                }
                int x = Rng.range(h, n.x0() + 2, n.x1() - 2);
                int z = Rng.range(Rng.mix(h), n.z0() + 2, n.z1() - 2);
                return nearLane(x, z, 0) ? null : new int[] {x, y, z, -1};
            }
            default -> { // "wall": by a wall, facing into the room, not in front of a doorway
                int side = Rng.range(h, 0, 3);
                boolean ew = side == DelveSpec.E || side == DelveSpec.W;
                int v = ew ? Rng.range(Rng.mix(h), n.z0(), n.z1()) : Rng.range(Rng.mix(h), n.x0(), n.x1());
                int x = ew ? n.edgeAt(side) : v;
                int z = ew ? v : n.edgeAt(side);
                if (inDoorInside(x, z) || nearLane(x, z, 1)) {
                    return null;
                }
                return new int[] {x, y, z, (side + 2) % 4};
            }
        }
    }
}
