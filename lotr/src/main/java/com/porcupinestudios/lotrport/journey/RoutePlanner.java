package com.porcupinestudios.lotrport.journey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Finds the quickest way over the land between two points (plain rules: a {@link TerrainView} in, a {@link Route}
 * out). A* over a grid of {@code cell}-block squares, eight ways; each step is sampled every {@code sample} blocks:
 * open water stops it, a river stops it unless a road or a marked bridge crosses there, and its time is its length
 * over the mean speed along it (roads lift the speed to at least {@code roadSpeed}). The search stays in a box round
 * the two points ({@code margin} blocks beyond them) and gives up after {@code maxNodes}.
 */
public final class RoutePlanner {
    /**
     * @param cell grid size in blocks
     * @param sample blocks between samples along a step (rivers narrower than this can slip through)
     * @param roadSpeed speed on a road
     * @param margin how far beyond the two points the search may go, in blocks
     * @param maxNodes most cells it opens before giving up
     * @param greed how much the search leans toward the goal (1 = always the quickest way; more opens far fewer
     *     cells on long trips, for a way at most that many times slower, in practice a few percent)
     */
    public record Settings(int cell, int sample, double roadSpeed, int margin, int maxNodes, double greed) {
        public Settings(int cell, int sample, double roadSpeed, int margin, int maxNodes) {
            this(cell, sample, roadSpeed, margin, maxNodes, 1);
        }
    }

    /** Cells the last search opened, and whether it ran out of {@code maxNodes} (for logs and tests). */
    public static volatile String lastSearch = "";

    private RoutePlanner() {}

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** The route from (x1, z1) to (x2, z2), or {@link Route#NONE} when there is no way. */
    public static Route plan(TerrainView land, Settings s, int x1, int z1, int x2, int z2) {
        int cell = Math.max(4, s.cell());
        int si = Math.floorDiv(x1, cell);
        int sj = Math.floorDiv(z1, cell);
        int ti = Math.floorDiv(x2, cell);
        int tj = Math.floorDiv(z2, cell);
        int pad = Math.max(1, s.margin() / cell);
        int minI = Math.min(si, ti) - pad;
        int maxI = Math.max(si, ti) + pad;
        int minJ = Math.min(sj, tj) - pad;
        int maxJ = Math.max(sj, tj) + pad;
        // the heuristic's speed: the road's (never overestimates), lowered by greed
        double fastest = Math.max(1, s.roadSpeed()) / Math.max(1, s.greed());
        Map<Long, Double> best = new HashMap<>();
        Map<Long, Long> from = new HashMap<>();
        Map<Long, double[]> legOf = new HashMap<>();
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        long start = key(si, sj);
        long goal = key(ti, tj);
        best.put(start, 0.0);
        open.add(new double[] {heuristic(si, sj, ti, tj, cell, fastest), si, sj});
        // where each cell's node stands: on a road in it if there is one (so a route on a road stays on it), at a
        // marked bridge's end for the cells holding them, else the middle
        Map<Long, int[]> spot = new HashMap<>();
        Map<Long, List<long[]>> extra = new HashMap<>();
        for (int[] b : land.bridgeEnds()) {
            long ka = key(Math.floorDiv(b[0], cell), Math.floorDiv(b[1], cell));
            long kb = key(Math.floorDiv(b[2], cell), Math.floorDiv(b[3], cell));
            spot.put(ka, new int[] {b[0], b[1]});
            spot.put(kb, new int[] {b[2], b[3]});
            extra.computeIfAbsent(ka, x -> new ArrayList<>()).add(new long[] {kb});
            extra.computeIfAbsent(kb, x -> new ArrayList<>()).add(new long[] {ka});
        }
        spot.put(start, new int[] {x1, z1});
        spot.put(goal, new int[] {x2, z2});
        int opened = 0;
        boolean reached = start == goal;
        while (!open.isEmpty() && !reached && opened < s.maxNodes()) {
            double[] cur = open.poll();
            int i = (int) cur[1];
            int j = (int) cur[2];
            long k = key(i, j);
            double g = best.get(k);
            if (cur[0] - heuristic(i, j, ti, tj, cell, fastest) > g + 1e-6) {
                continue;
            }
            opened++;
            List<Long> next = new ArrayList<>(10);
            for (int[] st : STEPS) {
                int ni = i + st[0];
                int nj = j + st[1];
                if (ni >= minI && ni <= maxI && nj >= minJ && nj <= maxJ) {
                    next.add(key(ni, nj));
                }
            }
            for (long[] e : extra.getOrDefault(k, List.of())) {
                next.add(e[0]);
            }
            int[] a = spotOf(land, s, spot, k, cell);
            for (long nk : next) {
                int[] b = spotOf(land, s, spot, nk, cell);
                double[] leg = leg(land, s, a[0], a[1], b[0], b[1]);
                if (leg == null) {
                    continue;
                }
                double ng = g + leg[0];
                Double old = best.get(nk);
                if (old == null || ng < old - 1e-9) {
                    best.put(nk, ng);
                    from.put(nk, k);
                    legOf.put(nk, leg);
                    if (nk == goal) {
                        reached = true;
                    }
                    open.add(new double[] {ng + heuristic(ix(nk), jz(nk), ti, tj, cell, fastest), ix(nk), jz(nk)});
                }
            }
        }
        if (!best.containsKey(goal)) {
            return Route.NONE;
        }
        if (start == goal) {
            double[] leg = leg(land, s, x1, z1, x2, z2);
            return leg == null ? Route.NONE : new Route(new int[] {x1, x2}, new int[] {z1, z2},
                    new double[] {leg[0]}, leg[1]);
        }
        // walk back, then merge straight runs
        List<long[]> cells = new ArrayList<>();
        List<double[]> legs = new ArrayList<>();
        for (long k = goal; k != start; k = from.get(k)) {
            cells.add(0, new long[] {k});
            legs.add(0, legOf.get(k));
        }
        List<int[]> pts = new ArrayList<>();
        List<Double> costs = new ArrayList<>();
        double road = 0;
        double all = 0;
        pts.add(new int[] {x1, z1});
        int lastDi = Integer.MIN_VALUE;
        int lastDj = Integer.MIN_VALUE;
        long prev = start;
        for (int n = 0; n < cells.size(); n++) {
            long k = cells.get(n)[0];
            int di = Long.signum(ix(k) - ix(prev));
            int dj = Long.signum(jz(k) - jz(prev));
            int[] p = spot.get(k);
            double[] leg = legs.get(n);
            road += leg[0] * leg[1];
            all += leg[0];
            boolean straight = di == lastDi && dj == lastDj && Math.abs(ix(k) - ix(prev)) <= 1
                    && Math.abs(jz(k) - jz(prev)) <= 1 && legs.get(n)[1] == 0 && (n == 0 || legs.get(n - 1)[1] == 0);
            if (straight && n < cells.size() - 1 && costs.size() > 0 && pts.size() > 1) {
                pts.set(pts.size() - 1, p);
                costs.set(costs.size() - 1, costs.get(costs.size() - 1) + leg[0]);
            } else {
                pts.add(p);
                costs.add(leg[0]);
            }
            lastDi = di;
            lastDj = dj;
            prev = k;
        }
        // a leg between two road spots is a chord across the road's bends: put points on the road along it, so a
        // route that follows a road stays on it all the way (bodies walk these points)
        List<int[]> fine = new ArrayList<>();
        List<Double> fineCosts = new ArrayList<>();
        fine.add(pts.get(0));
        for (int n = 1; n < pts.size(); n++) {
            int[] p0 = pts.get(n - 1);
            int[] p1 = pts.get(n);
            double cost = costs.get(n - 1);
            List<int[]> mid = new ArrayList<>();
            if (land.road(p0[0], p0[1]) && land.road(p1[0], p1[1])) {
                double len = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
                int steps = (int) (len / Math.max(2, s.sample()));
                for (int t = 1; t < steps; t++) {
                    int x = (int) Math.round(p0[0] + (p1[0] - p0[0]) * t / (double) steps);
                    int z = (int) Math.round(p0[1] + (p1[1] - p0[1]) * t / (double) steps);
                    int[] r = nearestRoad(land, x, z, Math.max(4, s.cell()) / 2);
                    if (r != null) {
                        mid.add(r);
                    }
                }
            }
            mid.add(p1);
            double total = 0;
            int[] q = p0;
            for (int[] m : mid) {
                total += Math.hypot(m[0] - q[0], m[1] - q[1]);
                q = m;
            }
            q = p0;
            for (int[] m : mid) {
                double part = Math.hypot(m[0] - q[0], m[1] - q[1]);
                fine.add(m);
                fineCosts.add(total <= 0 ? cost / mid.size() : cost * part / total);
                q = m;
            }
        }
        pts = fine;
        costs = fineCosts;
        int[] xs = new int[pts.size()];
        int[] zs = new int[pts.size()];
        for (int n = 0; n < pts.size(); n++) {
            xs[n] = pts.get(n)[0];
            zs[n] = pts.get(n)[1];
        }
        double[] cs = new double[costs.size()];
        for (int n = 0; n < cs.length; n++) {
            cs[n] = costs.get(n);
        }
        return new Route(xs, zs, cs, all <= 0 ? 0 : road / all);
    }

    /**
     * One straight step: its time at speed 1 and the share of it on roads, or null if it can't be made (open water,
     * or a river with no road or bridge where it crosses).
     */
    public static double[] leg(TerrainView land, Settings s, int ax, int az, int bx, int bz) {
        double len = Math.sqrt((double) (bx - ax) * (bx - ax) + (double) (bz - az) * (bz - az));
        int n = Math.max(1, (int) Math.ceil(len / Math.max(1, s.sample())));
        double slow = 0;
        int roads = 0;
        for (int t = 0; t <= n; t++) {
            int x = (int) Math.round(ax + (bx - ax) * (t / (double) n));
            int z = (int) Math.round(az + (bz - az) * (t / (double) n));
            // steps run between cell middles, so a road or bridge within half a cell counts as taken
            int reach = Math.max(4, s.cell()) / 2;
            boolean road = near(land, x, z, reach, s.sample(), false);
            if (land.river(x, z) && !road && !crossing(land, x, z, Math.max(4, s.cell()), s.sample())) {
                return null;
            }
            double v = land.speed(x, z);
            if (road) {
                v = Math.max(v, s.roadSpeed());
                roads++;
            } else if (land.river(x, z)) {
                v = Math.max(v, 1);
            }
            if (v <= 0) {
                return null;
            }
            slow += 1 / v;
        }
        return new double[] {len * slow / (n + 1), roads / (double) (n + 1)};
    }

    /** The road block nearest (x, z) within {@code reach}, or null. */
    private static int[] nearestRoad(TerrainView land, int x, int z, int reach) {
        if (land.road(x, z)) {
            return new int[] {x, z};
        }
        for (int r = 1; r <= reach; r++) {
            for (int d = -r; d <= r; d++) {
                int[][] ring = {{x + d, z - r}, {x + d, z + r}, {x - r, z + d}, {x + r, z + d}};
                for (int[] c : ring) {
                    if (land.road(c[0], c[1])) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    /** A cell's node: fixed spots first, else the road point nearest its middle, else its middle. */
    private static int[] spotOf(TerrainView land, Settings s, Map<Long, int[]> spot, long k, int cell) {
        int[] p = spot.get(k);
        if (p != null) {
            return p;
        }
        int cx = centre(ix(k), cell);
        int cz = centre(jz(k), cell);
        int[] bestP = land.roadPoint(cx, cz, cell / 2, Math.max(1, s.sample() / 2));
        p = bestP != null ? bestP : new int[] {cx, cz};
        spot.put(k, p);
        return p;
    }

    /** A road or marked bridge anywhere in the square {@code reach} round a river point (rivers are rare: worth it). */
    private static boolean crossing(TerrainView land, int x, int z, int reach, int sample) {
        int st = Math.max(1, sample);
        for (int dx = -reach; dx <= reach; dx += st) {
            for (int dz = -reach; dz <= reach; dz += st) {
                if (land.road(x + dx, z + dz) || land.bridge(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A road (or a marked bridge) at the point or within {@code reach} along x or z. */
    private static boolean near(TerrainView land, int x, int z, int reach, int sample, boolean bridge) {
        int st = Math.max(1, sample);
        for (int d = 0; d <= reach; d += st) {
            for (int sign = -1; sign <= 1; sign += 2) {
                if (d == 0 && sign > 0) {
                    continue;
                }
                int o = d * sign;
                boolean hit = bridge ? land.bridge(x + o, z) || land.bridge(x, z + o)
                        : land.road(x + o, z) || land.road(x, z + o);
                if (hit) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double heuristic(int i, int j, int ti, int tj, int cell, double fastest) {
        double di = (double) (ti - i) * cell;
        double dj = (double) (tj - j) * cell;
        return Math.sqrt(di * di + dj * dj) / fastest;
    }

    private static int centre(int i, int cell) {
        return i * cell + cell / 2;
    }

    private static long key(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
    }

    private static int ix(long k) {
        return (int) (k >> 32);
    }

    private static int jz(long k) {
        return (int) k;
    }
}
