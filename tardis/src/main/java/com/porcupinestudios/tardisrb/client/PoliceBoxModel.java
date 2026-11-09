package com.porcupinestudios.tardisrb.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.porcupinestudios.tardisrb.TardisRB;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The police box mesh from {@code assets/tardis_rb/police_box/model.json} (made from Aaron's Blockbench model by
 * {@code tools/police_box.py}): triangles in blocks, front (doors) toward -z, centred on x/z 0, floor at y 0. Loaded on
 * first use and again after a resource reload, so a resource pack can replace it.
 */
public final class PoliceBoxModel {
    private static final ResourceLocation FILE = TardisRB.id("police_box/model.json");
    private static PoliceBoxModel loaded;

    /** One triangle: three vertices of x y z u v, and its normal. */
    public record Tri(float[][] v, float[] n) {}

    /** A part of the box; doors turn about their {@code hinge} (a vertical line through it). */
    public record Part(List<Tri> tris, float[] hinge, int openSign) {}

    public final Part body;
    public final Map<String, Part> doors = new HashMap<>();
    public final int glowFromLook;
    public final float swingDegrees;
    public final float swingTicks;
    /** The body mesh's bounds (x y z), for effects drawn over it. */
    public final float[] min = {0, 0, 0};
    public final float[] max = {0, 0, 0};

    private PoliceBoxModel(JsonObject root) {
        JsonObject parts = root.getAsJsonObject("parts");
        JsonObject signs = root.getAsJsonObject("door_open_sign");
        Part b = null;
        for (Map.Entry<String, JsonElement> e : parts.entrySet()) {
            JsonObject p = e.getValue().getAsJsonObject();
            List<Tri> tris = new ArrayList<>();
            for (JsonElement t : p.getAsJsonArray("tris")) {
                JsonArray verts = t.getAsJsonObject().getAsJsonArray("v");
                float[][] v = new float[3][];
                for (int i = 0; i < 3; i++) {
                    v[i] = floats(verts.get(i).getAsJsonArray());
                }
                tris.add(new Tri(v, floats(t.getAsJsonObject().getAsJsonArray("n"))));
            }
            float[] hinge = p.has("hinge") ? floats(p.getAsJsonArray("hinge")) : new float[3];
            int sign = signs != null && signs.has(e.getKey()) ? signs.get(e.getKey()).getAsInt() : 1;
            Part part = new Part(tris, hinge, sign);
            if (e.getKey().equals("body")) {
                b = part;
            } else {
                doors.put(e.getKey(), part);
            }
        }
        body = b == null ? new Part(List.of(), new float[3], 1) : b;
        boolean first = true;
        for (Tri t : body.tris()) {
            for (float[] v : t.v()) {
                for (int i = 0; i < 3; i++) {
                    min[i] = first ? v[i] : Math.min(min[i], v[i]);
                    max[i] = first ? v[i] : Math.max(max[i], v[i]);
                }
                first = false;
            }
        }
        glowFromLook = root.has("glow_from_look") ? root.get("glow_from_look").getAsInt() : 0;
        JsonObject swing = root.getAsJsonObject("door_swing");
        swingDegrees = swing == null ? 90 : swing.get("degrees").getAsFloat();
        swingTicks = swing == null ? 8 : Math.max(1, swing.get("ticks").getAsFloat());
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    /** The mesh, loading it if needed; null if the file is missing or broken (logged once). */
    public static PoliceBoxModel get() {
        if (loaded == null) {
            try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(FILE)) {
                loaded = new PoliceBoxModel(JsonParser.parseReader(r).getAsJsonObject());
            } catch (Exception e) {
                TardisRB.LOG.error("Could not read the police box model {}", FILE, e);
                loaded = new PoliceBoxModel(JsonParser.parseString("{\"parts\":{}}").getAsJsonObject());
            }
        }
        return loaded;
    }

    /** Forgets the mesh so the next frame reads it again (after a resource reload). */
    public static void reset() {
        loaded = null;
    }
}
