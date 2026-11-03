package com.porcupinestudios.lotrexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import net.minecraft.block.Block;

/**
 * Writes the Middle-earth world data: every biome's numbers (heights, climate, surface blocks, colours, decorator
 * counts) and the map settings (colour to biome table, scale, origin). Reads runtime state through reflection only;
 * the map image itself is taken from the jar by the generator.
 */
final class WorldExporter {
    private final JsonArray errors;

    WorldExporter(JsonArray errors) {
        this.errors = errors;
    }

    JsonObject world() {
        JsonObject root = new JsonObject();
        try {
            Class<?> dimClass = Class.forName("lotr.common.LOTRDimension");
            Object me = dimClass.getField("MIDDLE_EARTH").get(null);
            Object list = dimClass.getField("biomeList").get(me);
            JsonArray biomes = new JsonArray();
            for (int i = 0; i < Array.getLength(list); i++) {
                Object b = Array.get(list, i);
                if (b != null) {
                    biomes.add(biome(b));
                }
            }
            root.add("biomes", biomes);

            JsonObject colors = new JsonObject();
            Map<?, ?> map = (Map<?, ?>) dimClass.getField("colorsToBiomeIDs").get(me);
            for (Map.Entry<?, ?> e : map.entrySet()) {
                int rgb = ((Number) e.getKey()).intValue() & 0xFFFFFF;
                colors.addProperty(String.format("%06x", rgb), ((Number) e.getValue()).intValue());
            }
            root.add("colors", colors);

            Class<?> world = Class.forName("lotr.common.world.genlayer.LOTRGenLayerWorld");
            JsonObject settings = new JsonObject();
            for (String name : new String[] { "scalePower", "originX", "originZ", "scale", "imageWidth",
                    "imageHeight" }) {
                try {
                    Field f = world.getDeclaredField(name);
                    f.setAccessible(true);
                    settings.addProperty(name, (Number) f.get(null));
                } catch (Throwable t) {
                    errors.add(new JsonPrimitive("map setting " + name + ": " + t));
                }
            }
            root.add("map", settings);
        } catch (Throwable t) {
            errors.add(new JsonPrimitive("world: " + t));
        }
        return root;
    }

    private JsonObject biome(Object b) {
        JsonObject o = new JsonObject();
        o.addProperty("class", b.getClass().getName());
        try {
            o.add("fields", primitives(b));
            for (String name : new String[] { "biomeColors", "biomeTerrain", "decorator" }) {
                Object part = field(b, name);
                if (part != null) {
                    o.add(name, primitives(part));
                }
            }
        } catch (Throwable t) {
            errors.add(new JsonPrimitive("biome " + b + ": " + t));
        }
        return o;
    }

    /** Numbers, booleans, strings, enums and blocks (as registry names) declared on the object's classes. */
    private static JsonObject primitives(Object obj) {
        JsonObject o = new JsonObject();
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || o.has(f.getName())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    JsonElement v = value(f.get(obj));
                    if (v != null) {
                        o.add(f.getName(), v);
                    }
                } catch (Throwable ignored) {
                    // unreadable field: leave it out
                }
            }
        }
        return o;
    }

    private static JsonElement value(Object v) {
        if (v instanceof Number n) {
            return new JsonPrimitive(n);
        }
        if (v instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (v instanceof String s) {
            return new JsonPrimitive(s);
        }
        if (v instanceof Enum<?> e) {
            return new JsonPrimitive(e.name());
        }
        if (v instanceof Block block) {
            return new JsonPrimitive(String.valueOf(Block.blockRegistry.getNameForObject(block)));
        }
        if (v instanceof java.awt.Color color) {
            return new JsonPrimitive(color.getRGB() & 0xFFFFFF);
        }
        return null;
    }

    private static Object field(Object obj, String name) {
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                // look in the superclass
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
