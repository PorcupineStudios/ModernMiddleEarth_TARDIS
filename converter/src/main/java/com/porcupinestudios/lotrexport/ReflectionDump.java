package com.porcupinestudios.lotrexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

/**
 * A generic dump of LOTR's static tables by reflection: every static field of the named classes, with objects
 * expanded a few levels (enums by name plus their fields, items and blocks by registry name, potions by name, lists,
 * arrays and maps element by element). Writes extra.json for the generator (achievements, titles, enchantments,
 * shields, capes, invasions, the calendar, drinks, quest factories...). Public game state only; no code copied.
 */
class ReflectionDump {
    static final String[] CLASSES = { "lotr.common.enchant.LOTREnchantment", "lotr.common.LOTRAchievement",
            "lotr.common.LOTRAchievement$Category", "lotr.common.LOTRTitle", "lotr.common.LOTRShields",
            "lotr.common.LOTRCapes", "lotr.common.world.spawning.LOTRInvasions", "lotr.common.LOTRDate",
            "lotr.common.LOTRDate$ShireReckoning", "lotr.common.LOTRDate$ShireReckoning$Month",
            "lotr.common.LOTRDate$ShireReckoning$Day", "lotr.common.LOTRDate$Season", "lotr.common.item.LOTRItemMug",
            "lotr.common.item.LOTRItemMug$Vessel", "lotr.common.quest.LOTRMiniQuestFactory",
            "lotr.common.world.spawning.LOTRBiomeInvasionSpawns" };
    private final Exporter ex;
    private final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();

    ReflectionDump(Exporter ex) {
        this.ex = ex;
    }

    JsonObject dump() {
        JsonObject out = new JsonObject();
        for (String name : CLASSES) {
            try {
                Class<?> c = Class.forName(name);
                JsonObject cls = new JsonObject();
                for (Field f : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        seen.clear();
                        cls.add(f.getName(), value(f.get(null), 3));
                    } catch (Throwable t) {
                        cls.addProperty(f.getName(), "!" + t.getClass().getSimpleName());
                    }
                }
                out.add(name, cls);
            } catch (Throwable t) {
                ex.report("dump " + name, t);
            }
        }
        out.add("mugs", mugs());
        return out;
    }

    /** Every drink item's own fields (heal, strength, effects, vessels...). */
    private JsonArray mugs() {
        JsonArray a = new JsonArray();
        try {
            Class<?> mug = Class.forName("lotr.common.item.LOTRItemMug");
            for (Object o : Item.itemRegistry) {
                if (mug.isInstance(o)) {
                    seen.clear();
                    JsonObject j = object(o, 2, true);
                    j.addProperty("id", String.valueOf(Item.itemRegistry.getNameForObject(o)));
                    a.add(j);
                }
            }
        } catch (Throwable t) {
            ex.report("dump mugs", t);
        }
        return a;
    }

    private JsonElement value(Object v, int depth) {
        if (v == null) {
            return JsonNull.INSTANCE;
        }
        if (v instanceof Number n) {
            return new JsonPrimitive(n);
        }
        if (v instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (v instanceof String || v instanceof Character || v instanceof ResourceLocation || v instanceof Class) {
            return new JsonPrimitive(v instanceof Class<?> c ? c.getName() : v.toString());
        }
        if (v instanceof Item i) {
            return new JsonPrimitive("item:" + Item.itemRegistry.getNameForObject(i));
        }
        if (v instanceof Block b) {
            return new JsonPrimitive("block:" + Block.blockRegistry.getNameForObject(b));
        }
        if (v instanceof ItemStack s) {
            return ex.stackJson(s);
        }
        if (v instanceof Potion p) {
            return new JsonPrimitive("potion:" + p.getName());
        }
        if (v instanceof PotionEffect e) {
            JsonObject o = new JsonObject();
            o.addProperty("potion", Potion.potionTypes[e.getPotionID()].getName());
            o.addProperty("duration", e.getDuration());
            o.addProperty("amplifier", e.getAmplifier());
            return o;
        }
        if (depth <= 0) {
            return new JsonPrimitive(v.getClass().isEnum() ? ((Enum<?>) v).name() : v.toString());
        }
        if (seen.containsKey(v)) {
            return new JsonPrimitive(v.getClass().isEnum() ? ((Enum<?>) v).name() : "@" + v.getClass().getSimpleName());
        }
        if (v.getClass().isArray()) {
            JsonArray a = new JsonArray();
            int n = Array.getLength(v);
            for (int i = 0; i < n && i < 4096; i++) {
                a.add(value(Array.get(v, i), depth - 1));
            }
            return a;
        }
        if (v instanceof Collection<?> c) {
            seen.put(v, true);
            JsonArray a = new JsonArray();
            for (Object o : c) {
                a.add(value(o, depth - 1));
            }
            return a;
        }
        if (v instanceof Map<?, ?> m) {
            seen.put(v, true);
            JsonArray a = new JsonArray();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                JsonArray pair = new JsonArray();
                pair.add(value(e.getKey(), depth - 1));
                pair.add(value(e.getValue(), depth - 1));
                a.add(pair);
            }
            return a;
        }
        if (v.getClass().getName().startsWith("lotr.") || v.getClass().isEnum()) {
            return object(v, depth, false);
        }
        return new JsonPrimitive(v.toString());
    }

    private JsonObject object(Object v, int depth, boolean all) {
        seen.put(v, true);
        JsonObject o = new JsonObject();
        o.addProperty("_class", v.getClass().getName());
        if (v instanceof Enum<?> e) {
            o.addProperty("_name", e.name());
        }
        for (Class<?> c = v.getClass(); c != null && c != Object.class && c != Enum.class
                && (all || c.getName().startsWith("lotr.")); c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || o.has(f.getName())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object val = f.get(v);
                    if (val instanceof net.minecraft.util.IIcon) {
                        continue;
                    }
                    o.add(f.getName(), value(val, depth - 1));
                } catch (Throwable t) {
                    o.addProperty(f.getName(), "!" + t.getClass().getSimpleName());
                }
            }
            if (!all && !c.getSuperclass().getName().startsWith("lotr.")) {
                break;
            }
        }
        return o;
    }
}
