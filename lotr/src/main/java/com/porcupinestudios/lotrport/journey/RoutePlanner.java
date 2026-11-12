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
     */
    public record Settings(int cell, int sample, double roadSpeed, int margin, int maxNodes) {}

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
        double fastest = Math.max(1, s.roadSpeed());
        Map<Long, Double> best = new HashMap<>();
        Map<Long, Long> from = new HashMap<>();
        Map<Long, double[]> legOf = new HashMap<>();
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        long start = key(si, sj);
        long goal = key(ti, tj);
        best.put(start, 0.0);
        open.add(new double[] {heuristic(si, sj, ti, tj, cell, fastest), si, sj});
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
            for (int[] st : STEPS) {
                int ni = i + st[0];
                int nj = j + st[1];
                if (ni < minI || ni > maxI || nj < minJ || nj > maxJ) {
                    continue;
                }
                // the first and last steps run from and to the real points, not the cells' middles
                int ax = k == start ? x1 : centre(i, cell);
                int az = k == start ? z1 : centre(j, cell);
                long nk = key(ni, nj);
                int bx = nk == goal ? x2 : centre(ni, cell);
                int bz = nk == goal ? z2 : centre(nj, cell);
                double[] leg = leg(land, s, ax, az, bx, bz);
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
                    open.add(new double[] {ng + heuristic(ni, nj, ti, tj, cell, fastest), ni, nj});
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
            int[] p = k == goal ? new int[] {x2, z2} : new int[] {centre(ix(k), cell), centre(jz(k), cell)};
            double[] leg = legs.get(n);
            road += leg[0] * leg[1];
            all += leg[0];
            if (di == lastDi && dj == lastDj && n < cells.size() - 1 && costs.size() > 0 && pts.size() > 1) {
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
            if (land.river(x, z) && !road && !near(land, x, z, reach, s.sample(), true)) {
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
