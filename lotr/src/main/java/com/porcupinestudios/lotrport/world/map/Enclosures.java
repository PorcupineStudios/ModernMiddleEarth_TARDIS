package com.porcupinestudios.lotrport.world.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enclosures: small fenced plots set into a biome's land, with ground flattened to one level (Aaron 2026-11-25: the
 * Shire's orchards "flatter and a lot smaller ... enclosed in fences", and Farmer Maggot's fields with 2-block-high
 * crops, sized so farmland "makes sense"). They replace the Shire's 176-block orchard variants.
 *
 * <p>The land is cut into a grid of {@code cell}-block cells (a multiple of 16, so a chunk is always inside one cell).
 * Each cell holds at most one enclosure: its kind is rolled by the kinds' {@code chance} in the biome at the cell's
 * middle, its length and width are rolled in the kind's ranges (blocks, fence included), and it is placed inside the
 * cell at least {@code fade + gap} blocks from the cell's edge, so neighbouring enclosures never touch and a column
 * only ever has to look at its own cell. A rolled enclosure is kept only on good ground: the biome is the same at
 * its corners, edge middles, centre and at the corners of its fade (no river, no other biome), the patch there is
 * one of the kind's {@code variants} ("plain" = no patch), no road comes within {@code road_gap} blocks, and the
 * map's ground under it varies by at most {@code max_relief} blocks and stays above the sea. Its ground is then
 * flattened to the middle of those heights inside the fence and {@link #PLATEAU} blocks round it, easing back to the
 * land over the rest of {@code fade} blocks outside it, and the hills' noise there is scaled by {@code roughness}
 * (0 = flat as a table).
 *
 * <p>Everything is a pure function of the map and the world seed (Aaron 2026-11-25: seeded, so every new world's
 * plots differ), no saved data. {@link MiddleEarthMap#heightAt} and
 * {@link MiddleEarthMap#variationAt} shape the ground; {@code lotr_port:enclosure} features build the fences, gates,
 * tree rows and crops; trees, patches and LOTR structures stay out (filters {@code lotr_port:outside_enclosures},
 * {@code lotr_port:in_variant}, {@code LotrStructure}).
 *
 * <p>map.json: {@code "enclosures": {"cell": 128, "fade": 24, "gap": 4, "road_gap": 10, "biomes": {"lotr_port:shire":
 * [{"name": "orchard", "chance": 0.07, "length": [18, 34], "width": [14, 26], "max_relief": 6, "roughness": 0.1,
 * "gates": 1, "variants": ["plain", "meadows"], "boost": {"farmland": 3}}, ...]}}}. {@code boost} multiplies the
 * chance in cells whose middle lies in that variant patch (the Shire's fields gather in its farmland patches).
 */
public final class Enclosures {
    /** One kind of enclosure in a biome; lengths and widths are block counts including the fence. */
    public record Kind(String name, double chance, int minLength, int maxLength, int minWidth, int maxWidth,
            double maxRelief, double roughness, int gates, List<String> variants, Map<String, Double> boost) {
        /** The kind's chance in a cell whose middle is in the named variant patch ("plain" for none). */
        double chanceIn(String variant) {
            return chance * boost.getOrDefault(variant, 1.0);
        }
    }

    /** A gate on the fence: its column and the side it is on (0 north, 1 south, 2 west, 3 east). */
    public record Gate(int x, int z, int side) {}

    /**
     * One enclosure: its kind, its biome's index, the rectangle its fence runs round (inclusive), the y its ground is
     * flattened to, its gates and a number to roll its own choices from (hedge or fence...).
     */
    public record Enclosure(Kind kind, int biome, int minX, int minZ, int maxX, int maxZ, double level,
            List<Gate> gates, long seed) {
        /** Whether a column is inside the fence (or on it), grown by {@code margin} blocks. */
        public boolean contains(int x, int z, int margin) {
            return x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin;
        }

        /** Whether a column is on the fence line. */
        public boolean onFence(int x, int z) {
            return contains(x, z, 0) && (x == minX || x == maxX || z == minZ || z == maxZ);
        }

        /** The gate at a column, or null. */
        public Gate gateAt(int x, int z) {
            for (Gate g : gates) {
                if (g.x() == x && g.z() == z) {
                    return g;
                }
            }
            return null;
        }

        /** Size along x and z in blocks (fence included). */
        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }

        /** The {@code n}th number in [0, 1) of this enclosure's own rolls (fixed for it everywhere). */
        public double roll(int n) {
            return unit(seed, 40 + n);
        }
    }

    private static final Enclosure NONE = new Enclosure(null, -1, 0, 0, -1, -1, 0, List.of(), 0);

    private final int cell;
    private final int fade;
    private final int gap;
    private final int roadGap;
    private final Map<Integer, List<Kind>> byBiome = new HashMap<>();
    /** Cells already worked out, per thread (worldgen threads ask about the same few cells over and over). */
    private final ThreadLocal<Cache> cache = ThreadLocal.withInitial(Cache::new);

    Enclosures(JsonObject json, List<MiddleEarthMap.Entry> biomes) {
        if (json == null) {
            this.cell = 128;
            this.fade = 0;
            this.gap = 0;
            this.roadGap = 0;
            return;
        }
        this.cell = json.get("cell").getAsInt();
        this.fade = json.get("fade").getAsInt();
        this.gap = json.get("gap").getAsInt();
        this.roadGap = json.has("road_gap") ? json.get("road_gap").getAsInt() : 8;
        if (cell <= 0 || cell % 16 != 0) {
            throw new IllegalArgumentException("enclosure cell must be a multiple of 16, not " + cell);
        }
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("biomes").entrySet()) {
            int index = -1;
            for (int i = 0; i < biomes.size(); i++) {
                if (biomes.get(i).id().equals(e.getKey())) {
                    index = i;
                }
            }
            if (index < 0) {
                throw new IllegalArgumentException("enclosures for " + e.getKey() + ", which is not in the biome table");
            }
            List<Kind> kinds = new ArrayList<>();
            for (JsonElement k : e.getValue().getAsJsonArray()) {
                JsonObject o = k.getAsJsonObject();
                List<String> variants = new ArrayList<>();
                if (o.has("variants")) {
                    o.getAsJsonArray("variants").forEach(v -> variants.add(v.getAsString()));
                } else {
                    variants.add("plain");
                }
                Map<String, Double> boost = new HashMap<>();
                if (o.has("boost")) {
                    o.getAsJsonObject("boost").entrySet().forEach(b -> boost.put(b.getKey(), b.getValue().getAsDouble()));
                }
                kinds.add(new Kind(o.get("name").getAsString(), o.get("chance").getAsDouble(),
                        o.getAsJsonArray("length").get(0).getAsInt(), o.getAsJsonArray("length").get(1).getAsInt(),
                        o.getAsJsonArray("width").get(0).getAsInt(), o.getAsJsonArray("width").get(1).getAsInt(),
                        o.get("max_relief").getAsDouble(),
                        o.has("roughness") ? o.get("roughness").getAsDouble() : 0,
                        o.has("gates") ? o.get("gates").getAsInt() : 1, List.copyOf(variants), Map.copyOf(boost)));
            }
            byBiome.put(index, List.copyOf(kinds));
        }
    }

    /** Whether any biome has enclosures. */
    boolean any() {
        return !byBiome.isEmpty();
    }

    int fade() {
        return fade;
    }

    int cell() {
        return cell;
    }

    /** The kinds of a biome (empty for none). */
    List<Kind> kinds(int biome) {
        return byBiome.getOrDefault(biome, List.of());
    }

    /** The enclosure planned in the cell that holds a column, or null. */
    Enclosure inCellOf(MiddleEarthMap map, int x, int z) {
        if (byBiome.isEmpty()) {
            return null;
        }
        Enclosure e = cell(map, Math.floorDiv(x, cell), Math.floorDiv(z, cell));
        return e == NONE ? null : e;
    }

    /** The enclosure whose fence, grown by {@code margin} (at most fade + gap), holds a column, or null. */
    Enclosure near(MiddleEarthMap map, int x, int z, int margin) {
        Enclosure e = inCellOf(map, x, z);
        return e != null && e.contains(x, z, margin) ? e : null;
    }

    /**
     * Blocks outside the fence still flattened fully: the terrain's noise samples the map's height every 4 blocks and
     * interpolates, so a slope starting at the fence leaned the plot's edge a block (GameTest, 2026-11-25).
     */
    static final int PLATEAU = 4;

    /**
     * How much a column's ground is pulled to the enclosure's level: 1 inside the fence and {@link #PLATEAU} blocks
     * past it, easing to 0 at {@code fade} blocks outside it.
     */
    double mask(Enclosure e, int x, int z) {
        int dx = Math.max(0, Math.max(e.minX() - x, x - e.maxX()));
        int dz = Math.max(0, Math.max(e.minZ() - z, z - e.maxZ()));
        double d = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (d <= PLATEAU) {
            return 1;
        }
        if (d >= fade) {
            return 0;
        }
        double t = (d - PLATEAU) / (fade - PLATEAU);
        return 1 - t * t * (3 - 2 * t);
    }

    private Enclosure cell(MiddleEarthMap map, int cx, int cz) {
        long seed = MiddleEarthMap.seed();
        Cache c = cache.get();
        if (c.seed != seed) { // a new world in the same game
            java.util.Arrays.fill(c.values, null);
            c.seed = seed;
        }
        long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
        int slot = (int) (MiddleEarthMap.mixHash(key) & (Cache.SIZE - 1));
        if (c.values[slot] != null && c.keys[slot] == key) {
            return c.values[slot];
        }
        Enclosure e = plan(map, cx, cz, seed);
        c.keys[slot] = key;
        c.values[slot] = e;
        return e;
    }

    /** Works out a cell's enclosure for a world seed from scratch (see the class comment); NONE if it has none. */
    Enclosure plan(MiddleEarthMap map, int cx, int cz, long seed) {
        long h = hash(cx, cz, seed);
        int x0 = cx * cell;
        int z0 = cz * cell;
        int biome = map.biomeIndexAt(x0 + cell / 2, z0 + cell / 2);
        List<Kind> kinds = byBiome.get(biome);
        if (kinds == null) {
            return NONE;
        }
        String cellVariant = map.variantAt(x0 + cell / 2, z0 + cell / 2);
        double r = unit(h, 0);
        Kind kind = null;
        for (Kind k : kinds) {
            r -= k.chanceIn(cellVariant == null ? "plain" : cellVariant);
            if (r < 0) {
                kind = k;
                break;
            }
        }
        if (kind == null) {
            return NONE;
        }
        int room = cell - 2 * (fade + gap);
        int length = Math.min(room, kind.minLength() + (int) (unit(h, 1) * (kind.maxLength() - kind.minLength() + 1)));
        int width = Math.min(room, kind.minWidth() + (int) (unit(h, 2) * (kind.maxWidth() - kind.minWidth() + 1)));
        if (length < 5 || width < 5) {
            return NONE;
        }
        boolean alongX = unit(h, 3) < 0.5;
        int sizeX = alongX ? length : width;
        int sizeZ = alongX ? width : length;
        int minX = x0 + fade + gap + (int) (unit(h, 4) * (room - sizeX + 1));
        int minZ = z0 + fade + gap + (int) (unit(h, 5) * (room - sizeZ + 1));
        int maxX = minX + sizeX - 1;
        int maxZ = minZ + sizeZ - 1;
        int midX = (minX + maxX) / 2;
        int midZ = (minZ + maxZ) / 2;
        // the same biome all over it and its fade: no rivers, no other biome's ground pulled flat
        int[][] edge = { { minX - fade, minZ - fade }, { maxX + fade, minZ - fade }, { minX - fade, maxZ + fade },
                { maxX + fade, maxZ + fade } };
        for (int[] p : edge) {
            if (map.biomeIndexAt(p[0], p[1]) != biome) {
                return NONE;
            }
        }
        int[][] points = { { minX, minZ }, { midX, minZ }, { maxX, minZ }, { minX, midZ }, { midX, midZ },
                { maxX, midZ }, { minX, maxZ }, { midX, maxZ }, { maxX, maxZ } };
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        double sum = 0;
        for (int[] p : points) {
            if (map.biomeIndexAt(p[0], p[1]) != biome) {
                return NONE;
            }
        }
        String variant = map.variantAt(midX, midZ);
        if (!kind.variants().contains(variant == null ? "plain" : variant)) {
            return NONE;
        }
        double halfDiagonal = Math.sqrt((double) sizeX * sizeX + (double) sizeZ * sizeZ) / 2;
        double clear = halfDiagonal + roadGap;
        if (Roads.get().distanceWithin(midX, midZ, clear + 1) <= clear) {
            return NONE;
        }
        for (int[] p : points) {
            double y = map.plainHeightAt(p[0], p[1]);
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
            sum += y;
        }
        if (hi - lo > kind.maxRelief() || lo < map.seaLevel() + 2) {
            return NONE;
        }
        double level = Math.floor(sum / points.length) + 0.5;
        List<Gate> gates = new ArrayList<>();
        int first = (int) (unit(h, 6) * 4);
        for (int i = 0; i < kind.gates(); i++) {
            int side = i == 0 ? first : i == 1 ? first ^ 1 : (int) (unit(h, 6 + 2 * i) * 4); // the 2nd: opposite side
            double along = unit(h, 7 + 2 * i);
            if (side < 2) {
                int x = minX + 2 + (int) (along * Math.max(1, sizeX - 4));
                gates.add(new Gate(Math.min(maxX - 2, x), side == 0 ? minZ : maxZ, side));
            } else {
                int z = minZ + 2 + (int) (along * Math.max(1, sizeZ - 4));
                gates.add(new Gate(side == 2 ? minX : maxX, Math.min(maxZ - 2, z), side));
            }
        }
        return new Enclosure(kind, biome, minX, minZ, maxX, maxZ, level, List.copyOf(gates), h);
    }

    private static final class Cache {
        static final int SIZE = 64;
        long seed = Long.MIN_VALUE;
        final long[] keys = new long[SIZE];
        final Enclosure[] values = new Enclosure[SIZE];
    }

    private static long hash(int cx, int cz, long seed) {
        return MiddleEarthMap.mixHash(cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL ^ 0x454E434C4FL
                ^ MiddleEarthMap.mixHash(seed ^ 0x454E43L));
    }

    private static double unit(long h, int n) {
        return (MiddleEarthMap.mixHash(h + n * 0x632BE59BD9B4E019L) >>> 11) * 0x1.0p-53;
    }
}
