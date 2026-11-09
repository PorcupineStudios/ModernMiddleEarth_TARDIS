package com.porcupinestudios.lotrport.world.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LOTR's roads as line segments in block coordinates, from {@code /lotr_port/map/roads.json} (written by the
 * converter), indexed in a 256-block grid so a chunk finds the few segments near it at once, and each biome's road
 * {@link Style} (LOTR's LOTRRoadType and bridge type, {@code converter/roads.py}).
 */
public final class Roads {
    private static final int CELL = 256;
    private static volatile Roads instance;

    /** One straight piece of road, from (x1, z1) to (x2, z2). */
    public record Segment(double x1, double z1, double x2, double z2) {
        /** Squared distance from a point to this segment. */
        public double distanceSq(double x, double z) {
            double dx = x2 - x1;
            double dz = z2 - z1;
            double len = dx * dx + dz * dz;
            double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((x - x1) * dx + (z - z1) * dz) / len));
            double px = x1 + t * dx - x;
            double pz = z1 + t * dz - z;
            return px * px + pz * pz;
        }
    }

    /** A block id and its chance among a road's blocks. */
    public record Pick(String block, double weight) {}

    /**
     * How a biome's roads look: weighted top blocks, the slabs laid where the road steps up, the share of road blocks
     * laid (LOTR's repair: old roads have gaps), and the bridge's deck, deck slab, edge beam and fence.
     */
    public record Style(List<Pick> top, List<Pick> slab, double repair, String deck, String deckSlab, String edge,
                        String fence) {}

    private final Map<String, Style> styles = new HashMap<>();
    private final Map<Long, List<Segment>> cells = new HashMap<>();
    private final List<Segment> all = new ArrayList<>();
    private final int count;
    private final double width;

    Roads(JsonObject json) {
        this.width = json.get("width").getAsDouble();
        int n = 0;
        for (JsonElement road : json.getAsJsonArray("roads")) {
            JsonArray points = road.getAsJsonObject().getAsJsonArray("points");
            for (int i = 1; i < points.size(); i++) {
                JsonArray a = points.get(i - 1).getAsJsonArray();
                JsonArray b = points.get(i).getAsJsonArray();
                add(new Segment(a.get(0).getAsDouble(), a.get(1).getAsDouble(), b.get(0).getAsDouble(),
                        b.get(1).getAsDouble()));
                n++;
            }
        }
        this.count = n;
        JsonObject types = json.getAsJsonObject("types");
        JsonObject bridges = json.getAsJsonObject("bridges");
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("biomes").entrySet()) {
            JsonObject b = e.getValue().getAsJsonObject();
            JsonObject type = types.getAsJsonObject(b.get("road").getAsString());
            JsonObject bridge = bridges.getAsJsonObject(b.get("bridge").getAsString());
            styles.put(e.getKey(), new Style(picks(type.getAsJsonArray("top")), picks(type.getAsJsonArray("slab")),
                    b.get("repair").getAsDouble(), bridge.get("deck").getAsString(),
                    bridge.get("slab").getAsString(), bridge.get("edge").getAsString(), bridge.get("fence").getAsString()));
        }
    }

    private static List<Pick> picks(JsonArray array) {
        List<Pick> out = new ArrayList<>();
        for (JsonElement p : array) {
            out.add(new Pick(p.getAsJsonObject().get("block").getAsString(),
                    p.getAsJsonObject().get("weight").getAsDouble()));
        }
        return out;
    }

    /** The road style of a biome ({@code lotr_port:shire}), or null for a biome LOTR has none for. */
    public Style style(String biome) {
        return styles.get(biome);
    }

    public static Roads get() {
        Roads roads = instance;
        if (roads == null) {
            synchronized (Roads.class) {
                roads = instance;
                if (roads == null) {
                    roads = load();
                    instance = roads;
                }
            }
        }
        return roads;
    }

    private static Roads load() {
        try (InputStream in = Roads.class.getResourceAsStream("/lotr_port/map/roads.json")) {
            if (in == null) {
                throw new IOException("missing /lotr_port/map/roads.json");
            }
            return new Roads(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the roads", e);
        }
    }

    private void add(Segment s) {
        all.add(s);
        double pad = width + 1;
        int cx0 = Math.floorDiv((int) Math.floor(Math.min(s.x1(), s.x2()) - pad), CELL);
        int cx1 = Math.floorDiv((int) Math.ceil(Math.max(s.x1(), s.x2()) + pad), CELL);
        int cz0 = Math.floorDiv((int) Math.floor(Math.min(s.z1(), s.z2()) - pad), CELL);
        int cz1 = Math.floorDiv((int) Math.ceil(Math.max(s.z1(), s.z2()) + pad), CELL);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                cells.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(s);
            }
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    public List<Segment> segments() {
        return all;
    }

    public int segmentCount() {
        return count;
    }

    /** Half the road's width in blocks. */
    public double width() {
        return width;
    }

    /** The segments that may pass within the road width of a block column. */
    public List<Segment> near(int blockX, int blockZ) {
        return cells.getOrDefault(key(Math.floorDiv(blockX, CELL), Math.floorDiv(blockZ, CELL)), List.of());
    }

    /** Distance from a block column's centre to the nearest road, or infinity if none is near. */
    public double distance(int blockX, int blockZ) {
        double best = Double.POSITIVE_INFINITY;
        for (Segment s : near(blockX, blockZ)) {
            best = Math.min(best, s.distanceSq(blockX + 0.5, blockZ + 0.5));
        }
        return Math.sqrt(best);
    }
}
