package com.porcupinestudios.lotrport.world.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lairs: small, seeded places a creature lives in, each with a core and a wider infested ground round it that is a
 * biome of its own (Aaron 2026-11-25: Mirkwood's spider nests "relatively small only about a 200 sq/blocks of actual
 * spider nests, and with spiders spawning within a larger radius around it (with spawning being affected by destroying
 * nests)", and seeded "so there's randomness on new worlds").
 *
 * <p>Per kind, the land is cut into {@code cell}-block cells (a multiple of 16); each holds a lair with chance
 * {@code chance}, at a spot drawn from the world seed and the cell, kept inside the cell by its whole reach, so a column
 * only looks at its own cell. A lair is kept only where the land (map biome) at its middle and round its core is one
 * of {@code on}, and inside one of {@code regions} if any are named (the region layer: the enchanted forest). Its core
 * ({@code core} blocks round the middle; 8 is ~200 blocks square) is built by a feature ({@code lotr_port:spider_nest}),
 * with {@code sacs} egg sacs at fixed spots; the ground within {@code radius} (its edge ragged by {@code ragged}) is
 * {@code biome}, whose spawn table brings the creatures, scaled by how much of the lair is left
 * ({@code lair/LairLife}).
 *
 * <p>map.json: {@code "lairs": {"mirkwood_spider_nests": {"biome": "lotr_port:mirkwood_spider_nests", "on":
 * ["lotr_port:mirkwood_enchanted"], "regions": ["mirkwood_enchanted"], "cell": 512, "chance": 0.3, "core": 8,
 * "radius": 56, "ragged": 0.2, "sacs": 4}}}.
 */
public final class Lairs {
    /** One kind of lair. */
    public record Kind(String name, int index, int biome, boolean[] on, List<String> regions, int cell, double chance,
            int core, int radius, double ragged, int sacs) {
        /** The farthest the infested ground reaches from the middle. */
        int reach() {
            return (int) Math.ceil(radius * (1 + ragged)) + 1;
        }
    }

    /** One lair: its kind, middle, the cell it was planned in and a number its own rolls come from. */
    public record Lair(Kind kind, int x, int z, int cellX, int cellZ, long seed) {
        /** A number naming this lair in this world (saved with what was done to it). */
        public long id() {
            return MiddleEarthMap.mixHash((long) cellX << 32 ^ (cellZ & 0xFFFFFFFFL) ^ (long) kind.index() << 58);
        }

        public double distance(int bx, int bz) {
            double dx = bx + 0.5 - (x + 0.5);
            double dz = bz + 0.5 - (z + 0.5);
            return Math.sqrt(dx * dx + dz * dz);
        }

        /** Whether a column is in the core (the nest itself). */
        public boolean inCore(int bx, int bz) {
            return distance(bx, bz) <= kind.core();
        }

        /** Whether a column is on the lair's infested ground (its edge ragged by three waves round it). */
        public boolean inRange(int bx, int bz) {
            double d = distance(bx, bz);
            if (d > kind.reach()) {
                return false;
            }
            double a = Math.atan2(bz - z, bx - x);
            double wave = 0;
            for (int k = 2; k <= 4; k++) {
                wave += Math.sin(k * a + unit(seed, 30 + k) * Math.PI * 2) / k;
            }
            return d <= kind.radius() * (1 + kind.ragged() * wave / 1.1);
        }

        /** The columns of its egg sacs, as {x, z}: spread round the middle inside the core. */
        public List<int[]> sacs() {
            List<int[]> out = new ArrayList<>();
            int n = kind.sacs();
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * (i + 0.6 * unit(seed, 10 + i)) / n;
                double d = kind.core() * (0.25 + 0.45 * unit(seed, 20 + i));
                out.add(new int[] { x + (int) Math.round(Math.cos(a) * d), z + (int) Math.round(Math.sin(a) * d) });
            }
            return out;
        }

        /** The {@code n}th number in [0, 1) of this lair's own rolls. */
        public double roll(int n) {
            return unit(seed, 60 + n);
        }
    }

    private static final Lair NONE = new Lair(null, 0, 0, 0, 0, 0);

    private final List<Kind> kinds = new ArrayList<>();
    /** Per map biome: whether any kind may lie on it (the hot check in {@link MiddleEarthMap#biomeIndexAt}). */
    private final boolean[] onAny = new boolean[256];
    private final ThreadLocal<Cache> cache = ThreadLocal.withInitial(Cache::new);

    Lairs(JsonObject json, List<MiddleEarthMap.Entry> biomes) {
        if (json == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            int cell = o.get("cell").getAsInt();
            if (cell <= 0 || cell % 16 != 0) {
                throw new IllegalArgumentException("lair cell must be a multiple of 16, not " + cell);
            }
            boolean[] on = new boolean[256];
            for (JsonElement b : o.getAsJsonArray("on")) {
                on[index(biomes, b.getAsString())] = true;
            }
            List<String> regions = new ArrayList<>();
            if (o.has("regions")) {
                o.getAsJsonArray("regions").forEach(r -> regions.add(r.getAsString()));
            }
            Kind kind = new Kind(e.getKey(), kinds.size(), index(biomes, o.get("biome").getAsString()), on,
                    List.copyOf(regions), cell, o.get("chance").getAsDouble(), o.get("core").getAsInt(),
                    o.get("radius").getAsInt(), o.has("ragged") ? o.get("ragged").getAsDouble() : 0,
                    o.has("sacs") ? o.get("sacs").getAsInt() : 0);
            if (2 * kind.reach() >= cell) {
                throw new IllegalArgumentException("lair " + kind.name() + " reaches past its cell");
            }
            kinds.add(kind);
            for (int i = 0; i < 256; i++) {
                onAny[i] |= on[i];
            }
        }
    }

    private static int index(List<MiddleEarthMap.Entry> biomes, String id) {
        for (int i = 0; i < biomes.size(); i++) {
            if (biomes.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalArgumentException("lair biome " + id + " is not in the biome table");
    }

    /** Whether a lair may lie on this map biome. */
    boolean on(int land) {
        return onAny[land];
    }

    public List<Kind> kinds() {
        return List.copyOf(kinds);
    }

    /** The lair whose infested ground holds a column standing on land {@code land}, or null. */
    Lair at(MiddleEarthMap map, int x, int z, int land) {
        for (Kind k : kinds) {
            if (!k.on()[land]) {
                continue;
            }
            Lair l = cell(map, k, Math.floorDiv(x, k.cell()), Math.floorDiv(z, k.cell()));
            if (l != NONE && l.inRange(x, z)) {
                return l;
            }
        }
        return null;
    }

    /** The lairs planned in the cells holding a column, one per kind that has one there. */
    List<Lair> inCellsOf(MiddleEarthMap map, int x, int z) {
        List<Lair> out = new ArrayList<>(1);
        for (Kind k : kinds) {
            Lair l = cell(map, k, Math.floorDiv(x, k.cell()), Math.floorDiv(z, k.cell()));
            if (l != NONE) {
                out.add(l);
            }
        }
        return out;
    }

    private Lair cell(MiddleEarthMap map, Kind kind, int cx, int cz) {
        long seed = MiddleEarthMap.seed();
        Cache c = cache.get();
        if (c.seed != seed) {
            java.util.Arrays.fill(c.values, null);
            c.seed = seed;
        }
        long key = MiddleEarthMap.mixHash(((long) cx << 32 | (cz & 0xFFFFFFFFL)) + kind.index());
        int slot = (int) (key & (Cache.SIZE - 1));
        if (c.values[slot] != null && c.keys[slot] == key) {
            return c.values[slot];
        }
        Lair l = plan(map, kind, cx, cz, seed);
        c.keys[slot] = key;
        c.values[slot] = l == null ? NONE : l;
        return c.values[slot];
    }

    /** Works out a cell's lair for a world seed from scratch; null if it has none. */
    Lair plan(MiddleEarthMap map, Kind kind, int cx, int cz, long seed) {
        long h = MiddleEarthMap.mixHash(cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL
                ^ (kind.index() + 1) * 0x4C414952L ^ MiddleEarthMap.mixHash(seed ^ 0x4C41495253L));
        if (unit(h, 0) >= kind.chance()) {
            return null;
        }
        int margin = kind.reach();
        int x = cx * kind.cell() + margin + (int) (unit(h, 1) * (kind.cell() - 2 * margin));
        int z = cz * kind.cell() + margin + (int) (unit(h, 2) * (kind.cell() - 2 * margin));
        // the forest all round its core, and the middle in an allowed region
        int[][] points = { { 0, 0 }, { kind.core(), 0 }, { -kind.core(), 0 }, { 0, kind.core() }, { 0, -kind.core() } };
        for (int[] p : points) {
            if (!kind.on()[map.landIndexAt(x + p[0], z + p[1])]) {
                return null;
            }
        }
        if (!kind.regions().isEmpty() && kind.regions().stream().noneMatch(r -> map.inRegion(r, x, z))) {
            return null;
        }
        return new Lair(kind, x, z, cx, cz, h);
    }

    private static final class Cache {
        static final int SIZE = 64;
        long seed = Long.MIN_VALUE;
        final long[] keys = new long[SIZE];
        final Lair[] values = new Lair[SIZE];
    }

    private static double unit(long h, int n) {
        return (MiddleEarthMap.mixHash(h + n * 0x632BE59BD9B4E019L) >>> 11) * 0x1.0p-53;
    }
}
