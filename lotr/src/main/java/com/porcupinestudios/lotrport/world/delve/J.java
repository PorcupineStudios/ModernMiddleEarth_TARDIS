package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Required-field readers for delve data: a missing number is an error naming the field, never a silent default. */
final class J {
    private J() {}

    static JsonElement req(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) {
            throw new IllegalArgumentException("delve data: missing \"" + k + "\" in " + brief(o));
        }
        return e;
    }

    static int i(JsonObject o, String k) {
        return req(o, k).getAsInt();
    }

    static double d(JsonObject o, String k) {
        return req(o, k).getAsDouble();
    }

    static String s(JsonObject o, String k) {
        return req(o, k).getAsString();
    }

    static boolean b(JsonObject o, String k) {
        return req(o, k).getAsBoolean();
    }

    static JsonObject o(JsonObject o, String k) {
        return req(o, k).getAsJsonObject();
    }

    static JsonArray a(JsonObject o, String k) {
        return req(o, k).getAsJsonArray();
    }

    /** {@code [lo, hi]} (or one number for both). */
    static int[] range(JsonObject o, String k) {
        JsonElement e = req(o, k);
        if (e.isJsonPrimitive()) {
            int v = e.getAsInt();
            return new int[] {v, v};
        }
        JsonArray a = e.getAsJsonArray();
        return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()};
    }

    static int[] ints(JsonArray a) {
        int[] r = new int[a.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = a.get(i).getAsInt();
        }
        return r;
    }

    private static String brief(JsonObject o) {
        String s = o.toString();
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }
}
