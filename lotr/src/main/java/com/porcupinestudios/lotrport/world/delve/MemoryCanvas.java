package com.porcupinestudios.lotrport.world.delve;

import java.util.HashMap;
import java.util.Map;

/**
 * A canvas in memory over some columns, for checks without a world: the ground is solid below its height (no caves),
 * and what the delve writes is kept by position.
 */
public final class MemoryCanvas implements Canvas {
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final Ground ground;
    private final Map<Long, Mat> blocks = new HashMap<>();

    public MemoryCanvas(int minX, int minZ, int maxX, int maxZ, Ground ground) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.ground = ground;
    }

    /** The chunk holding a block. */
    public static MemoryCanvas chunkAt(int x, int z, Ground ground) {
        int x0 = x & ~15;
        int z0 = z & ~15;
        return new MemoryCanvas(x0, z0, x0 + 15, z0 + 15, ground);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    @Override
    public int minX() {
        return minX;
    }

    @Override
    public int maxX() {
        return maxX;
    }

    @Override
    public int minZ() {
        return minZ;
    }

    @Override
    public int maxZ() {
        return maxZ;
    }

    @Override
    public int minY() {
        return -64;
    }

    @Override
    public int maxY() {
        return 639;
    }

    @Override
    public void set(int x, int y, int z, Mat mat) {
        if (mat != Mat.KEEP) {
            blocks.put(key(x, y, z), mat);
        }
    }

    @Override
    public boolean solid(int x, int y, int z) {
        Mat m = blocks.get(key(x, y, z));
        return m != null ? !m.isAir() && !m.id.contains("water") && !m.id.contains("lava") : y < ground.height(x, z);
    }

    @Override
    public void template(String id, int rotation, int x, int y, int z) {}

    /** What the delve wrote there, null if nothing. */
    public Mat at(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    /** How many written blocks have an id containing the text. */
    public int count(String idPart) {
        int n = 0;
        for (Mat m : blocks.values()) {
            if (m.id.contains(idPart)) {
                n++;
            }
        }
        return n;
    }

    public int size() {
        return blocks.size();
    }
}
