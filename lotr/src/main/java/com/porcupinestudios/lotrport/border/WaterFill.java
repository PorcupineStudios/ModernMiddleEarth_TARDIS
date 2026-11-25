package com.porcupinestudios.lotrport.border;

import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.function.IntUnaryOperator;

/**
 * Rivers and lakes lie within borders: their map pixels (biomes listed as {@code fill} in {@code border_base}) take the
 * holder of the land around them instead of cutting a realm in two. Borders lie above the rivers; they never follow
 * them. A flood from every bank at once: each water pixel touching land takes the commonest holder of its land
 * neighbours (nobody's when unheld land is as common), then the rest of the water, ring by ring, takes the value of
 * the nearest bank. So a river leaving a realm becomes nobody's where its banks do, instead of carrying the realm's
 * colour along its whole length. Water with no held land around it (a lake in the wilds) stays nobody's.
 * Deterministic (pixels in map order), common code: the server's borders and the client's map layer use the same fill.
 */
public final class WaterFill {
    /** Biome holder code: nobody's. */
    public static final int NONE = -1;
    /** Biome holder code: water that takes the holder around it. */
    public static final int FILL = -2;

    /** Internal: water reached from nobody's banks (stops a holder flooding along the river beyond its land). */
    private static final int WILD = -3;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private final Int2IntOpenHashMap owners;

    private WaterFill(Int2IntOpenHashMap owners) {
        this.owners = owners;
    }

    /** The holder (an index the caller gave) of a water pixel; {@link #NONE} for nobody's or a pixel that isn't water. */
    public int ownerOf(int pixel) {
        int o = owners.get(pixel);
        return o == WILD ? NONE : o;
    }

    /** A grid of biome indices, {@code width} x {@code height} (the map, or a test's). */
    public interface Grid {
        int biomeAt(int x, int z);
    }

    /** @param ownerOfBiome a biome index's holder: an index >= 0, {@link #NONE} or {@link #FILL} */
    public static WaterFill of(MiddleEarthMap map, IntUnaryOperator ownerOfBiome) {
        return of(map.width(), map.height(), map.biomes().size(), map::pixelIndex, ownerOfBiome);
    }

    public static WaterFill of(int w, int h, int biomes, Grid map, IntUnaryOperator ownerOfBiome) {
        int[] byBiome = new int[biomes];
        boolean any = false;
        for (int b = 0; b < biomes; b++) {
            byBiome[b] = ownerOfBiome.applyAsInt(b);
            any |= byBiome[b] == FILL;
        }
        Int2IntOpenHashMap owners = new Int2IntOpenHashMap();
        owners.defaultReturnValue(NONE);
        if (!any) {
            return new WaterFill(owners);
        }
        IntArrayList water = new IntArrayList();
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                int b = map.biomeAt(x, z);
                if (b >= 0 && b < biomes && byBiome[b] == FILL) {
                    water.add(z * w + x);
                }
            }
        }
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        int[] seen = new int[4];
        for (int i = 0; i < water.size(); i++) {
            int p = water.getInt(i);
            int x = p % w;
            int z = p / w;
            int n = 0;
            int wild = 0;
            for (int[] d : DIRS) {
                int nx = x + d[0];
                int nz = z + d[1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) {
                    continue;
                }
                int b = map.biomeAt(nx, nz);
                int o = b >= 0 && b < biomes ? byBiome[b] : NONE;
                if (o >= 0) {
                    seen[n++] = o;
                } else if (o != FILL) {
                    wild++; // nobody's land (or sea): the river doesn't carry a holder past it
                }
            }
            if (n == 0 && wild > 0) {
                owners.put(p, WILD);
                queue.enqueue(p);
            } else if (n > 0) {
                // the commonest land holder among the neighbours (first seen wins a tie)
                int best = seen[0];
                int bestCount = 0;
                for (int a = 0; a < n; a++) {
                    int c = 0;
                    for (int b2 = 0; b2 < n; b2++) {
                        c += seen[b2] == seen[a] ? 1 : 0;
                    }
                    if (c > bestCount) {
                        best = seen[a];
                        bestCount = c;
                    }
                }
                owners.put(p, bestCount >= wild ? best : WILD);
                queue.enqueue(p);
            }
        }
        while (!queue.isEmpty()) {
            int p = queue.dequeueInt();
            int o = owners.get(p);
            int x = p % w;
            int z = p / w;
            int[] next = {x + 1 < w ? p + 1 : -1, x > 0 ? p - 1 : -1, z + 1 < h ? p + w : -1, z > 0 ? p - w : -1};
            for (int q : next) {
                if (q < 0 || owners.containsKey(q)) {
                    continue;
                }
                int b = map.biomeAt(q % w, q / w);
                if (b >= 0 && b < biomes && byBiome[b] == FILL) {
                    owners.put(q, o);
                    queue.enqueue(q);
                }
            }
        }
        return new WaterFill(owners);
    }
}
