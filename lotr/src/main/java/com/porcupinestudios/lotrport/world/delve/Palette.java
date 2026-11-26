package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/**
 * The blocks a delve style builds with, by role ({@code wall}, {@code floor}, {@code pillar}, {@code rubble}...):
 * each role a weighted list of blocks. An entry's {@code decay} (-1..1) ties its weight to the delve's ruin
 * ({@link DelveSpec#decay}): 1 = only as ruined as the delve (cracked bricks), -1 = only as intact (lit lamps),
 * 0 = always. {@code "keep"} places nothing, {@code "minecraft:air"} clears. A palette may {@code extends} another and
 * replace some of its roles.
 */
public final class Palette {
    private record Entry(Mat mat, double weight) {}

    private final String name;
    private final Map<String, Entry[]> roles = new HashMap<>();
    private final Map<String, Double> totals = new HashMap<>();

    Palette(String name, JsonObject json, Palette parent, double decay) {
        this.name = name;
        if (parent != null) {
            roles.putAll(parent.roles);
            totals.putAll(parent.totals);
        }
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (e.getKey().equals("extends")) {
                continue;
            }
            var list = e.getValue().getAsJsonArray();
            Entry[] entries = new Entry[list.size()];
            double total = 0;
            for (int i = 0; i < entries.length; i++) {
                JsonElement el = list.get(i);
                Mat mat;
                double w = 1;
                double d = 0;
                if (el.isJsonPrimitive()) {
                    mat = Mat.of(el.getAsString());
                } else {
                    JsonObject o = el.getAsJsonObject();
                    mat = Mat.of(J.s(o, "block"), o.has("loot") ? o.get("loot").getAsString() : null);
                    w = o.has("weight") ? o.get("weight").getAsDouble() : 1;
                    d = o.has("decay") ? o.get("decay").getAsDouble() : 0;
                }
                double eff = d >= 0 ? w * (1 - d * (1 - decay)) : w * (1 + d * decay);
                entries[i] = new Entry(mat, Math.max(0, eff));
                total += entries[i].weight;
            }
            roles.put(e.getKey(), entries);
            totals.put(e.getKey(), total);
        }
    }

    /** Every block any role may place, with its loot table (for checks). */
    public java.util.List<Mat> mats() {
        java.util.List<Mat> out = new java.util.ArrayList<>();
        for (Entry[] es : roles.values()) {
            for (Entry e : es) {
                out.add(e.mat);
            }
        }
        return out;
    }

    public boolean has(String role) {
        return roles.containsKey(role);
    }

    /** A block for the role picked by the hash; {@link Mat#KEEP} if the role has nothing left at this ruin level. */
    public Mat pick(String role, long hash) {
        Entry[] entries = roles.get(role);
        if (entries == null) {
            throw new IllegalArgumentException("delve palette " + name + " has no role \"" + role + "\"");
        }
        double total = totals.get(role);
        if (total <= 0) {
            return Mat.KEEP;
        }
        double r = Rng.unit(hash) * total;
        for (Entry e : entries) {
            r -= e.weight;
            if (r < 0) {
                return e.mat;
            }
        }
        return entries[entries.length - 1].mat;
    }

    @Override
    public String toString() {
        return name;
    }
}
