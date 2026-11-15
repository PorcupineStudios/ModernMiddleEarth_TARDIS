package com.porcupinestudios.lotrport.world.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Biome variants (LOTR's LOTRBiomeVariant, Aaron 2026-11-14: "many biomes within the Shire"): patches inside a biome
 * with their own terrain and plants, as LOTR had (flower meadows, copses, woods, hills, wooded hills, birch and aspen
 * woods, orchards). They are not separate biomes, as in LOTR: everything keyed to the biome (its NPCs, structures,
 * roads, music, waypoint region) stays the same. Plants follow the patches through the {@code lotr_port:in_variant}
 * placement filter ({@link com.porcupinestudios.lotrport.world.feature.InVariant}); terrain through
 * {@link #downsFactor} and {@link #variationFactor}.
 *
 * <p>The patches are cells of a jittered grid ({@code cell} blocks): each cell is a variant (picked by weight) with
 * chance {@code chance}, else the plain biome. A column's variant is its nearest cell's (sharp edges for plants, like
 * LOTR's); terrain factors are blended over nearby cells with Gaussian weights, so the ground changes smoothly from a
 * patch of hills into a meadow. Seedless: the same everywhere.
 *
 * <p>map.json: {@code "variants": {"lotr_port:shire": {"cell": 176, "chance": 0.6, "list": [{"name": "hills",
 * "weight": 3, "downs": 1.6, "variation": 1.2}, ...]}}}.
 */
public final class Variants {
    /** One kind of patch: its name, weight among the biome's variants, and how it scales downs and hilliness. */
    public record Variant(String name, double weight, double downs, double variation) {}

    /** A biome's variants. */
    record Set(int cell, double chance, List<Variant> list, double total) {}

    private final Map<Integer, Set> byBiome = new HashMap<>();

    Variants(JsonObject json, List<MiddleEarthMap.Entry> biomes) {
        if (json == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            int index = -1;
            for (int i = 0; i < biomes.size(); i++) {
                if (biomes.get(i).id().equals(e.getKey())) {
                    index = i;
                }
            }
            if (index < 0) {
                throw new IllegalArgumentException("variants for " + e.getKey() + ", which is not in the biome table");
            }
            JsonObject o = e.getValue().getAsJsonObject();
            List<Variant> list = new ArrayList<>();
            double total = 0;
            for (JsonElement v : o.getAsJsonArray("list")) {
                JsonObject vo = v.getAsJsonObject();
                Variant variant = new Variant(vo.get("name").getAsString(), vo.get("weight").getAsDouble(),
                        vo.has("downs") ? vo.get("downs").getAsDouble() : 1,
                        vo.has("variation") ? vo.get("variation").getAsDouble() : 1);
                list.add(variant);
                total += variant.weight();
            }
            byBiome.put(index, new Set(o.get("cell").getAsInt(), o.get("chance").getAsDouble(), List.copyOf(list),
                    total));
        }
    }

    boolean has(int biome) {
        return byBiome.containsKey(biome);
    }

    /** The variant of the patch a column is in, for a biome with variants; null for the plain biome. */
    Variant at(int biome, int x, int z) {
        Set set = byBiome.get(biome);
        if (set == null) {
            return null;
        }
        int cx = Math.floorDiv(x, set.cell());
        int cz = Math.floorDiv(z, set.cell());
        double best = Double.MAX_VALUE;
        long bestHash = 0;
        for (int i = -1; i <= 1; i++) {
            for (int k = -1; k <= 1; k++) {
                long h = hash(cx + i, cz + k);
                double dx = x - (cx + i + 0.1 + 0.8 * unit(h, 1)) * set.cell();
                double dz = z - (cz + k + 0.1 + 0.8 * unit(h, 2)) * set.cell();
                double d = dx * dx + dz * dz;
                if (d < best) {
                    best = d;
                    bestHash = h;
                }
            }
        }
        return pick(set, bestHash);
    }

    /** How much a biome's downs are scaled at a column: 1 off its patches, blended smoothly between them. */
    double downsFactor(int biome, int x, int z) {
        return blend(biome, x, z, true);
    }

    /** How much a biome's hilliness (noise) is scaled at a column. */
    double variationFactor(int biome, int x, int z) {
        return blend(biome, x, z, false);
    }

    private double blend(int biome, int x, int z, boolean downs) {
        Set set = byBiome.get(biome);
        if (set == null) {
            return 1;
        }
        int cx = Math.floorDiv(x, set.cell());
        int cz = Math.floorDiv(z, set.cell());
        double sigma2 = 2 * Math.pow(0.32 * set.cell(), 2);
        double sum = 0;
        double weights = 0;
        for (int i = -2; i <= 2; i++) {
            for (int k = -2; k <= 2; k++) {
                long h = hash(cx + i, cz + k);
                double dx = x - (cx + i + 0.1 + 0.8 * unit(h, 1)) * set.cell();
                double dz = z - (cz + k + 0.1 + 0.8 * unit(h, 2)) * set.cell();
                double w = Math.exp(-(dx * dx + dz * dz) / sigma2);
                Variant v = pick(set, h);
                sum += w * (v == null ? 1 : downs ? v.downs() : v.variation());
                weights += w;
            }
        }
        return weights > 1.0E-9 ? sum / weights : 1;
    }

    private static Variant pick(Set set, long h) {
        if (unit(h, 0) >= set.chance()) {
            return null;
        }
        double r = unit(h, 3) * set.total();
        for (Variant v : set.list()) {
            r -= v.weight();
            if (r < 0) {
                return v;
            }
        }
        return set.list().get(set.list().size() - 1);
    }

    private static long hash(int cx, int cz) {
        return mix(cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL ^ 0x5641524E54L);
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }

    private static double unit(long h, int n) {
        return (mix(h + n * 0x632BE59BD9B4E019L) >>> 11) * 0x1.0p-53;
    }
}
