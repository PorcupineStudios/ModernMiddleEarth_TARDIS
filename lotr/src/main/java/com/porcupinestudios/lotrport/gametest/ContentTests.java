package com.porcupinestudios.lotrport.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.crafting.FactionCraftingMenu;
import com.porcupinestudios.lotrport.crafting.FactionRecipe;
import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 2 done-when: every converted LOTR block and item exists, sits in a creative tab, has a name, a model and its
 * textures, and every converted recipe loaded (faction recipes only at their own table).
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContentTests {
    private ContentTests() {}

    private static List<String> ids(String file, boolean withItemOnly) {
        List<String> out = new ArrayList<>();
        for (JsonObject e : ContentData.read(file)) {
            if (withItemOnly && e.has("item") && "none".equals(e.get("item").getAsString())) {
                continue;
            }
            out.add(e.get("id").getAsString());
        }
        return out;
    }

    @GameTest(template = "empty")
    public static void everyBlockAndItemIsRegistered(GameTestHelper helper) {
        List<String> blocks = ids("blocks.json", false);
        List<String> items = ids("items.json", false);
        helper.assertTrue(blocks.size() > 1000 && items.size() > 800, "generated content is missing");
        for (String id : blocks) {
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(LotrPort.id(id)), "block not registered: " + id);
        }
        for (String id : ids("blocks.json", true)) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(LotrPort.id(id)), "block item missing: " + id);
        }
        for (String id : items) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(LotrPort.id(id)), "item not registered: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyItemIsInACreativeTab(GameTestHelper helper) {
        CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), true,
                helper.getLevel().registryAccess());
        Set<Item> shown = new HashSet<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<CreativeModeTab>, CreativeModeTab> e
                : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            if (e.getKey().location().getNamespace().equals(LotrPort.MOD_ID)) {
                for (ItemStack s : e.getValue().getDisplayItems()) {
                    shown.add(s.getItem());
                }
            }
        }
        List<String> all = new ArrayList<>(ids("blocks.json", true));
        all.addAll(ids("items.json", false));
        for (String id : all) {
            helper.assertTrue(shown.contains(BuiltInRegistries.ITEM.get(LotrPort.id(id))), "not in a tab: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyItemHasNameModelAndTextures(GameTestHelper helper) {
        JsonObject lang = json("/assets/lotr_port/lang/en_us.json");
        helper.assertTrue(lang != null, "en_us.json missing");
        List<String> all = new ArrayList<>(ids("blocks.json", true));
        all.addAll(ids("items.json", false));
        for (String id : all) {
            Item item = BuiltInRegistries.ITEM.get(LotrPort.id(id));
            helper.assertTrue(lang.has(item.getDescriptionId()), "no English name: " + id);
            JsonObject model = json("/assets/lotr_port/models/item/" + id + ".json");
            helper.assertTrue(model != null, "no item model: " + id);
            checkTextures(helper, model, id, 0);
        }
        helper.succeed();
    }

    /** Follows the model and its lotr_port parents and checks each lotr_port texture file exists. */
    private static void checkTextures(GameTestHelper helper, JsonObject model, String id, int depth) {
        if (model.has("textures")) {
            for (Map.Entry<String, JsonElement> t : model.getAsJsonObject("textures").entrySet()) {
                String tex = t.getValue().getAsString();
                if (tex.startsWith(LotrPort.MOD_ID + ":")) {
                    String path = "/assets/lotr_port/textures/" + tex.substring(LotrPort.MOD_ID.length() + 1) + ".png";
                    helper.assertTrue(LotrPort.class.getResource(path) != null, "missing texture " + tex + " in " + id);
                }
            }
        }
        if (depth < 3 && model.has("parent")) {
            String parent = model.get("parent").getAsString();
            if (parent.startsWith(LotrPort.MOD_ID + ":")) {
                JsonObject p = json("/assets/lotr_port/models/" + parent.substring(LotrPort.MOD_ID.length() + 1)
                        + ".json");
                helper.assertTrue(p != null, "missing parent model " + parent + " of " + id);
                checkTextures(helper, p, id, depth + 1);
            }
        }
    }

    @GameTest(template = "empty")
    public static void everyRecipeLoaded(GameTestHelper helper) {
        int expected = ContentData.read("summary.json").get(0).get("recipes").getAsInt();
        long loaded = helper.getLevel().getRecipeManager().getRecipes().stream()
                .filter(r -> r.id().getNamespace().equals(LotrPort.MOD_ID) && !r.id().getPath().startsWith("own/"))
                .count(); // own/ = hand-written recipes of our own features, not converted
        helper.assertTrue(loaded == expected, "loaded " + loaded + " of " + expected + " converted recipes");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void factionRecipeOnlyAtItsTable(GameTestHelper helper) {
        var level = helper.getLevel();
        RecipeHolder<FactionRecipe> sample = null;
        for (RecipeHolder<FactionRecipe> h : level.getRecipeManager().getAllRecipesFor(
                LotrRecipes.FACTION_CRAFTING.get())) {
            if (h.value().recipe() instanceof ShapedRecipe) {
                sample = h;
                break;
            }
        }
        helper.assertTrue(sample != null, "no shaped faction recipe loaded");
        ShapedRecipe shaped = (ShapedRecipe) sample.value().recipe();
        List<ItemStack> grid = new ArrayList<>();
        for (Ingredient ing : shaped.getIngredients()) {
            ItemStack[] options = ing.getItems();
            grid.add(options.length == 0 ? ItemStack.EMPTY : options[0].copy());
        }
        CraftingInput input = CraftingInput.of(shaped.getWidth(), shaped.getHeight(), grid);
        ResourceLocation table = sample.value().table();
        var atTable = FactionCraftingMenu.find(level, table, input);
        helper.assertTrue(atTable.isPresent() && atTable.get().value() instanceof FactionRecipe,
                "faction recipe " + sample.id() + " does not craft at " + table);
        var elsewhere = FactionCraftingMenu.find(level, ResourceLocation.withDefaultNamespace("crafting_table"),
                input);
        helper.assertTrue(elsewhere.isEmpty() || !(elsewhere.get().value() instanceof FactionRecipe),
                "faction recipe " + sample.id() + " also crafts at a plain table");
        helper.succeed();
    }

    private static JsonObject json(String path) {
        try (InputStream in = LotrPort.class.getResourceAsStream(path)) {
            return in == null ? null
                    : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
