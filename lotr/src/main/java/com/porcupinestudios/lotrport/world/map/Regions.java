package com.porcupinestudios.lotrport.world.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The region layer (Aaron 2026-11-25: an "allowed biomes" map "that adheres on top of the geographical map of
 * middle earth (backend only)", for the enchanted forest of Mirkwood and later the Mines of Moria and Goblin-town):
 * named areas drawn over LOTR's map, on the same pixel grid (128 blocks a pixel). A pixel can be in any number of
 * regions (the spider nests lie inside the enchanted forest). Players never see them.
 *
 * <p>What reads them: map.json {@code overlays} paint a sub-biome over chosen biomes inside a region (so Enchanted
 * Mirkwood only ever appears in northern Mirkwood, its spider nests only inside it); the placement filter
 * {@code lotr_port:in_region} keeps a feature in or out of regions; {@link MiddleEarthMap#inRegion} answers code
 * (future underground biomes: Moria under the Misty Mountains between its two gates, Goblin-town under the High
 * Pass). Regions are drawn in {@code converter/regions.py} and written to {@code /lotr_port/map/regions.json}:
 * {@code {"regions": [{"name": "moria", "x": 1120, "z": 836, "width": 72, "height": 58, "rows": [[3, 12, 20, 4],
 * ...]}]}}, each row a list of (start, length) runs from the region's left edge.
 */
public final class Regions {
    /** One region: its pixel bounding box and a bit per pixel inside it. */
    record Region(String name, int x, int z, int width, int height, long[] bits) {
        boolean contains(int px, int pz) {
            int dx = px - x;
            int dz = pz - z;
            if (dx < 0 || dz < 0 || dx >= width || dz >= height) {
                return false;
            }
            int i = dz * width + dx;
            return (bits[i >>> 6] & 1L << (i & 63)) != 0;
        }

        int pixels() {
            int n = 0;
            for (long b : bits) {
                n += Long.bitCount(b);
            }
            return n;
        }
    }

    static final Regions EMPTY = new Regions(List.of());

    private final List<Region> list;
    private final Map<String, Region> byName = new LinkedHashMap<>();

    private Regions(List<Region> list) {
        this.list = List.copyOf(list);
        for (Region r : list) {
            byName.put(r.name(), r);
        }
    }

    /** Reads regions.json; null gives no regions. */
    static Regions read(JsonObject json) {
        if (json == null || !json.has("regions")) {
            return EMPTY;
        }
        List<Region> out = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("regions")) {
            JsonObject o = e.getAsJsonObject();
            int width = o.get("width").getAsInt();
            int height = o.get("height").getAsInt();
            long[] bits = new long[(width * height + 63) >>> 6];
            JsonArray rows = o.getAsJsonArray("rows");
            for (int dz = 0; dz < rows.size() && dz < height; dz++) {
                JsonArray runs = rows.get(dz).getAsJsonArray();
                for (int k = 0; k + 1 < runs.size(); k += 2) {
                    int start = runs.get(k).getAsInt();
                    int length = runs.get(k + 1).getAsInt();
                    for (int dx = Math.max(0, start); dx < Math.min(width, start + length); dx++) {
                        int i = dz * width + dx;
                        bits[i >>> 6] |= 1L << (i & 63);
                    }
                }
            }
            out.add(new Region(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("z").getAsInt(), width,
                    height, bits));
        }
        return new Regions(out);
    }

    /** The named region, or null. */
    Region region(String name) {
        return byName.get(name);
    }

    /** Whether a map pixel is in the named region (false for a name with no region). */
    public boolean contains(String name, int px, int pz) {
        Region r = byName.get(name);
        return r != null && r.contains(px, pz);
    }

    /** The names of the regions a map pixel is in, in file order. */
    public List<String> at(int px, int pz) {
        List<String> out = null;
        for (Region r : list) {
            if (r.contains(px, pz)) {
                if (out == null) {
                    out = new ArrayList<>(2);
                }
                out.add(r.name());
            }
        }
        return out == null ? List.of() : out;
    }

    /** Every region's name. */
    public List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(byName.keySet()));
    }

    /** How many map pixels a region covers (0 for none). */
    public int pixels(String name) {
        Region r = byName.get(name);
        return r == null ? 0 : r.pixels();
    }
}
