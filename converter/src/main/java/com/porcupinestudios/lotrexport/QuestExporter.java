package com.porcupinestudios.lotrexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;

/**
 * Writes quests.json: LOTR's waypoints (map and world position, region, faction, hidden), the fast travel cooldown
 * settings, and the mini-quest factories (per group: collect and kill quests with their targets, reward factor and
 * reward items). {@link #questInfo} adds each NPC's quest group and reward bonuses to npcs.json. Reflection on runtime
 * state only; no LOTR code is copied.
 */
final class QuestExporter {
    private final JsonArray errors;

    QuestExporter(JsonArray errors) {
        this.errors = errors;
    }

    JsonObject quests() {
        JsonObject root = new JsonObject();
        root.add("waypoints", waypoints());
        root.add("cooldown", cooldown());
        root.add("factories", factories());
        return root;
    }

    private JsonArray waypoints() {
        JsonArray out = new JsonArray();
        try {
            Class<?> c = Class.forName("lotr.common.world.map.LOTRWaypoint");
            for (Object wp : c.getEnumConstants()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", ((Enum<?>) wp).name());
                o.addProperty("code", (String) c.getMethod("getCodeName").invoke(wp));
                o.addProperty("region", NpcExporter.enumName(NpcExporter.field(wp, "region")));
                o.addProperty("faction", NpcExporter.enumName(NpcExporter.field(wp, "faction")));
                o.add("imgX", NpcExporter.number(NpcExporter.field(wp, "imgX")));
                o.add("imgY", NpcExporter.number(NpcExporter.field(wp, "imgY")));
                o.add("x", NpcExporter.number(NpcExporter.field(wp, "xCoord")));
                o.add("z", NpcExporter.number(NpcExporter.field(wp, "zCoord")));
                o.addProperty("hidden", Boolean.TRUE.equals(NpcExporter.field(wp, "isHidden")));
                out.add(o);
            }
        } catch (Throwable t) {
            errors.add(new JsonPrimitive("waypoints: " + t));
        }
        return out;
    }

    private JsonObject cooldown() {
        JsonObject o = new JsonObject();
        try {
            Class<?> c = Class.forName("lotr.common.LOTRLevelData");
            for (String name : new String[] { "WAYPOINT_COOLDOWN_DEFAULT", "WAYPOINT_COOLDOWN_MIN_DEFAULT",
                    "waypointCooldownMax", "waypointCooldownMin" }) {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                o.add(name, NpcExporter.number(f.get(null)));
            }
        } catch (Throwable t) {
            errors.add(new JsonPrimitive("waypoint cooldown: " + t));
        }
        return o;
    }

    private JsonObject factories() {
        JsonObject out = new JsonObject();
        try {
            Class<?> c = Class.forName("lotr.common.quest.LOTRMiniQuestFactory");
            Object weights = NpcExporter.field(c.getEnumConstants()[0], "questClassWeights");
            JsonObject w = new JsonObject();
            if (weights instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    w.add(((Class<?>) e.getKey()).getSimpleName(), NpcExporter.number(e.getValue()));
                }
            }
            out.add("_classWeights", w);
            for (Object f : c.getEnumConstants()) {
                JsonObject o = new JsonObject();
                o.addProperty("baseName", (String) NpcExporter.field(f, "baseName"));
                o.addProperty("alignmentRewardOverride",
                        NpcExporter.enumName(NpcExporter.field(f, "alignmentRewardOverride")));
                o.addProperty("noAlignRewardForEnemy", Boolean.TRUE.equals(NpcExporter.field(f,
                        "noAlignRewardForEnemy")));
                JsonArray quests = new JsonArray();
                Object map = NpcExporter.field(f, "questFactories");
                if (map instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        for (Object q : (List<?>) e.getValue()) {
                            quests.add(quest(((Class<?>) e.getKey()).getSimpleName(), q));
                        }
                    }
                }
                o.add("quests", quests);
                out.add(((Enum<?>) f).name(), o);
            }
        } catch (Throwable t) {
            errors.add(new JsonPrimitive("mini-quests: " + t));
        }
        return out;
    }

    private static JsonObject quest(String kind, Object q) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("factory", q.getClass().getSimpleName());
        for (Class<?> k = q.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || o.has(f.getName())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(q);
                    if (v instanceof Number || v instanceof Boolean || v instanceof String) {
                        o.add(f.getName(), new com.google.gson.Gson().toJsonTree(v));
                    } else if (v instanceof Enum<?> e) {
                        o.addProperty(f.getName(), e.name());
                    } else if (v instanceof ItemStack st) {
                        o.add(f.getName(), NpcExporter.stack(st));
                    } else if (v instanceof Class<?> cl) {
                        o.addProperty(f.getName(), NpcExporter.entityName(cl));
                    } else if (v instanceof List<?> l) {
                        JsonArray a = new JsonArray();
                        for (Object x : l) {
                            if (x instanceof ItemStack st) {
                                a.add(NpcExporter.stack(st));
                            }
                        }
                        o.add(f.getName(), a);
                    }
                } catch (Throwable ignored) {
                    // unreadable field: leave it out
                }
            }
        }
        return o;
    }

    /** The quest group an NPC gives quests from (by making one quest) and its reward bonuses. */
    static void questInfo(JsonObject o, Object entity) {
        try {
            Object quest = entity.getClass().getMethod("createMiniQuest").invoke(entity);
            if (quest != null) {
                o.addProperty("questGroup", NpcExporter.enumName(NpcExporter.field(quest, "questGroup")));
            }
        } catch (Throwable ignored) {
            // no quests, or the quest needs a running server
        }
        for (String m : new String[] { "getAlignmentBonus", "getCoinBonus" }) {
            Object v = NpcExporter.call(entity, m);
            if (v instanceof Number n) {
                o.addProperty(m.substring(3, 4).toLowerCase() + m.substring(4), n);
            }
        }
    }
}
