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
import net.minecraft.world.phys.AABB;

/**
 * The Capaldi console room's models from {@code assets/tardis_rb/console/capaldi.json} (made by
 * {@code tools/capaldi.py}): quads in blocks from the console block's lower corner. Parts: {@code body} (the console
 * and its pedestal down to the lower deck), one {@code ctl_<name>} per control, {@code rotor_glass},
 * {@code rotor_column}, {@code rotor_spinners}, {@code rotor_spinner_mid} and {@code arches}. Each control has a click
 * box. Loaded on first use and again after a resource reload, so a resource pack can replace it.
 */
public final class ConsoleModel {
    private static final ResourceLocation FILE = TardisRB.id("console/capaldi.json");
    private static ConsoleModel loaded;

    /** One quad: four vertices of x y z u v, and its normal. */
    public record Quad(float[][] v, float[] n) {}

    /** A control on the model: its kind (lever, slider, dial...), its panel 0-5 and where it can be clicked. */
    public record ControlPart(String kind, int panel, AABB box) {}

    public final Map<String, List<Quad>> parts = new HashMap<>();
    public final Map<String, ControlPart> controls = new HashMap<>();
    /** The time rotor's axis (x, z) from the block's corner. */
    public final float pivotX;
    public final float pivotZ;

    private ConsoleModel(JsonObject root) {
        JsonObject ps = root.has("parts") ? root.getAsJsonObject("parts") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : ps.entrySet()) {
            List<Quad> quads = new ArrayList<>();
            for (JsonElement q : e.getValue().getAsJsonArray()) {
                JsonArray verts = q.getAsJsonObject().getAsJsonArray("v");
                float[][] v = new float[4][];
                for (int i = 0; i < 4; i++) {
                    v[i] = floats(verts.get(i).getAsJsonArray());
                }
                quads.add(new Quad(v, floats(q.getAsJsonObject().getAsJsonArray("n"))));
            }
            parts.put(e.getKey(), quads);
        }
        JsonObject cs = root.has("controls") ? root.getAsJsonObject("controls") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : cs.entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();
            float[] b = floats(c.getAsJsonArray("box"));
            controls.put(e.getKey(), new ControlPart(c.get("kind").getAsString(), c.get("panel").getAsInt(),
                    new AABB(b[0], b[1], b[2], b[3], b[4], b[5])));
        }
        float[] pivot = root.has("pivot") ? floats(root.getAsJsonArray("pivot")) : new float[] {0.5F, 0, 0.5F};
        pivotX = pivot[0];
        pivotZ = pivot[2];
    }

    public List<Quad> part(String name) {
        return parts.getOrDefault(name, List.of());
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    /** The models, loading them if needed; empty if the file is missing or broken (logged once). */
    public static ConsoleModel get() {
        if (loaded == null) {
            try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(FILE)) {
                loaded = new ConsoleModel(JsonParser.parseReader(r).getAsJsonObject());
            } catch (Exception e) {
                TardisRB.LOG.error("Could not read the console model {}", FILE, e);
                loaded = new ConsoleModel(new JsonObject());
            }
        }
        return loaded;
    }

    /** Forgets the models so the next frame reads them again (after a resource reload). */
    public static void reset() {
        loaded = null;
    }
}
