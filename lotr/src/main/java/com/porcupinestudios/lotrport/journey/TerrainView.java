package com.porcupinestudios.lotrport.journey;

/**
 * What the route planner asks of the land, by block x, z (no chunks loaded: answered from the map and saved data).
 * The world's answer is {@link MapTerrain}; tests give their own.
 */
public interface TerrainView {
    /** How fast a party moves here off a road, against open grassland (1); 0 for open water no one can cross. */
    double speed(int x, int z);

    /** A road runs here. */
    boolean road(int x, int z);

    /** A river runs here: only crossed on a road (its bridge) or a marked bridge. */
    boolean river(int x, int z);

    /** A marked bridge is here. */
    boolean bridge(int x, int z);

    /**
     * The road point nearest (cx, cz) within {@code half} blocks along x and z, or null: where a cell's route node
     * goes. This default scans; the map answers from its road segments.
     */
    default int[] roadPoint(int cx, int cz, int half, int step) {
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int dx = -half; dx < half; dx += step) {
            for (int dz = -half; dz < half; dz += step) {
                long d = (long) dx * dx + (long) dz * dz;
                if (d < bestD && road(cx + dx, cz + dz)) {
                    bestD = d;
                    best = new int[] {cx + dx, cz + dz};
                }
            }
        }
        return best;
    }

    /** Marked bridges end to end, {x1, z1, x2, z2} each: the planner adds each as a way across. */
    default java.util.List<int[]> bridgeEnds() {
        return java.util.List.of();
    }
}
