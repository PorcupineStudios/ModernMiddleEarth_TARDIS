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
 * patch of hills into a meadow. Seeded by the world seed since 2026-11-25 (Aaron: "randomness on new worlds"):
 * which cell is which patch, and the edges' meander, differ per world.
 *
 * <p>Large patches (Eriador's, Aaron 2026-11-25: travellers shouldn't change landscape every few hundred blocks, "but
 * there still being variation") take two more settings: {@code warp} (share of a cell the patch edges meander, by a
 * smooth noise, so 700-block patches don't meet along straight lines) and {@code feather} (blocks over which
 * {@link com.porcupinestudios.lotrport.world.feature.InVariant} thins plants out at an edge; 20 by default).
 *
 * <p>map.json: {@code "variants": {"lotr_port:shire": {"cell": 176, "chance": 0.6, "list": [{"name": "hills",
 * "weight": 3, "downs": 1.6, "variation": 1.2}, ...]}, "lotr_port:eriador": {"cell": 768, "chance": 0.9,
 * "warp": 0.35, "feather": 56, "list": [...]}}}.
 */
public final class Variants {
    /** One kind of patch: its name, weight among the biome's variants, and how it scales downs and hilliness. */
    public record Variant(String name, double weight, double downs, double variation) {}

    /** A biome's variants. */
    record Set(int cell, double chance, List<Variant> list, double total, double warp, int feather) {}

    /** How far plants of a patch reach over its edge when the biome has no feather of its own (blocks). */
    public static final int FEATHER = 20;

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
                    total, o.has("warp") ? o.get("warp").getAsDouble() : 0,
                    o.has("feather") ? o.get("feather").getAsInt() : FEATHER));
        }
    }

    boolean has(int biome) {
        return byBiome.containsKey(biome);
    }

    /** Blocks over which a biome's patches feather their plants out ({@link #FEATHER} without variants). */
    int feather(int biome) {
        Set set = byBiome.get(biome);
        return set == null ? FEATHER : set.feather();
    }

    /** The variant of the patch a column is in, for a biome with variants; null for the plain biome. */
    Variant at(int biome, int x, int z) {
        Set set = byBiome.get(biome);
        if (set == null) {
            return null;
        }
        if (set.warp() > 0) { // meandering edges: look the patch up at a smoothly shifted position
            double w = set.warp() * set.cell();
            double s = set.cell() * 0.45;
            int wx = (int) Math.floor(x + w * warp(x / s, z / s, 0x5741525058L));
            int wz = (int) Math.floor(z + w * warp(x / s, z / s, 0x574152505AL));
            x = wx;
            z = wz;
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

    /** Smooth value noise in about [-1, 1]: an octave at the given scale and one at a third of it. */
    private static double warp(double x, double z, long salt) {
        return 0.75 * valueNoise(x, z, salt) + 0.25 * valueNoise(x * 3, z * 3, salt * 31 + 7);
    }

    private static double valueNoise(double x, double z, long salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = x - x0;
        double tz = z - z0;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = lattice(x0, z0, salt);
        double b = lattice(x0 + 1, z0, salt);
        double c = lattice(x0, z0 + 1, salt);
        double d = lattice(x0 + 1, z0 + 1, salt);
        double top = a + (b - a) * tx;
        return top + (c + (d - c) * tx - top) * tz;
    }

    private static double lattice(int x, int z, long salt) {
        return unit(mix(x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt ^ worldSalt()), 0) * 2 - 1;
    }

    private static long hash(int cx, int cz) {
        return mix(cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL ^ 0x5641524E54L ^ worldSalt());
    }

    /** The world seed, mixed (0's mix for a client, which never knows the seed). */
    private static long worldSalt() {
        return mix(MiddleEarthMap.seed() ^ 0x5641525345L);
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
