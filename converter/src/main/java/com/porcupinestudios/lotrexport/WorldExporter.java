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
            root.add("roads", roads());
        } catch (Throwable t) {
            errors.add(new JsonPrimitive("world: " + t));
        }
        return root;
    }

    /** LOTR's roads: each road's name and points (map coordinates) as LOTR built them. */
    private JsonArray roads() {
        JsonArray arr = new JsonArray();
        try {
            Class<?> roads = Class.forName("lotr.common.world.map.LOTRRoads");
            Field all = roads.getDeclaredField("allRoads");
            all.setAccessible(true);
            Object list = all.get(null);
            if (list instanceof Iterable<?> it) {
                for (Object road : it) {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", String.valueOf(field(road, "roadName")));
                    // LOTR keeps a point every block or so; keep one every eighth of a map pixel (16 blocks),
                    // plus waypoints and the ends, so the export stays small.
                    JsonArray points = new JsonArray();
                    Object pts = field(road, "roadPoints");
                    int n = pts == null ? 0 : Array.getLength(pts);
                    double lastX = Double.NaN;
                    double lastZ = Double.NaN;
                    for (int i = 0; i < n; i++) {
                        Object p = Array.get(pts, i);
                        double x = ((Number) field(p, "x")).doubleValue();
                        double z = ((Number) field(p, "z")).doubleValue();
                        boolean waypoint = Boolean.TRUE.equals(field(p, "isWaypoint"));
                        // map pixels are at most a few thousand; anything larger is already in blocks
                        double spacing = Math.abs(x) > 5000 || Math.abs(z) > 5000 ? 16 : 0.125;
                        boolean far = Double.isNaN(lastX) || Math.hypot(x - lastX, z - lastZ) >= spacing;
                        if (far || waypoint || i == n - 1) {
                            JsonArray xz = new JsonArray();
                            xz.add(new JsonPrimitive(Math.round(x * 1000) / 1000.0));
                            xz.add(new JsonPrimitive(Math.round(z * 1000) / 1000.0));
                            xz.add(new JsonPrimitive(waypoint));
                            points.add(xz);
                            lastX = x;
                            lastZ = z;
                        }
                    }
                    o.addProperty("sourcePoints", n);
                    o.add("points", points);
                    arr.add(o);
                }
            }
        } catch (Throwable t) {
            errors.add(new JsonPrimitive("roads: " + t));
        }
        return arr;
    }

    /**
     * Every LOTR faction: name, colour, region, types, flags, ranks, pledge rank, map area, control zones, and the
     * default relation with every other faction.
     */
    JsonArray factions() {
        JsonArray arr = new JsonArray();
        try {
            Class<?> fac = Class.forName("lotr.common.fac.LOTRFaction");
            Class<?> relations = Class.forName("lotr.common.fac.LOTRFactionRelations");
            java.lang.reflect.Method getRelations = relations.getMethod("getRelations", fac, fac);
            Object[] all = (Object[]) fac.getMethod("values").invoke(null);
            for (Object f : all) {
                JsonObject o = new JsonObject();
                try {
                    o.addProperty("code", (String) fac.getMethod("codeName").invoke(f));
                    o.addProperty("name", (String) fac.getMethod("untranslatedFactionName").invoke(f));
                    o.addProperty("color", (Integer) fac.getMethod("getFactionColor").invoke(f));
                    o.add("fields", primitives(f));
                    Object region = field(f, "factionRegion");
                    o.addProperty("region", region == null ? null : ((Enum<?>) region).name());
                    Object dim = field(f, "factionDimension");
                    o.addProperty("dimension", dim == null ? null : ((Enum<?>) dim).name());
                    JsonArray types = new JsonArray();
                    Object typeSet = field(f, "factionTypes");
                    if (typeSet instanceof Iterable<?> it) {
                        for (Object t : it) {
                            types.add(new JsonPrimitive(((Enum<?>) t).name()));
                        }
                    }
                    o.add("types", types);
                    JsonArray ranks = new JsonArray();
                    Object rankList = field(f, "ranksSortedDescending");
                    if (rankList instanceof Iterable<?> it) {
                        for (Object r : it) {
                            ranks.add(primitives(r));
                        }
                    }
                    o.add("ranks", ranks);
                    Object pledge = field(f, "pledgeRank");
                    if (pledge != null) {
                        o.add("pledgeRank", primitives(pledge));
                    }
                    Object mapInfo = field(f, "factionMapInfo");
                    if (mapInfo != null) {
                        o.add("map", primitives(mapInfo));
                    }
                    JsonArray zones = new JsonArray();
                    Object zoneList = field(f, "controlZones");
                    if (zoneList instanceof Iterable<?> it) {
                        for (Object z : it) {
                            zones.add(primitives(z));
                        }
                    }
                    o.add("controlZones", zones);
                    JsonObject rel = new JsonObject();
                    for (Object other : all) {
                        if (other != f) {
                            Object r = getRelations.invoke(null, f, other);
                            rel.addProperty(((Enum<?>) other).name(), r == null ? null : ((Enum<?>) r).name());
                        }
                    }
                    o.add("relations", rel);
                } catch (Throwable t) {
                    errors.add(new JsonPrimitive("faction " + f + ": " + t));
                }
                arr.add(o);
            }
        } catch (Throwable t) {
            errors.add(new JsonPrimitive("factions: " + t));
        }
        return arr;
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
            Object decorator = field(b, "decorator");
            if (decorator != null) {
                JsonArray trees = new JsonArray();
                Object treeList = field(decorator, "treeTypes");
                if (treeList instanceof Iterable<?> it) {
                    for (Object t : it) {
                        JsonObject tree = new JsonObject();
                        Object type = field(t, "treeType");
                        tree.addProperty("type", type == null ? null : ((Enum<?>) type).name());
                        Object weight = field(t, "itemWeight");
                        tree.addProperty("weight", weight instanceof Number n ? n : 1);
                        trees.add(tree);
                    }
                }
                o.add("trees", trees);
                JsonArray ores = new JsonArray();
                for (String kind : new String[] { "biomeSoils", "biomeOres", "biomeGems" }) {
                    Object list = field(decorator, kind);
                    if (list instanceof Iterable<?> it) {
                        for (Object g : it) {
                            JsonObject ore = primitives(g);
                            ore.addProperty("kind", kind);
                            Object gen = field(g, "oreGen");
                            if (gen != null) {
                                ore.addProperty("genClass", gen.getClass().getName());
                                ore.add("gen", primitives(gen));
                            }
                            ores.add(ore);
                        }
                    }
                }
                o.add("ores", ores);
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
