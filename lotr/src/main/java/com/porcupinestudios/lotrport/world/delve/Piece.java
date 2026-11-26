package com.porcupinestudios.lotrport.world.delve;

/**
 * Something a delve builds, inside its box {@code x0..x1, y0..y1, z0..z1} (walls and decoration included). Pieces
 * build in {@link #order} (corridors, then rooms and stairwells, then set pieces), so a room's walls and doorways
 * finish the corridors that run into it.
 */
abstract class Piece {
    final int order;
    int x0;
    int y0;
    int z0;
    int x1;
    int y1;
    int z1;

    Piece(int order) {
        this.order = order;
    }

    void bounds(int x0, int y0, int z0, int x1, int y1, int z1) {
        this.x0 = Math.min(x0, x1);
        this.y0 = Math.min(y0, y1);
        this.z0 = Math.min(z0, z1);
        this.x1 = Math.max(x0, x1);
        this.y1 = Math.max(y0, y1);
        this.z1 = Math.max(z0, z1);
    }

    void grow(int x0, int y0, int z0, int x1, int y1, int z1) {
        bounds(Math.min(this.x0, Math.min(x0, x1)), Math.min(this.y0, Math.min(y0, y1)),
                Math.min(this.z0, Math.min(z0, z1)), Math.max(this.x1, Math.max(x0, x1)),
                Math.max(this.y1, Math.max(y0, y1)), Math.max(this.z1, Math.max(z0, z1)));
    }

    boolean touches(Canvas c) {
        return x1 >= c.minX() && x0 <= c.maxX() && z1 >= c.minZ() && z0 <= c.maxZ();
    }

    abstract void build(Draw d);
}
