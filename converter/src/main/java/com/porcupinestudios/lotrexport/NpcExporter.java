package com.porcupinestudios.lotrexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/**
 * Writes npcs.json: LOTR's trade pools (what traders buy and sell, for how many coins), unit pools (who captains hire
 * out, cost, alignment, pledge), and every LOTR entity: faction, size, attributes, the gear it spawns with, which pools
 * it uses, and the textures its renderer draws (fixed skins and random-skin folders). Entities are built in a
 * throwaway client world that never ticks. Reflection on runtime state only; no LOTR code is copied.
 */
final class NpcExporter {
    private final JsonArray errors;
    private final Map<Object, String> tradePools = new IdentityHashMap<>();
    private final Map<Object, String> unitPools = new IdentityHashMap<>();

    NpcExporter(JsonArray errors) {
        this.errors = errors;
    }

    JsonObject npcs() {
        JsonObject root = new JsonObject();
        root.add("trades", pools("lotr.common.entity.npc.LOTRTradeEntries", tradePools, true));
        root.add("units", pools("lotr.common.entity.npc.LOTRUnitTradeEntries", unitPools, false));
        root.add("entities", entities());
        return root;
    }

    // ---------------------------------------------------------------- trade and unit pools

    private JsonObject pools(String className, Map<Object, String> names, boolean trades) {
        JsonObject out = new JsonObject();
        try {
            Class<?> c = Class.forName(className);
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType() != c) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object pool = f.get(null);
                    if (pool == null) {
                        continue;
                    }
                    names.put(pool, f.getName());
                    out.add(f.getName(), trades ? tradePool(pool) : unitPool(pool));
                } catch (Throwable t) {
                    error("pool " + f.getName(), t);
                }
            }
        } catch (Throwable t) {
            error(className, t);
        }
        return out;
    }

    private JsonObject tradePool(Object pool) {
        JsonObject o = new JsonObject();
        Object type = field(pool, "tradeType");
        o.addProperty("type", type == null ? null : ((Enum<?>) type).name());
        Object vessels = field(pool, "drinkVessels");
        if (vessels instanceof Object[] vs) {
            JsonArray a = new JsonArray();
            for (Object v : vs) {
                a.add(new JsonPrimitive(((Enum<?>) v).name()));
            }
            o.add("vessels", a);
        }
        JsonArray entries = new JsonArray();
        Object list = field(pool, "tradeEntries");
        if (list instanceof Object[] es) {
            for (Object e : es) {
                JsonObject entry = new JsonObject();
                entry.add("item", stack((ItemStack) field(e, "tradeItem")));
                entry.add("cost", number(field(e, "tradeCost")));
                entry.addProperty("class", e.getClass().getSimpleName());
                entries.add(entry);
            }
        }
        o.add("entries", entries);
        return o;
    }

    private JsonObject unitPool(Object pool) {
        JsonObject o = new JsonObject();
        JsonArray entries = new JsonArray();
        Object list = field(pool, "tradeEntries");
        if (list instanceof Object[] es) {
            for (Object e : es) {
                JsonObject entry = new JsonObject();
                entry.addProperty("entity", entityName((Class<?>) field(e, "entityClass")));
                entry.addProperty("mount", entityName((Class<?>) field(e, "mountClass")));
                Object armor = field(e, "mountArmor");
                entry.addProperty("mountArmor", armor instanceof Item i ? Item.itemRegistry.getNameForObject(i) : null);
                entry.add("mountArmorChance", number(field(e, "mountArmorChance")));
                entry.addProperty("name", (String) field(e, "name"));
                entry.add("cost", number(field(e, "initialCost")));
                entry.add("alignment", number(field(e, "alignmentRequired")));
                Object pledge = field(e, "pledgeType");
                entry.addProperty("pledge", pledge == null ? null : ((Enum<?>) pledge).name());
                Object task = field(e, "task");
                entry.addProperty("task", task == null ? null : ((Enum<?>) task).name());
                entry.addProperty("extraInfo", (String) field(e, "extraInfo"));
                entries.add(entry);
            }
        }
        o.add("entries", entries);
        return o;
    }

    // ---------------------------------------------------------------- entities

    private JsonArray entities() {
        JsonArray arr = new JsonArray();
        World world = null;
        try {
            // A client world is the cheapest real world: no save, no server. It only lives during the export.
            world = new WorldClient(new NetHandlerPlayClient(Minecraft.getMinecraft(), null, null), new WorldSettings(0L, WorldSettings.GameType.SURVIVAL, false, false,
                    WorldType.DEFAULT), 0, EnumDifficulty.NORMAL, Minecraft.getMinecraft().mcProfiler);
        } catch (Throwable t) {
            error("throwaway world", t);
        }
        try {
            Class<?> reg = Class.forName("lotr.common.entity.LOTREntities");
            Map<?, ?> classToId = (Map<?, ?>) reg.getField("classToIDMapping").get(null);
            Map<?, ?> idToString = (Map<?, ?>) reg.getField("IDToStringMapping").get(null);
            Map<?, ?> eggs = (Map<?, ?>) reg.getField("spawnEggs").get(null);
            for (Map.Entry<?, ?> e : classToId.entrySet()) {
                Class<?> c = (Class<?>) e.getKey();
                Object id = e.getValue();
                JsonObject o = new JsonObject();
                o.addProperty("name", String.valueOf(idToString.get(id)));
                o.add("id", number(id));
                o.addProperty("class", c.getName());
                JsonArray chain = new JsonArray();
                for (Class<?> s = c; s != null && s != Entity.class; s = s.getSuperclass()) {
                    chain.add(new JsonPrimitive(s.getName()));
                }
                o.add("classes", chain);
                JsonArray ifaces = new JsonArray();
                for (Class<?> s = c; s != null; s = s.getSuperclass()) {
                    for (Class<?> i : s.getInterfaces()) {
                        ifaces.add(new JsonPrimitive(i.getName()));
                    }
                }
                o.add("interfaces", ifaces);
                Object egg = eggs == null ? null : eggs.get(id);
                if (egg != null) {
                    o.add("egg", primitives(egg));
                }
                if (world != null) {
                    instance(o, c, world);
                }
                render(o, c);
                arr.add(o);
            }
        } catch (Throwable t) {
            error("entities", t);
        }
        return arr;
    }

    /** Builds one entity in the throwaway world and reads what it is. */
    private void instance(JsonObject o, Class<?> c, World world) {
        Entity entity;
        try {
            entity = (Entity) c.getConstructor(World.class).newInstance(world);
        } catch (Throwable t) {
            error("entity " + c.getSimpleName(), t);
            return;
        }
        o.addProperty("width", entity.width);
        o.addProperty("height", entity.height);
        o.addProperty("faction", enumName(call(entity, "getFaction")));
        o.addProperty("hiringFaction", enumName(call(entity, "getHiringFaction")));
        Object npcName = call(entity, "getNPCName");
        o.addProperty("sampleName", npcName == null ? null : npcName.toString());
        Object buy = call(entity, "getBuyPool");
        Object sell = call(entity, "getSellPool");
        Object units = call(entity, "getUnits");
        o.addProperty("buyPool", buy == null ? null : tradePools.get(buy));
        o.addProperty("sellPool", sell == null ? null : tradePools.get(sell));
        o.addProperty("unitPool", units == null ? null : unitPools.get(units));
        o.addProperty("warhorn", enumName(call(entity, "getWarhorn")));
        o.addProperty("speechBank", speechBank(entity, world));
        QuestExporter.questInfo(o, entity);
        o.add("fields", primitives(entity));
        if (entity instanceof EntityLivingBase living) {
            JsonObject attrs = new JsonObject();
            try {
                for (Object a : living.getAttributeMap().getAllAttributes()) {
                    IAttributeInstance ai = (IAttributeInstance) a;
                    attrs.addProperty(ai.getAttribute().getAttributeUnlocalizedName(), ai.getBaseValue());
                }
            } catch (Throwable t) {
                error("attributes " + c.getSimpleName(), t);
            }
            o.add("attributes", attrs);
        }
        if (entity instanceof EntityLiving living) {
            // LOTR picks gear at random when an NPC spawns: sample it a few times and keep every distinct item.
            JsonObject gear = new JsonObject();
            for (int sample = 0; sample < 8; sample++) {
                try {
                    living.setPosition(0, 64, 0);
                    living.onSpawnWithEgg((IEntityLivingData) null);
                } catch (Throwable t) {
                    if (sample == 0) {
                        error("spawn gear " + c.getSimpleName(), t);
                    }
                    break;
                }
                String[] slots = { "mainhand", "feet", "legs", "chest", "head" };
                for (int slot = 0; slot < 5; slot++) {
                    addGear(gear, slots[slot], living.getEquipmentInSlot(slot));
                }
                Object inv = field(entity, "npcItemsInv");
                if (inv != null) {
                    for (String m : new String[] { "getMeleeWeapon", "getRangedWeapon", "getIdleItem",
                            "getMeleeWeaponMounted" }) {
                        Object item = call(inv, m);
                        addGear(gear, m.substring(3, 4).toLowerCase() + m.substring(4), (ItemStack) item);
                    }
                }
            }
            o.add("gear", gear);
        }
    }

    private static void addGear(JsonObject gear, String slot, ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        JsonArray list = gear.has(slot) ? gear.getAsJsonArray(slot) : new JsonArray();
        JsonElement s = stack(stack);
        for (JsonElement e : list) {
            if (e.equals(s)) {
                return;
            }
        }
        list.add(s);
        gear.add(slot, list);
    }

    /** The renderer's model class and every texture it holds: fixed skins and LOTR random-skin folders. */
    private void render(JsonObject o, Class<?> c) {
        try {
            Render r = RenderManager.instance.getEntityClassRenderObject(c);
            if (r == null) {
                return;
            }
            JsonObject out = new JsonObject();
            out.addProperty("class", r.getClass().getName());
            Object model = fieldNamed(r, "mainModel", "field_77045_g");
            out.addProperty("model", model == null ? null : model.getClass().getName());
            JsonObject textures = new JsonObject();
            for (Class<?> k = r.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(Modifier.isStatic(f.getModifiers()) ? null : r);
                        if (v instanceof ResourceLocation rl) {
                            textures.addProperty(f.getName(), rl.toString());
                        } else if (v != null && v.getClass().getSimpleName().contains("RandomSkins")) {
                            JsonObject skins = new JsonObject();
                            skins.addProperty("path", (String) field(v, "skinPath"));
                            Object list = field(v, "skins");
                            skins.addProperty("count", list instanceof java.util.List<?> l ? l.size() : 0);
                            textures.add(f.getName(), skins);
                        }
                    } catch (Throwable ignored) {
                        // unreadable field: leave it out
                    }
                }
            }
            out.add("textures", textures);
            o.add("render", out);
        } catch (Throwable t) {
            error("render " + c.getSimpleName(), t);
        }
    }

    // ---------------------------------------------------------------- helpers

    static String entityName(Class<?> c) {
        if (c == null) {
            return null;
        }
        try {
            Class<?> reg = Class.forName("lotr.common.entity.LOTREntities");
            Object name = reg.getMethod("getStringFromClass", Class.class).invoke(null, c);
            if (name != null) {
                return name.toString();
            }
        } catch (Throwable ignored) {
            // vanilla entity
        }
        Object map = null;
        for (String n : new String[] { "classToStringMapping", "field_75626_c" }) {
            try {
                Field f = EntityList.class.getDeclaredField(n);
                f.setAccessible(true);
                map = f.get(null);
                break;
            } catch (Throwable ignored) {
                // try the other name
            }
        }
        Object vanilla = map instanceof Map<?, ?> m ? m.get(c) : null;
        return vanilla == null ? c.getName() : vanilla.toString();
    }

    /** The speech bank LOTR picks for the client player (e.g. hobbit/hobbit/friendly); its folder is the NPC's voice. */
    static String speechBank(Entity entity, World world) {
        net.minecraft.entity.player.EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            try {
                player = new net.minecraft.client.entity.EntityOtherPlayerMP(world,
                        new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(new byte[] {1}), "Exporter"));
            } catch (Throwable t) {
                return null;
            }
        }
        for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("getSpeechBank") && m.getParameterTypes().length == 1) {
                    try {
                        m.setAccessible(true);
                        Object bank = m.invoke(entity, player);
                        return bank == null ? null : bank.toString();
                    } catch (Throwable t) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    static Object call(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == 0) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(o);
                    } catch (Throwable t) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    static String enumName(Object v) {
        return v instanceof Enum<?> e ? e.name() : null;
    }

    static JsonElement number(Object v) {
        return v instanceof Number n ? new JsonPrimitive(n) : JsonNull.INSTANCE;
    }

    static JsonElement stack(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("item", Item.itemRegistry.getNameForObject(s.getItem()));
        o.addProperty("meta", s.getItemDamage());
        if (s.stackSize != 1) {
            o.addProperty("count", s.stackSize);
        }
        if (s.hasTagCompound()) {
            o.addProperty("nbt", s.getTagCompound().toString());
        }
        return o;
    }

    /** Numbers, booleans, strings and enums declared on the object's own LOTR classes (not vanilla's). */
    static JsonObject primitives(Object obj) {
        JsonObject o = new JsonObject();
        for (Class<?> c = obj.getClass(); c != null && c.getName().startsWith("lotr."); c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || o.has(f.getName())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v instanceof Number n) {
                        o.addProperty(f.getName(), n);
                    } else if (v instanceof Boolean b) {
                        o.addProperty(f.getName(), b);
                    } else if (v instanceof String s) {
                        o.addProperty(f.getName(), s);
                    } else if (v instanceof Enum<?> e) {
                        o.addProperty(f.getName(), e.name());
                    }
                } catch (Throwable ignored) {
                    // unreadable field: leave it out
                }
            }
        }
        return o;
    }

    private static Object fieldNamed(Object obj, String... names) {
        for (String n : names) {
            Object v = field(obj, n);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    static Object field(Object obj, String name) {
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(Modifier.isStatic(f.getModifiers()) ? null : obj);
            } catch (NoSuchFieldException e) {
                // look in the superclass
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private void error(String where, Throwable t) {
        errors.add(new JsonPrimitive(where + ": " + t));
    }
}
