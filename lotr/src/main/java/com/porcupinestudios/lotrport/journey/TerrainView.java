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
}
