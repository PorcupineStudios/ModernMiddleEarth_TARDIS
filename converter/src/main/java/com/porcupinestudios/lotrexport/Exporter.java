package com.porcupinestudios.lotrexport;

import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.IIcon;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * Writes blocks.json, items.json, recipes.json, tabs.json, oredict.json, world.json and factions.json. Only reads
 * public game state through reflection and the normal Block/Item methods; no LOTR code is copied.
 */
public class Exporter {
    private static final String NS = "lotr";
    private final File out;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<Object, String> oreLists = new IdentityHashMap<>();
    private final JsonArray errors = new JsonArray();

    public Exporter(File out) {
        this.out = out;
    }

    public void run() throws Exception {
        out.mkdirs();
        for (String name : OreDictionary.getOreNames()) {
            oreLists.put(OreDictionary.getOres(name), name);
        }
        write("blocks.json", blocks());
        write("items.json", items());
        write("recipes.json", recipes());
        write("tabs.json", tabs());
        write("oredict.json", oredict());
        WorldExporter world = new WorldExporter(errors);
        write("world.json", world.world());
        write("factions.json", world.factions());
        write("errors.json", errors);
    }

    // ---------------------------------------------------------------- blocks

    private JsonArray blocks() {
        JsonArray arr = new JsonArray();
        for (String key : sortedKeys(Block.blockRegistry.getKeys())) {
            if (!key.startsWith(NS + ":")) {
                continue;
            }
            Block block = (Block) Block.blockRegistry.getObject(key);
            try {
                arr.add(block(key, block));
            } catch (Throwable t) {
                error("block " + key, t);
            }
        }
        return arr;
    }

    private JsonObject block(String key, Block block) {
        JsonObject o = new JsonObject();
        o.addProperty("id", key);
        o.add("classes", classChain(block.getClass(), Block.class));
        o.add("interfaces", interfaces(block.getClass()));
        o.addProperty("unlocalized", block.getUnlocalizedName());
        o.addProperty("material", materialName(block.getMaterial()));
        o.addProperty("hardness", floatField(block, Block.class, "blockHardness", "field_149782_v"));
        o.addProperty("resistance", floatField(block, Block.class, "blockResistance", "field_149781_w"));
        o.addProperty("light", block.getLightValue());
        o.addProperty("lightOpacity", block.getLightOpacity());
        o.addProperty("sound", block.stepSound == null ? null : block.stepSound.soundName);
        o.addProperty("renderType", block.getRenderType());
        o.addProperty("renderPass", block.getRenderBlockPass());
        o.addProperty("opaque", block.isOpaqueCube());
        o.addProperty("normalCube", block.renderAsNormalBlock());
        o.addProperty("ticksRandomly", block.getTickRandomly());
        o.addProperty("power", block.canProvidePower());
        o.addProperty("slipperiness", block.slipperiness);
        o.addProperty("blockColor", block.getBlockColor());
        CreativeTabs tab = block.getCreativeTabToDisplayOn();
        o.addProperty("tab", tab == null ? null : tab.getTabLabel());
        try {
            block.setBlockBoundsForItemRender();
            JsonArray b = new JsonArray();
            b.add(new JsonPrimitive(block.getBlockBoundsMinX()));
            b.add(new JsonPrimitive(block.getBlockBoundsMinY()));
            b.add(new JsonPrimitive(block.getBlockBoundsMinZ()));
            b.add(new JsonPrimitive(block.getBlockBoundsMaxX()));
            b.add(new JsonPrimitive(block.getBlockBoundsMaxY()));
            b.add(new JsonPrimitive(block.getBlockBoundsMaxZ()));
            o.add("bounds", b);
        } catch (Throwable t) {
            error("bounds " + key, t);
        }

        Item item = Item.getItemFromBlock(block);
        o.addProperty("itemClass", item == null ? null : item.getClass().getName());
        Set<Integer> metas = new TreeSet<>();
        if (item != null) {
            List<ItemStack> subs = new ArrayList<>();
            try {
                block.getSubBlocks(item, tab, subs);
            } catch (Throwable t) {
                error("subBlocks " + key, t);
            }
            for (ItemStack s : subs) {
                if (s != null) {
                    metas.add(s.getItemDamage());
                }
            }
        }
        o.addProperty("inCreative", !metas.isEmpty());
        if (metas.isEmpty()) {
            metas.add(0);
        }
        JsonArray variants = new JsonArray();
        for (int meta : metas) {
            JsonObject v = new JsonObject();
            v.addProperty("meta", meta);
            JsonArray icons = new JsonArray();
            for (int side = 0; side < 6; side++) {
                int s = side;
                icons.add(icon(() -> block.getIcon(s, meta)));
            }
            v.add("icons", icons);
            v.addProperty("renderColor", safeInt(() -> block.getRenderColor(meta)));
            v.addProperty("harvestTool", block.getHarvestTool(meta));
            v.addProperty("harvestLevel", block.getHarvestLevel(meta));
            v.addProperty("tileEntity", block.hasTileEntity(meta));
            if (item != null) {
                ItemStack stack = new ItemStack(item, 1, meta);
                v.addProperty("itemUnlocalized", safeString(() -> item.getUnlocalizedName(stack)));
                v.addProperty("name", safeString(stack::getDisplayName));
                v.add("itemIcon", icon(() -> item.getIconIndex(stack)));
            }
            variants.add(v);
        }
        o.add("variants", variants);
        return o;
    }

    // ---------------------------------------------------------------- items

    private JsonArray items() {
        JsonArray arr = new JsonArray();
        for (String key : sortedKeys(Item.itemRegistry.getKeys())) {
            if (!key.startsWith(NS + ":")) {
                continue;
            }
            Item item = (Item) Item.itemRegistry.getObject(key);
            if (item instanceof ItemBlock) {
                continue;
            }
            try {
                arr.add(item(key, item));
            } catch (Throwable t) {
                error("item " + key, t);
            }
        }
        return arr;
    }

    private JsonObject item(String key, Item item) {
        JsonObject o = new JsonObject();
        o.addProperty("id", key);
        o.add("classes", classChain(item.getClass(), Item.class));
        o.add("interfaces", interfaces(item.getClass()));
        o.addProperty("maxDamage", item.getMaxDamage());
        o.addProperty("hasSubtypes", item.getHasSubtypes());
        o.addProperty("full3D", item.isFull3D());
        o.addProperty("enchantability", item.getItemEnchantability());
        CreativeTabs tab = item.getCreativeTab();
        o.addProperty("tab", tab == null ? null : tab.getTabLabel());
        if (item instanceof ItemTool tool) {
            o.addProperty("toolMaterial", tool.getToolMaterialName());
        }
        if (item instanceof ItemSword sword) {
            o.addProperty("toolMaterial", sword.getToolMaterialName());
        }
        if (item instanceof ItemArmor armor) {
            JsonObject a = new JsonObject();
            a.addProperty("slot", armor.armorType);
            a.addProperty("defense", armor.damageReduceAmount);
            a.addProperty("material", armor.getArmorMaterial().name());
            o.add("armor", a);
        }

        Set<Integer> metas = new TreeSet<>();
        List<ItemStack> subs = new ArrayList<>();
        try {
            item.getSubItems(item, tab, subs);
        } catch (Throwable t) {
            error("subItems " + key, t);
        }
        for (ItemStack s : subs) {
            if (s != null) {
                metas.add(s.getItemDamage());
            }
        }
        o.addProperty("inCreative", !metas.isEmpty());
        if (metas.isEmpty()) {
            metas.add(0);
        }
        JsonArray variants = new JsonArray();
        for (int meta : metas) {
            ItemStack stack = new ItemStack(item, 1, meta);
            JsonObject v = new JsonObject();
            v.addProperty("meta", meta);
            v.addProperty("unlocalized", safeString(() -> item.getUnlocalizedName(stack)));
            v.addProperty("name", safeString(stack::getDisplayName));
            v.addProperty("maxStack", safeInt(() -> item.getItemStackLimit(stack)));
            JsonArray layers = new JsonArray();
            JsonArray colors = new JsonArray();
            Integer passCount = item.requiresMultipleRenderPasses() ? safeInt(() -> item.getRenderPasses(meta)) : null;
            int passes = passCount == null ? 1 : passCount;
            for (int pass = 0; pass < passes; pass++) {
                int p = pass;
                layers.add(passes == 1 ? icon(() -> item.getIconIndex(stack)) : icon(() -> item.getIcon(stack, p)));
                Integer color = safeInt(() -> item.getColorFromItemStack(stack, p));
                colors.add(color == null ? JsonNull.INSTANCE : new JsonPrimitive(color));
            }
            v.add("icons", layers);
            v.add("colors", colors);
            JsonObject tools = new JsonObject();
            try {
                for (String cls : item.getToolClasses(stack)) {
                    tools.addProperty(cls, item.getHarvestLevel(stack, cls));
                }
            } catch (Throwable t) {
                error("toolClasses " + key, t);
            }
            v.add("toolClasses", tools);
            v.add("attributes", attributes(item, stack));
            if (item instanceof ItemFood) {
                JsonObject f = new JsonObject();
                f.add("heal", callNumber(item, new String[] { "func_150905_g", "getHealAmount" }, stack));
                f.add("saturation", callNumber(item, new String[] { "func_150906_h", "getSaturationModifier" }, stack));
                v.add("food", f);
            }
            variants.add(v);
        }
        o.add("variants", variants);
        return o;
    }

    @SuppressWarnings("unchecked")
    private JsonObject attributes(Item item, ItemStack stack) {
        JsonObject o = new JsonObject();
        try {
            Multimap<String, AttributeModifier> mods = item.getAttributeModifiers(stack);
            for (Map.Entry<String, AttributeModifier> e : mods.entries()) {
                o.addProperty(e.getKey(), e.getValue().getAmount());
            }
        } catch (Throwable t) {
            error("attributes " + Item.itemRegistry.getNameForObject(item), t);
        }
        return o;
    }

    // ---------------------------------------------------------------- recipes

    private JsonObject recipes() {
        JsonObject o = new JsonObject();
        JsonArray table = new JsonArray();
        for (Object r : CraftingManager.getInstance().getRecipeList()) {
            JsonObject j = recipe((IRecipe) r);
            if (j != null && j.toString().contains("\"" + NS + ":")) {
                table.add(j);
            }
        }
        o.add("crafting", table);

        JsonObject lists = new JsonObject();
        try {
            Class<?> c = Class.forName("lotr.common.recipe.LOTRRecipes");
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || !List.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                f.setAccessible(true);
                Object v = f.get(null);
                if (!(v instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof IRecipe)) {
                    continue;
                }
                JsonArray a = new JsonArray();
                for (Object r : list) {
                    JsonObject j = recipe((IRecipe) r);
                    if (j != null) {
                        a.add(j);
                    }
                }
                lists.add(f.getName(), a);
            }
        } catch (Throwable t) {
            error("LOTRRecipes lists", t);
        }
        o.add("stations", lists);

        JsonArray smelting = new JsonArray();
        for (Object e : FurnaceRecipes.smelting().getSmeltingList().entrySet()) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) e;
            JsonObject j = new JsonObject();
            j.add("input", stack((ItemStack) entry.getKey()));
            j.add("output", stack((ItemStack) entry.getValue()));
            j.add("xp", callNumber(FurnaceRecipes.smelting(), new String[] { "func_151398_b", "getSmeltingExperience" },
                    entry.getValue()));
            if (j.toString().contains("\"" + NS + ":")) {
                smelting.add(j);
            }
        }
        o.add("smelting", smelting);
        return o;
    }

    private JsonObject recipe(IRecipe r) {
        JsonObject o = new JsonObject();
        o.addProperty("class", r.getClass().getName());
        try {
            o.add("output", stack(r.getRecipeOutput()));
            if (r instanceof ShapedRecipes s) {
                o.addProperty("type", "shaped");
                o.addProperty("width", s.recipeWidth);
                o.addProperty("height", s.recipeHeight);
                JsonArray in = new JsonArray();
                for (ItemStack i : s.recipeItems) {
                    in.add(stack(i));
                }
                o.add("input", in);
            } else if (r instanceof ShapelessRecipes s) {
                o.addProperty("type", "shapeless");
                JsonArray in = new JsonArray();
                for (Object i : s.recipeItems) {
                    in.add(ingredient(i));
                }
                o.add("input", in);
            } else if (r instanceof ShapedOreRecipe s) {
                o.addProperty("type", "shaped");
                o.add("width", intField(s, ShapedOreRecipe.class, "width"));
                o.add("height", intField(s, ShapedOreRecipe.class, "height"));
                JsonArray in = new JsonArray();
                for (Object i : s.getInput()) {
                    in.add(ingredient(i));
                }
                o.add("input", in);
            } else if (r instanceof ShapelessOreRecipe s) {
                o.addProperty("type", "shapeless");
                JsonArray in = new JsonArray();
                for (Object i : s.getInput()) {
                    in.add(ingredient(i));
                }
                o.add("input", in);
            } else {
                o.addProperty("type", "special");
            }
        } catch (Throwable t) {
            error("recipe " + r.getClass().getName(), t);
            return null;
        }
        return o;
    }

    private JsonElement ingredient(Object i) {
        if (i == null) {
            return JsonNull.INSTANCE;
        }
        if (i instanceof ItemStack s) {
            return stack(s);
        }
        if (i instanceof Collection<?> c) {
            String ore = oreLists.get(i);
            if (ore != null) {
                JsonObject o = new JsonObject();
                o.addProperty("ore", ore);
                return o;
            }
            JsonArray a = new JsonArray();
            for (Object x : c) {
                a.add(ingredient(x));
            }
            return a;
        }
        return new JsonPrimitive("?" + i.getClass().getName());
    }

    private JsonElement stack(ItemStack s) {
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

    // ---------------------------------------------------------------- tabs and ore dictionary

    @SuppressWarnings("unchecked")
    private JsonArray tabs() {
        JsonArray arr = new JsonArray();
        for (CreativeTabs tab : CreativeTabs.creativeTabArray) {
            if (tab == null) {
                continue;
            }
            List<ItemStack> list = new ArrayList<>();
            try {
                tab.displayAllReleventItems(list);
            } catch (Throwable t) {
                error("tab " + tab.getTabLabel(), t);
            }
            JsonArray stacks = new JsonArray();
            boolean ours = false;
            for (ItemStack s : list) {
                JsonElement j = stack(s);
                stacks.add(j);
                ours |= j.toString().contains("\"" + NS + ":");
            }
            if (!ours) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("label", tab.getTabLabel());
            o.addProperty("name", tab.getTranslatedTabLabel());
            o.add("icon", stack(tab.getIconItemStack()));
            o.add("items", stacks);
            arr.add(o);
        }
        return arr;
    }

    private JsonObject oredict() {
        JsonObject o = new JsonObject();
        for (String name : sortedKeys(java.util.Arrays.asList(OreDictionary.getOreNames()))) {
            JsonArray a = new JsonArray();
            for (ItemStack s : OreDictionary.getOres(name)) {
                JsonElement j = stack(s);
                if (j.toString().contains("\"" + NS + ":")) {
                    a.add(j);
                }
            }
            if (a.size() > 0) {
                o.add(name, a);
            }
        }
        return o;
    }

    // ---------------------------------------------------------------- helpers

    private interface IconSupplier {
        IIcon get();
    }

    private interface IntSupplier {
        int get();
    }

    private interface StringSupplier {
        String get();
    }

    private JsonElement icon(IconSupplier s) {
        try {
            IIcon icon = s.get();
            return icon == null ? JsonNull.INSTANCE : new JsonPrimitive(icon.getIconName());
        } catch (Throwable t) {
            return new JsonPrimitive("!" + t.getClass().getSimpleName());
        }
    }

    private static Integer safeInt(IntSupplier s) {
        try {
            return s.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String safeString(StringSupplier s) {
        try {
            return s.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static JsonArray classChain(Class<?> c, Class<?> stop) {
        JsonArray a = new JsonArray();
        while (c != null && c != Object.class) {
            a.add(new JsonPrimitive(c.getName()));
            if (c == stop) {
                break;
            }
            c = c.getSuperclass();
        }
        return a;
    }

    private static JsonArray interfaces(Class<?> c) {
        Set<String> names = new LinkedHashSet<>();
        while (c != null) {
            collectInterfaces(c, names);
            c = c.getSuperclass();
        }
        JsonArray a = new JsonArray();
        for (String n : names) {
            a.add(new JsonPrimitive(n));
        }
        return a;
    }

    private static void collectInterfaces(Class<?> c, Set<String> names) {
        for (Class<?> i : c.getInterfaces()) {
            if (names.add(i.getName())) {
                collectInterfaces(i, names);
            }
        }
    }

    private static String materialName(Material m) {
        for (Field f : Material.class.getFields()) {
            try {
                if (Modifier.isStatic(f.getModifiers()) && f.get(null) == m) {
                    return f.getName();
                }
            } catch (IllegalAccessException ignored) {
                // public field
            }
        }
        return m == null ? null : "custom:" + m.getClass().getName();
    }

    private Float floatField(Object o, Class<?> owner, String... names) {
        for (String n : names) {
            try {
                Field f = owner.getDeclaredField(n);
                f.setAccessible(true);
                return f.getFloat(o);
            } catch (Throwable ignored) {
                // try the next name
            }
        }
        return null;
    }

    private JsonElement intField(Object o, Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return new JsonPrimitive(f.getInt(o));
        } catch (Throwable t) {
            return JsonNull.INSTANCE;
        }
    }

    private static JsonElement callNumber(Object target, String[] names, Object arg) {
        for (String n : names) {
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equals(n) && m.getParameterTypes().length == 1) {
                    try {
                        Object v = m.invoke(target, arg);
                        return v instanceof Number num ? new JsonPrimitive(num) : JsonNull.INSTANCE;
                    } catch (Throwable ignored) {
                        // try the next name
                    }
                }
            }
        }
        return JsonNull.INSTANCE;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static List<String> sortedKeys(Collection keys) {
        List<String> list = new ArrayList<>();
        for (Object k : keys) {
            list.add(String.valueOf(k));
        }
        java.util.Collections.sort(list);
        return list;
    }

    private void error(String where, Throwable t) {
        errors.add(new JsonPrimitive(where + ": " + t));
    }

    private void write(String name, JsonElement json) throws Exception {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(out, name)), StandardCharsets.UTF_8)) {
            gson.toJson(json, w);
        }
        System.out.println("[lotrexport] wrote " + name);
    }
}
