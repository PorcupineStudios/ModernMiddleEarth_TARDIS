package com.porcupinestudios.lotrexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Spots to sample on LOTR's map: per wanted biome up to {@code perBiome} random pixels of LOTR's biome image, inside the
 * biome (its four neighbours the same) where it has any, else any of its pixels. Shared by the structure rates and the
 * plant census. Pixels come back interleaved (first pixel of every biome, then the second...), so a run cut short by
 * its time budget still covers every biome.
 */
final class MapPatches {
    final List<int[]> pixels = new ArrayList<>();
    final int originX;
    final int originZ;
    final int scale;

    MapPatches(Object[] biomeList, boolean[] wanted, int perBiome, long seed, JsonArray errors) throws Exception {
        Class<?> layer = Class.forName("lotr.common.world.genlayer.LOTRGenLayerWorld");
        if (!(Boolean) layer.getMethod("loadedBiomeImage").invoke(null)) {
            throw new IllegalStateException("LOTR's map image isn't loaded");
        }
        Field data = layer.getDeclaredField("biomeImageData");
        data.setAccessible(true);
        byte[] image = (byte[]) data.get(null);
        int w = layer.getField("imageWidth").getInt(null);
        int h = layer.getField("imageHeight").getInt(null);
        originX = layer.getField("originX").getInt(null);
        originZ = layer.getField("originZ").getInt(null);
        scale = layer.getField("scale").getInt(null);
        Random random = new Random(seed);
        int[][] picks = new int[biomeList.length][perBiome];
        int[] seen = new int[biomeList.length];
        for (int pass = 0; pass < 2; pass++) {
            boolean[] open = new boolean[biomeList.length]; // pass 1: biomes with no pixel inside, any pixel
            for (int id = 0; id < biomeList.length; id++) {
                open[id] = wanted[id] && seen[id] == 0;
            }
            for (int pz = 1; pz < h - 1; pz++) {
                for (int px = 1; px < w - 1; px++) {
                    int i = pz * w + px;
                    int id = image[i] & 255;
                    if (id >= biomeList.length || !open[id] || pass == 0 && ((image[i - 1] & 255) != id
                            || (image[i + 1] & 255) != id || (image[i - w] & 255) != id
                            || (image[i + w] & 255) != id)) {
                        continue;
                    }
                    int n = seen[id]++; // reservoir sampling
                    int r = n < perBiome ? n : random.nextInt(n + 1);
                    if (r < perBiome) {
                        picks[id][r] = i;
                    }
                }
            }
        }
        for (int p = 0; p < perBiome; p++) {
            for (int id = 0; id < biomeList.length; id++) {
                if (wanted[id] && p < seen[id]) {
                    pixels.add(new int[] { picks[id][p] % w, picks[id][p] / w });
                } else if (wanted[id] && p == 0) {
                    errors.add(new JsonPrimitive("biome " + ((BiomeGenBase) biomeList[id]).biomeName
                            + " not on the map"));
                }
            }
        }
    }

    /** The chunk x of a pixel's middle. */
    int chunkX(int[] pixel) {
        return (pixel[0] - originX) * scale + scale / 2 >> 4;
    }

    int chunkZ(int[] pixel) {
        return (pixel[1] - originZ) * scale + scale / 2 >> 4;
    }
}
