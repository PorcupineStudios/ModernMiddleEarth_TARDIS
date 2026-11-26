package com.porcupinestudios.lotrport.world.delve;

/**
 * Where a delve's pieces write: one chunk's columns (the game, {@code DelveFeature}) or a preview. Writes outside
 * the clip are dropped, so a piece may draw its whole shape and each chunk keeps its own part.
 */
public interface Canvas {
    int minX();

    int maxX();

    int minZ();

    int maxZ();

    int minY();

    int maxY();

    /** Places the block; {@link Mat#KEEP} does nothing. */
    void set(int x, int y, int z, Mat mat);

    /** Whether the block there now is solid ground (not air, fluid or a plant). */
    boolean solid(int x, int y, int z);

    /**
     * Places a structure template turned {@code rotation} quarter turns clockwise with its box's lowest corner at
     * (x, y, z), clipped to this canvas.
     */
    void template(String id, int rotation, int x, int y, int z);
}
