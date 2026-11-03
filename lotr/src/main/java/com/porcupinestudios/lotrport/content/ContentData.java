package com.porcupinestudios.lotrport.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the block, item and creative tab lists the converter generated ({@code converter/generate.py}) into
 * {@code lotr_port/content/} in the jar. They are read once at startup, before any registry opens, so they are plain
 * files in the jar rather than datapack data.
 */
public final class ContentData {
    private static final String ROOT = "/lotr_port/content/";

    private ContentData() {}

    public static List<JsonObject> read(String file) {
        List<JsonObject> out = new ArrayList<>();
        try (InputStream in = ContentData.class.getResourceAsStream(ROOT + file)) {
            if (in == null) {
                return out;
            }
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement e : arr) {
                out.add(e.getAsJsonObject());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read generated content " + file, e);
        }
        return out;
    }

    static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }

    static float num(JsonObject o, String key, float fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsFloat() : fallback;
    }

    static int integer(JsonObject o, String key, int fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : fallback;
    }

    static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsBoolean() : fallback;
    }
}
