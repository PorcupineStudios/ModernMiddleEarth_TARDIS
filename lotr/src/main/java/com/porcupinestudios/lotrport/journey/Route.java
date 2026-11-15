package com.porcupinestudios.lotrport.journey;

import java.util.Arrays;

/**
 * A planned way between two places: points in block x, z, and for each leg the time it takes at speed 1 (blocks
 * against grassland), so a party's time on a leg is {@code cost / party speed}. Legs are as the planner found them, a
 * cell or a few long; turns are kept, straight runs merged.
 *
 * @param xs block x of each point, the start first
 * @param zs block z of each point
 * @param costs time of each leg at speed 1 (one fewer than the points)
 * @param roadShare how much of the way (by cost) runs on roads, 0-1, for the map and the ledger
 */
public record Route(int[] xs, int[] zs, double[] costs, double roadShare) {
    public static final Route NONE = new Route(new int[0], new int[0], new double[0], 0);

    public int points() {
        return xs.length;
    }

    public boolean found() {
        return xs.length >= 2;
    }

    /** All the legs' costs together. */
    public double total() {
        double t = 0;
        for (double c : costs) {
            t += c;
        }
        return t;
    }

    /** Where a party is after {@code done} of the total cost: x, z (blocks) and the leg it is on. */
    public double[] at(double done) {
        if (xs.length == 0) {
            return new double[] {0, 0, 0};
        }
        double left = Math.max(0, done);
        for (int i = 0; i < costs.length; i++) {
            if (left <= costs[i] || i == costs.length - 1) {
                double f = costs[i] <= 0 ? 1 : Math.min(1, left / costs[i]);
                return new double[] {xs[i] + (xs[i + 1] - xs[i]) * f, zs[i] + (zs[i + 1] - zs[i]) * f, i};
            }
            left -= costs[i];
        }
        return new double[] {xs[xs.length - 1], zs[zs.length - 1], Math.max(0, costs.length - 1)};
    }

    /** The cost done when standing nearest to (x, z) on legs from {@code fromLeg} on (a body that walked the way). */
    public double doneAt(double x, double z, int fromLeg) {
        double best = Double.MAX_VALUE;
        double bestDone = 0;
        double before = 0;
        for (int i = 0; i < costs.length; i++) {
            if (i >= fromLeg) {
                double ax = xs[i];
                double az = zs[i];
                double dx = xs[i + 1] - ax;
                double dz = zs[i + 1] - az;
                double len2 = dx * dx + dz * dz;
                double f = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / len2));
                double px = ax + dx * f - x;
                double pz = az + dz * f - z;
                double d = px * px + pz * pz;
                if (d < best) {
                    best = d;
                    bestDone = before + costs[i] * f;
                }
            }
            before += costs[i];
        }
        return bestDone;
    }

    /** The route the other way round (a caravan going home). */
    public Route reversed() {
        int n = xs.length;
        int[] rx = new int[n];
        int[] rz = new int[n];
        double[] rc = new double[costs.length];
        for (int i = 0; i < n; i++) {
            rx[i] = xs[n - 1 - i];
            rz[i] = zs[n - 1 - i];
        }
        for (int i = 0; i < costs.length; i++) {
            rc[i] = costs[costs.length - 1 - i];
        }
        return new Route(rx, rz, rc, roadShare);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Route r && Arrays.equals(xs, r.xs) && Arrays.equals(zs, r.zs)
                && Arrays.equals(costs, r.costs);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(xs) * 31 + Arrays.hashCode(zs);
    }
}
