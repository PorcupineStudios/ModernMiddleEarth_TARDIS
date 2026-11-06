package com.porcupinestudios.lotrexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;

/**
 * LOTR's machine recipes, which live outside the crafting manager: the barrel (brewing), millstone, Ent jar, the
 * alloy forges (each forge's own smelting and two-item alloys) and the unsmeltery. Read through reflection on LOTR's
 * recipe registries and by asking each forge tile entity what it makes from every item; nothing is copied.
 */
class StationExporter {
    private static final String[] FORGES = { "LOTRTileEntityAlloyForge", "LOTRTileEntityDwarvenForge",
            "LOTRTileEntityElvenForge", "LOTRTileEntityOrcForge" };
    private final Exporter ex;

    StationExporter(Exporter ex) {
        this.ex = ex;
    }

    JsonObject export() {
        JsonObject o = new JsonObject();
        o.add("brewing", brewing());
        o.add("millstone", millstone());
        o.add("ent_jar", entJar());
        o.add("forges", forges());
        o.add("unsmeltery", unsmeltery());
        return o;
    }

    private Object staticField(String cls, String name) throws Exception {
        Field f = Class.forName(cls).getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private JsonElement brewing() {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        try {
            for (Object r : (List<?>) staticField("lotr.common.recipe.LOTRBrewingRecipes", "recipes")) {
                JsonObject j = ex.recipeJson((IRecipe) r);
                if (j != null) {
                    a.add(j);
                }
            }
            o.addProperty("barrelCapacity", ((Number) staticField("lotr.common.recipe.LOTRBrewingRecipes",
                    "BARREL_CAPACITY")).intValue());
        } catch (Throwable t) {
            ex.report("brewing", t);
        }
        o.add("recipes", a);
        return o;
    }

    private JsonElement millstone() {
        JsonArray a = new JsonArray();
        try {
            Map<?, ?> map = (Map<?, ?>) staticField("lotr.common.recipe.LOTRMillstoneRecipes", "recipeList");
            for (Map.Entry<?, ?> e : map.entrySet()) {
                JsonObject j = new JsonObject();
                j.add("input", ex.stackJson((ItemStack) e.getKey()));
                Object res = e.getValue();
                for (Field f : res.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    Object v = f.get(res);
                    if (v instanceof ItemStack s) {
                        j.add("output", ex.stackJson(s));
                    } else if (v instanceof Number n) {
                        j.addProperty("chance", n);
                    }
                }
                a.add(j);
            }
        } catch (Throwable t) {
            ex.report("millstone", t);
        }
        return a;
    }

    private JsonElement entJar() {
        JsonArray a = new JsonArray();
        try {
            Map<?, ?> map = (Map<?, ?>) staticField("lotr.common.recipe.LOTREntJarRecipes", "recipes");
            for (Map.Entry<?, ?> e : map.entrySet()) {
                JsonObject j = new JsonObject();
                j.add("input", ex.stackJson((ItemStack) e.getKey()));
                j.add("output", ex.stackJson((ItemStack) e.getValue()));
                a.add(j);
            }
        } catch (Throwable t) {
            ex.report("entJar", t);
        }
        return a;
    }

    /** Every item stack worth trying in a machine: each registered item's creative variants (or meta 0). */
    @SuppressWarnings("unchecked")
    private List<ItemStack> allStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (Object o : Item.itemRegistry) {
            Item item = (Item) o;
            List<ItemStack> subs = new ArrayList<>();
            try {
                item.getSubItems(item, item.getCreativeTab(), subs);
            } catch (Throwable ignored) {
                // some items need a world; meta 0 below
            }
            if (subs.isEmpty()) {
                subs.add(new ItemStack(item));
            }
            for (ItemStack s : subs) {
                if (s != null && s.getItem() != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static Method find(Class<?> c, String name, Class<?>... params) throws NoSuchMethodException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // look in the superclass
            }
        }
        throw new NoSuchMethodException(c.getName() + "." + name);
    }

    /**
     * Each forge's single-item smelting (only what differs from the vanilla furnace) and its alloys: every pair of
     * smeltable-looking items (anything with a smelting result anywhere, plus ingots, nuggets and coal) is offered.
     */
    private JsonElement forges() {
        JsonObject o = new JsonObject();
        List<ItemStack> all = allStacks();
        for (String name : FORGES) {
            JsonObject forge = new JsonObject();
            JsonArray single = new JsonArray();
            JsonArray alloys = new JsonArray();
            try {
                Class<?> c = Class.forName("lotr.common.tileentity." + name);
                Object te = c.getDeclaredConstructor().newInstance();
                Method smelt = find(c, "getSmeltingResult", ItemStack.class);
                Method alloy = find(c, "getAlloySmeltingResult", ItemStack.class, ItemStack.class);
                List<ItemStack> candidates = new ArrayList<>();
                for (ItemStack s : all) {
                    ItemStack r = safe(() -> (ItemStack) smelt.invoke(te, s.copy()));
                    ItemStack vanilla = FurnaceRecipes.smelting().getSmeltingResult(s);
                    if (r != null) {
                        candidates.add(s);
                        if (vanilla == null || !ItemStack.areItemStacksEqual(r, vanilla)) {
                            JsonObject j = new JsonObject();
                            j.add("input", ex.stackJson(s));
                            j.add("output", ex.stackJson(r));
                            single.add(j);
                        }
                    } else {
                        String n = String.valueOf(Item.itemRegistry.getNameForObject(s.getItem())).toLowerCase();
                        if (n.contains("ingot") || n.contains("nugget") || n.contains("coal") || n.contains("ore")) {
                            candidates.add(s);
                        }
                    }
                }
                for (ItemStack a : candidates) {
                    for (ItemStack b : candidates) {
                        ItemStack r = safe(() -> (ItemStack) alloy.invoke(te, a.copy(), b.copy()));
                        if (r != null) {
                            JsonObject j = new JsonObject();
                            j.add("input", ex.stackJson(a));
                            j.add("alloy", ex.stackJson(b));
                            j.add("output", ex.stackJson(r));
                            alloys.add(j);
                        }
                    }
                }
            } catch (Throwable t) {
                ex.report("forge " + name, t);
            }
            forge.add("smelting", single);
            forge.add("alloys", alloys);
            o.add(name, forge);
        }
        return o;
    }

    /** What the unsmeltery gives back at most for each item it accepts. */
    private JsonElement unsmeltery() {
        JsonArray a = new JsonArray();
        try {
            Class<?> c = Class.forName("lotr.common.tileentity.LOTRTileEntityUnsmeltery");
            Object te = c.getDeclaredConstructor().newInstance();
            Method can = find(c, "canBeUnsmelted", ItemStack.class);
            Method largest = find(c, "getLargestUnsmeltingResult", ItemStack.class);
            for (ItemStack s : allStacks()) {
                Boolean ok = safe(() -> (Boolean) can.invoke(te, s.copy()));
                if (!Boolean.TRUE.equals(ok)) {
                    continue;
                }
                ItemStack r = safe(() -> (ItemStack) largest.invoke(te, s.copy()));
                if (r != null) {
                    JsonObject j = new JsonObject();
                    j.add("input", ex.stackJson(s));
                    j.add("output", ex.stackJson(r));
                    a.add(j);
                }
            }
        } catch (Throwable t) {
            ex.report("unsmeltery", t);
        }
        return a;
    }

    private interface Call<T> {
        T get() throws Exception;
    }

    private static <T> T safe(Call<T> c) {
        try {
            return c.get();
        } catch (Throwable t) {
            return null;
        }
    }
}
