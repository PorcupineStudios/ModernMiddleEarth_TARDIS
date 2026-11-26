package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Plans a goblin town (layout {@code goblin}) around an anchor, from its spec's sections, each optional:
 * <ul>
 * <li>{@code town}: a rift under the mountain (as deep as the ground over it allows, {@code cover} under it), its pool
 * and falls, scaffold levels (platforms against both walls, walkways between them, bridges across, ladders between
 * levels, huts, braziers, hanging cages);</li>
 * <li>{@code throne}: the Great Goblin's deck on a rock spire, bridged to both walls;</li>
 * <li>{@code porch}: the Front Porch, a cave in a steep slope found near {@code near} (or the anchor); armed with a
 * chute ({@code chute}) to a landing on a town level below, then a tunnel into the town's end; or a harmless decoy;</li>
 * <li>{@code lake}: Gollum's lake beyond the rift's far end at the pool's level, a flooded tunnel to it (the deep
 * falls drop into the pool);</li>
 * <li>{@code back_door}: a door in a slope on the far side, a tunnel up to it from the lake (or the pool).</li>
 * </ul>
 * Returns null where a scattered copy doesn't fit (no room under the ground, no slope for its porch).
 */
public final class GoblinPlanner {
    private final DelveSpec spec;
    private final JsonObject j;
    private final Ground ground;
    private final long seed;
    private final SplittableRandom rnd;
    private final int ax;
    private final int az;
    private final boolean scattered;
    private final Palette pal;
    private final List<Piece> pieces = new ArrayList<>();
    private final Map<String, int[]> doorways = new HashMap<>();
    private final StringBuilder log = new StringBuilder();
    private RiftShape rift;
    private int poolTop;
    private int[] levels = new int[0];

    private record Plat(int level, int y, int sign, int u0, int u1, int inner, int depth, Set<Integer> openings) {}

    private final List<Plat> plats = new ArrayList<>();

    private GoblinPlanner(DelveSpec spec, long worldSeed, Ground ground, int ax, int az) {
        this.spec = spec;
        this.j = spec.json;
        this.ground = ground;
        this.ax = ax;
        this.az = az;
        this.scattered = spec.scatter != null;
        this.seed = Rng.mix(worldSeed ^ Rng.mix(spec.salt) ^ Rng.hash(0, ax, az, 7));
        this.rnd = new SplittableRandom(seed);
        this.pal = spec.palettes.get("goblin");
        if (pal == null) {
            throw new IllegalArgumentException("delve " + spec.name + ": a goblin layout needs a \"goblin\" palette");
        }
    }

    /** The plan of a goblin town around (ax, az), or null if it doesn't fit there. */
    public static DelvePlan plan(DelveSpec spec, long worldSeed, Ground ground, int ax, int az) {
        return new GoblinPlanner(spec, worldSeed, ground, ax, az).run();
    }

    private int range(JsonObject o, String k) {
        int[] r = J.range(o, k);
        return r[0] + (r[1] > r[0] ? rnd.nextInt(r[1] - r[0] + 1) : 0);
    }

    private DelvePlan run() {
        log.append(String.format("goblin %s at (%d, %d)%n", spec.name, ax, az));
        if (j.has("town") && !town(j.getAsJsonObject("town"))) {
            return null;
        }
        if (j.has("throne") && rift != null) {
            throne(j.getAsJsonObject("throne"));
        }
        if (rift != null) {
            scaffold(j.getAsJsonObject("town"));
        }
        if (j.has("porch") && !porch(j.getAsJsonObject("porch")) && scattered) {
            return null;
        }
        int[] from = null;
        if (j.has("lake") && rift != null) {
            from = lake(j.getAsJsonObject("lake"));
        }
        if (j.has("back_door")) {
            JsonObject bd = j.getAsJsonObject("back_door");
            if (!bd.has("chance") || rnd.nextDouble() < bd.get("chance").getAsDouble()) {
                if (from == null && rift != null) {
                    from = new int[] {rift.x(rift.length - 4, 0), poolTop, rift.z(rift.length - 4, 0)};
                }
                if (from != null) {
                    backDoor(bd, from);
                }
            }
        }
        if (pieces.isEmpty()) {
            return null;
        }
        log.append(String.format("  %d pieces%n", pieces.size()));
        return new DelvePlan(spec, seed, 0, 0, List.of(), List.of(), List.copyOf(pieces), List.of(), true,
                log.toString(), doorways);
    }

    // ---- the rift ----

    private boolean town(JsonObject t) {
        int length = range(t, "length");
        double hw = range(t, "half_width");
        int height = range(t, "height");
        String axis = J.s(t, "axis");
        boolean alongX = axis.equals("x") || (axis.equals("random") && rnd.nextBoolean());
        int start = (alongX ? ax : az) - length / 2;
        int line = alongX ? az : ax;
        double groundMin = Double.MAX_VALUE;
        for (int u = 0; u <= length; u += 8) {
            for (int v = (int) -hw; v <= hw; v += 8) {
                int x = alongX ? start + u : line + v;
                int z = alongX ? line + v : start + u;
                groundMin = Math.min(groundMin, ground.height(x, z));
            }
        }
        int top = (int) Math.min(groundMin - J.i(t, "cover"), J.i(t, "max_top"));
        int bottom = top - height;
        if (bottom < J.i(t, "min_bottom")) {
            bottom = J.i(t, "min_bottom");
            if (top - bottom < J.i(t, "min_height")) {
                log.append(String.format("  no room under the ground (ground %.0f)%n", groundMin));
                return false;
            }
        }
        rift = new RiftShape(alongX, start, line, length, bottom, top, hw, J.d(t, "roughness"), seed + 1);
        poolTop = bottom + J.i(t, "pool_depth");
        JsonObject lv = J.o(t, "levels");
        int gap = J.i(lv, "gap");
        int y0 = bottom + (int) Math.round(J.d(lv, "from") * (top - bottom));
        int y1 = bottom + (int) Math.round(J.d(lv, "to") * (top - bottom));
        List<Integer> ys = new ArrayList<>();
        for (int y = y0; y <= y1; y += gap) {
            ys.add(y);
        }
        levels = ys.stream().mapToInt(Integer::intValue).toArray();
        int falls = J.i(t, "falls");
        int[][] fall = new int[falls][];
        for (int i = 0; i < falls; i++) {
            fall[i] = new int[] {(int) (length * (0.15 + 0.7 * rnd.nextDouble())), rnd.nextBoolean() ? 1 : -1,
                poolTop + 20 + rnd.nextInt(Math.max(1, top - poolTop - 35))};
        }
        pieces.add(new GoblinPieces.RiftPiece(rift, poolTop, fall, pal).zone("town"));
        log.append(String.format("  rift along %s, %d long, half %.0f, y %d..%d (ground %.0f), pool to %d, %d levels%n",
                alongX ? "x" : "z", length, hw, bottom, top, groundMin, poolTop, levels.length));
        // platforms on both walls of every level
        JsonObject p = J.o(t, "platforms");
        int endMargin = J.i(p, "end_margin");
        for (int k = 0; k < levels.length; k++) {
            int y = levels[k];
            for (int sign : new int[] {-1, 1}) {
                int u = endMargin + rnd.nextInt(Math.max(1, J.range(p, "spacing")[0]));
                while (u < length - endMargin) {
                    int len = range(p, "length");
                    int depth = range(p, "depth");
                    addPlatform(k, y, sign, u, u + len - 1, depth);
                    u += len + range(p, "spacing");
                }
            }
        }
        townView(length);
        return true;
    }

    /**
     * Doorway "town" for {@code /lotr_delve goto}: on the lowest level's platform nearest the cavern's middle, a step
     * in from its rail, looking across the cavern (its 5th value is the side to face).
     */
    private void townView(int length) {
        Plat best = null;
        for (Plat pl : plats) {
            if (pl.level() == 0 && (best == null
                    || Math.abs((pl.u0() + pl.u1()) / 2 - length / 2) < Math.abs((best.u0() + best.u1()) / 2 - length / 2))) {
                best = pl;
            }
        }
        if (best == null) {
            return;
        }
        int u = (best.u0() + best.u1()) / 2 + 1;
        int v = best.sign() * (best.inner() + 2);
        // v runs along z when the rift runs along x, else along x; face back towards v = 0
        int look = rift.alongX ? (best.sign() > 0 ? DelveSpec.N : DelveSpec.S) : (best.sign() > 0 ? DelveSpec.W : DelveSpec.E);
        doorways.put("town", new int[] {rift.x(u, v), best.y(), rift.z(u, v), -1, look});
    }

    /** A platform if the wall there leaves room for it; its inner edge where the rock would leave {@code depth}. */
    private Plat addPlatform(int k, int y, int sign, int u0, int u1, int depth) {
        int uc = (u0 + u1) / 2;
        double w = rift.halfAt(uc, y);
        int inner = (int) Math.floor(w * (1 - rift.roughness / 2)) - depth;
        if (w < depth + 3 || inner < 2 || u0 < 2 || u1 > rift.length - 2) {
            return null;
        }
        Plat pl = new Plat(k, y, sign, u0, u1, inner, depth, new HashSet<>());
        plats.add(pl);
        return pl;
    }

    private void scaffold(JsonObject t) {
        JsonObject br = J.o(t, "bridges");
        JsonObject hut = J.o(t, "huts");
        int maxPost = J.i(t, "max_post");
        // bridges across each level
        for (int k = 0; k < levels.length; k++) {
            List<Plat[]> pairs = new ArrayList<>();
            for (Plat l : plats) {
                for (Plat r : plats) {
                    if (l.level == k && r.level == k && l.sign < 0 && r.sign > 0
                            && Math.min(l.u1, r.u1) - Math.max(l.u0, r.u0) >= 3) {
                        pairs.add(new Plat[] {l, r});
                    }
                }
            }
            shuffle(pairs);
            int made = 0;
            Set<Plat> used = new HashSet<>();
            for (Plat[] pr : pairs) {
                if (used.contains(pr[0]) || used.contains(pr[1])) {
                    continue;
                }
                if (made >= J.i(br, "min_per_level") && rnd.nextDouble() >= J.d(br, "chance")) {
                    continue;
                }
                int lo = Math.max(pr[0].u0, pr[1].u0) + 1;
                int hi = Math.min(pr[0].u1, pr[1].u1) - 1;
                int ub = (lo + hi) / 2;
                int span = pr[0].inner + pr[1].inner;
                double sag = Math.min(J.d(br, "max_sag"), span * J.d(br, "sag"));
                boolean rope = rnd.nextDouble() < J.d(br, "rope_share");
                pieces.add(new GoblinPieces.BridgePiece(rift, levels[k], ub, pr[0].inner, pr[1].inner, 1,
                        rope ? sag : 0, rope, pal));
                for (int w = -1; w <= 1; w++) {
                    pr[0].openings.add(ub + w);
                    pr[1].openings.add(ub + w);
                }
                used.add(pr[0]);
                used.add(pr[1]);
                made++;
            }
        }
        // ladders between levels, both sides
        int ladders = J.i(t, "ladders_per_level");
        for (int k = 1; k < levels.length; k++) {
            int made = 0;
            List<Plat[]> pairs = new ArrayList<>();
            for (Plat up : plats) {
                for (Plat lo : plats) {
                    if (up.level == k && lo.level == k - 1 && up.sign == lo.sign
                            && Math.min(up.u1, lo.u1) - Math.max(up.u0, lo.u0) >= 0) {
                        pairs.add(new Plat[] {up, lo});
                    }
                }
            }
            shuffle(pairs);
            for (Plat[] pr : pairs) {
                if (made >= ladders) {
                    break;
                }
                int u = (Math.max(pr[0].u0, pr[1].u0) + Math.min(pr[0].u1, pr[1].u1)) / 2;
                int a = Math.max(pr[0].inner, pr[1].inner) + 1;
                boolean clear = true;
                for (int y = pr[1].y; y < pr[0].y - 1 && clear; y++) {
                    clear = rift.inside(rift.x(u, pr[0].sign * a), y, rift.z(u, pr[0].sign * a))
                            && rift.inside(rift.x(u, pr[0].sign * (a + 1)), y, rift.z(u, pr[0].sign * (a + 1)));
                }
                if (!clear || pr[0].openings.contains(u) || pr[1].openings.contains(u)) {
                    continue;
                }
                pieces.add(new GoblinPieces.LadderPiece(rift, pr[0].sign, u, a, pr[1].y, pr[0].y, pal));
                made++;
            }
        }
        // walkways along each wall
        int walkMax = J.i(t, "walkway_max");
        List<Plat> sorted = new ArrayList<>(plats);
        sorted.sort((p, q) -> p.level != q.level ? Integer.compare(p.level, q.level)
                : p.sign != q.sign ? Integer.compare(p.sign, q.sign) : Integer.compare(p.u0, q.u0));
        for (int i = 0; i + 1 < sorted.size(); i++) {
            Plat p = sorted.get(i);
            Plat q = sorted.get(i + 1);
            int gap = q.u0 - p.u1 - 1;
            if (p.level == q.level && p.sign == q.sign && gap >= 1 && gap <= walkMax
                    && rnd.nextDouble() < J.d(t, "walkway_chance")) {
                pieces.add(new GoblinPieces.WalkwayPiece(rift, p.y, p.sign, p.u1 + 1, q.u0 - 1,
                        J.i(t, "walkway_width"), pal));
            }
        }
        // the platforms themselves (before their ladders' hatches: order)
        for (Plat p : plats) {
            boolean h = p.depth >= J.i(hut, "min_depth") && p.u1 - p.u0 + 1 >= J.i(hut, "min_length")
                    && rnd.nextDouble() < J.d(hut, "chance");
            pieces.add(new GoblinPieces.PlatformPiece(rift, p.y, p.sign, p.u0, p.u1, p.inner, h,
                    rnd.nextDouble() < J.d(t, "braziers"), maxPost, p.openings, pal));
        }
        // cages hanging from the roof
        for (int i = 0; i < J.i(t, "cages"); i++) {
            int u = (int) (rift.length * (0.1 + 0.8 * rnd.nextDouble()));
            int y = levels.length == 0 ? poolTop + 20 : levels[rnd.nextInt(levels.length)] + 3 + rnd.nextInt(4);
            double w = rift.halfAt(u, y);
            int v = (int) ((rnd.nextDouble() - 0.5) * w);
            int x = rift.x(u, v);
            int z = rift.z(u, v);
            int topY = y;
            while (topY < rift.top && rift.inside(x, topY + 1, z)) {
                topY++;
            }
            if (topY - y <= J.i(t, "max_chain")) {
                pieces.add(new GoblinPieces.CagePiece(x, z, y, topY, pal));
            }
        }
        log.append(String.format("  %d platforms%n", plats.size()));
    }

    private <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int k = rnd.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(k));
            list.set(k, tmp);
        }
    }

    private void throne(JsonObject th) {
        if (levels.length == 0) {
            return;
        }
        int k = (int) Math.round(J.d(th, "level") * (levels.length - 1));
        int y = levels[k];
        int uc = (int) Math.round(J.d(th, "at") * rift.length);
        int hl = J.i(th, "half_length");
        double w = rift.halfAt(uc, y) * (1 - rift.roughness / 2);
        int hw = (int) Math.min(J.i(th, "half_width"), w - 6);
        if (hw < 4) {
            log.append("  no room for the throne\n");
            return;
        }
        int bh = J.i(th, "bridge_half");
        pieces.add(new GoblinPieces.ThronePiece(rift, y, uc, hl, hw, J.i(th, "spire_radius"), bh, pal));
        // ledges on both walls facing it, bridged to its sides; keep other platforms of its level clear of it
        plats.removeIf(p -> p.level == k && p.u1 >= uc - hl - 3 && p.u0 <= uc + hl + 3);
        for (int sign : new int[] {-1, 1}) {
            Plat ledge = addPlatform(k, y, sign, uc - bh - 3, uc + bh + 3, 5);
            if (ledge != null) {
                for (int u = uc - bh; u <= uc + bh; u++) {
                    ledge.openings.add(u);
                }
                pieces.add(sign < 0
                        ? new GoblinPieces.BridgePiece(rift, y, uc, ledge.inner, -hw, bh, 0, false, pal)
                        : new GoblinPieces.BridgePiece(rift, y, uc, -hw, ledge.inner, bh, 0, false, pal));
            }
        }
        doorways.put("throne", new int[] {rift.x(uc, 0), y, rift.z(uc, 0), -1});
        log.append(String.format("  throne on level %d (y %d) at u %d%n", k, y, uc));
    }

    // ---- the Front Porch ----

    /** A spot near (cx, cz) on a slope rising {@code rise} within {@code look} blocks: {x, z, groundY, inward side}. */
    private int[] slope(int cx, int cz, int minDist, int search, int look, int rise, double gMin, double gMax) {
        int start = rnd.nextInt(16);
        for (int dist = minDist; dist <= search; dist += 6) {
            int steps = Math.max(8, dist / 6);
            for (int i = 0; i < steps; i++) {
                double a = 2 * Math.PI * (i + start / 16.0) / steps;
                int x = cx + (int) Math.round(Math.cos(a) * dist);
                int z = cz + (int) Math.round(Math.sin(a) * dist);
                double g = ground.height(x, z);
                if (g < gMin || g > gMax) {
                    continue;
                }
                for (int side = 0; side < 4; side++) {
                    double behind = ground.height(x + DelveSpec.DX[side] * look, z + DelveSpec.DZ[side] * look);
                    int ls = (side + 1) % 4;
                    double l1 = ground.height(x + DelveSpec.DX[ls] * 4, z + DelveSpec.DZ[ls] * 4);
                    double l2 = ground.height(x - DelveSpec.DX[ls] * 4, z - DelveSpec.DZ[ls] * 4);
                    double front = ground.height(x - DelveSpec.DX[side] * 4, z - DelveSpec.DZ[side] * 4);
                    if (behind - g >= rise && Math.abs(l1 - g) < 4 && Math.abs(l2 - g) < 4 && front <= g + 1) {
                        return new int[] {x, z, (int) Math.floor(g), side};
                    }
                }
            }
        }
        return null;
    }

    private boolean porch(JsonObject p) {
        int cx = ax;
        int cz = az;
        if (p.has("near")) {
            cx = J.ints(J.a(p, "near"))[0];
            cz = J.ints(J.a(p, "near"))[1];
        }
        int[] g = J.range(p, "ground");
        int[] site = slope(cx, cz, J.i(p, "min_distance"), J.i(p, "search"), J.i(p, "look"), J.i(p, "rise"), g[0], g[1]);
        if (site == null) {
            log.append("  no slope for the porch\n");
            return false;
        }
        int floor = site[2] + 1;
        int in = site[3];
        boolean armed = J.b(p, "armed");
        int landingY = Integer.MIN_VALUE;
        if (armed && J.b(p, "chute") && rift != null) {
            for (int y : levels) {
                if (y <= floor - J.i(p, "min_drop")) {
                    landingY = y;
                }
            }
            if (landingY == Integer.MIN_VALUE) {
                log.append("  the porch is below every level of the town\n");
                return false;
            }
        }
        GoblinPieces.PorchPiece porch = new GoblinPieces.PorchPiece(site[0], site[1], floor, in, armed,
                landingY == Integer.MIN_VALUE ? Integer.MIN_VALUE : landingY + J.i(p, "landing_height"),
                J.i(p, "width"), J.i(p, "height"), J.i(p, "depth"), J.i(p, "floor_from"), J.i(p, "floor_to"),
                J.i(p, "floor_half"), pal);
        pieces.add(porch);
        doorways.put("front_porch", new int[] {site[0], floor, site[1], (in + 2) % 4});
        log.append(String.format("  porch at (%d, %d, %d) facing %s, %s%n", site[0], floor, site[1],
                DelveSpec.SIDES[(in + 2) % 4], armed ? "armed" : "a decoy"));
        if (landingY != Integer.MIN_VALUE) {
            int[] c = porch.centre();
            pieces.add(new GoblinPieces.LandingPiece(c[0], c[1], landingY, J.i(p, "landing_radius"),
                    J.i(p, "landing_height"), J.i(p, "straw"), J.i(p, "floor_half") + 1, pal));
            doorways.put("landing", new int[] {c[0], landingY, c[1], -1});
            entry(c[0], c[1], landingY, J.o(j, "tunnel"));
        }
        return true;
    }

    /** A gallery across the rift's nearer end at the landing's level, and a tunnel from the landing to it. */
    private void entry(int lx, int lz, int y, JsonObject tun) {
        double lu = rift.u(lx, lz);
        boolean nearStart = lu < rift.length / 2.0;
        int ue = nearStart ? 12 : rift.length - 12;
        int max = (int) Math.ceil(rift.halfWidth * (1 + rift.roughness)) + 3;
        int left = rift.wallFrom(ue, y, -1, 0, max);
        int right = rift.wallFrom(ue, y, 1, 0, max);
        pieces.add(new GoblinPieces.BridgePiece(rift, y, ue, left, right, 2, 0, false, pal));
        int outU = nearStart ? -16 : rift.length + 16;
        List<double[]> path = new ArrayList<>();
        path.add(new double[] {lx + 0.5, y, lz + 0.5});
        double[] end = {rift.x(outU, 0) + 0.5, y, rift.z(outU, 0) + 0.5};
        wiggle(path, path.get(0), end, J.d(tun, "wiggle"));
        path.add(new double[] {rift.x(ue, 0) + 0.5, y, rift.z(ue, 0) + 0.5});
        pieces.add(new GoblinPieces.TunnelPiece(path, J.i(tun, "half_width"), J.i(tun, "height"),
                J.d(tun, "roughness"), Integer.MIN_VALUE, pal).zone("tunnels"));
    }

    /** Adds points from a towards b (b included), bending a little side to side. */
    private void wiggle(List<double[]> path, double[] a, double[] b, double amount) {
        double dx = b[0] - a[0];
        double dz = b[2] - a[2];
        double len = Math.hypot(dx, dz);
        int n = Math.max(1, (int) (len / 40));
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            double off = (rnd.nextDouble() - 0.5) * 2 * amount * len / n;
            path.add(new double[] {a[0] + dx * t - dz / len * off, a[1] + (b[1] - a[1]) * t,
                a[2] + dz * t + dx / len * off});
        }
        path.add(b);
    }

    // ---- Gollum's lake and the back door ----

    /** The lake beyond the far end; returns the shore point a back door tunnel starts from. */
    private int[] lake(JsonObject l) {
        int radius = J.i(l, "radius");
        int dist = J.i(l, "distance");
        int water = poolTop - 1;
        int cu = rift.length + dist + radius;
        int cx = rift.x(cu, 0);
        int cz = rift.z(cu, 0);
        pieces.add(new GoblinPieces.LakePiece(cx, cz, water, radius, J.i(l, "height"), J.i(l, "water_depth"),
                J.i(l, "island_radius"), pal));
        List<double[]> path = new ArrayList<>();
        path.add(new double[] {rift.x(rift.length - 6, 0) + 0.5, water - 2, rift.z(rift.length - 6, 0) + 0.5});
        path.add(new double[] {rift.x(rift.length + dist + 3, 0) + 0.5, water - 2, rift.z(rift.length + dist + 3, 0) + 0.5});
        JsonObject tun = J.o(j, "tunnel");
        pieces.add(new GoblinPieces.TunnelPiece(path, J.i(tun, "half_width"), 5, J.d(tun, "roughness"), water + 1, pal)
                .zone("gollum_lake"));
        doorways.put("lake", new int[] {cx, water, cz, -1});
        log.append(String.format("  Gollum's lake at (%d, %d, %d)%n", cx, water, cz));
        int su = rift.length + dist + radius + radius - 5;
        return new int[] {rift.x(su, 0), water + 1, rift.z(su, 0)};
    }

    private void backDoor(JsonObject b, int[] from) {
        int cx = from[0];
        int cz = from[2];
        if (b.has("near")) {
            cx = J.ints(J.a(b, "near"))[0];
            cz = J.ints(J.a(b, "near"))[1];
        }
        int[] above = J.range(b, "ground_above");
        int[] site = slope(cx, cz, J.i(b, "min_distance"), J.i(b, "search"), J.i(b, "look"), J.i(b, "rise"),
                from[1] + above[0], from[1] + above[1]);
        if (site == null) {
            log.append("  no slope for the back door\n");
            return;
        }
        int floor = site[2] + 1;
        int in = site[3];
        int out = (in + 2) % 4;
        int passage = J.i(b, "passage");
        pieces.add(new GoblinPieces.BackDoorPiece(site[0], site[1], floor, out, passage, pal));
        doorways.put("back_door", new int[] {site[0], floor, site[1], out});
        int ex = site[0] + DelveSpec.DX[in] * passage;
        int ez = site[1] + DelveSpec.DZ[in] * passage;
        JsonObject tun = J.o(j, "tunnel");
        double slope = J.d(tun, "max_slope");
        double[] a = {from[0] + 0.5, from[1], from[2] + 0.5};
        double[] e = {ex + 0.5, floor, ez + 0.5};
        double dx = e[0] - a[0];
        double dz = e[2] - a[2];
        double d = Math.hypot(dx, dz);
        double need = Math.abs(e[1] - a[1]) / slope;
        List<double[]> path = new ArrayList<>();
        path.add(a);
        if (need > d) {
            // too steep straight: zigzag so the climb stays walkable
            int n = 6;
            double amp = Math.min(160, Math.sqrt(Math.max(0, (need / n) * (need / n) - (d / n) * (d / n))));
            for (int i = 1; i < n; i++) {
                double t = i / (double) n;
                double off = (i % 2 == 0 ? -1 : 1) * amp;
                path.add(new double[] {a[0] + dx * t - dz / d * off, a[1] + (e[1] - a[1]) * t, a[2] + dz * t + dx / d * off});
            }
            path.add(e);
        } else {
            wiggle(path, a, e, J.d(tun, "wiggle"));
        }
        pieces.add(new GoblinPieces.TunnelPiece(path, J.i(tun, "half_width"), J.i(tun, "height"),
                J.d(tun, "roughness"), Integer.MIN_VALUE, pal).zone("tunnels"));
        log.append(String.format("  back door at (%d, %d, %d) facing %s%n", site[0], floor, site[1], DelveSpec.SIDES[out]));
    }
}
