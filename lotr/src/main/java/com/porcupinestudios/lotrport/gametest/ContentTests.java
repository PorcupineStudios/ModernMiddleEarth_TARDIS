package com.porcupinestudios.lotrport.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.content.LotrBowItem;
import com.porcupinestudios.lotrport.content.LotrCrossbowItem;
import com.porcupinestudios.lotrport.content.LotrWeaponItem;
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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.ItemAbilities;
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

    /**
     * Follows the model, its lotr_port parents, the parts of a separate_transforms model (LOTR's large in-hand icons)
     * and its override models (bow pull frames), and checks each lotr_port texture file exists.
     */
    private static void checkTextures(GameTestHelper helper, JsonObject model, String id, int depth) {
        if (model.has("base")) {
            checkTextures(helper, model.getAsJsonObject("base"), id, depth);
        }
        if (model.has("perspectives")) {
            for (Map.Entry<String, JsonElement> p : model.getAsJsonObject("perspectives").entrySet()) {
                checkTextures(helper, p.getValue().getAsJsonObject(), id, depth);
            }
        }
        if (depth == 0 && model.has("overrides")) {
            for (JsonElement o : model.getAsJsonArray("overrides")) {
                String ref = o.getAsJsonObject().get("model").getAsString();
                JsonObject m = json("/assets/lotr_port/models/" + ref.substring(LotrPort.MOD_ID.length() + 1) + ".json");
                helper.assertTrue(m != null, "missing override model " + ref + " of " + id);
                checkTextures(helper, m, id, 1);
            }
        }
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
                .filter(r -> r.id().getNamespace().equals(LotrPort.MOD_ID) && !r.id().getPath().startsWith("own/")
                        && !r.id().getPath().startsWith("create/"))
                .count(); // own/ = our own features' recipes; create/ = cross-mod recipes that need Create
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

    /**
     * 1.7.10 -> 1.21.1 combat: LOTR's melee timing, reach and fractional damage arrive as attributes (a dagger swings
     * twice a second and reaches 0.75 less, a pike 0.67 and 3 more), only swords sweep, bows and crossbows are real.
     */
    @GameTest(template = "empty")
    public static void weaponsUseLotrCombatNumbers(GameTestHelper helper) {
        ItemStack dagger = new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("angmar_dagger")));
        helper.assertTrue(dagger.getItem() instanceof LotrWeaponItem, "angmar_dagger is not a LOTR weapon");
        helper.assertTrue(near(modifier(dagger, Attributes.ATTACK_DAMAGE), 3.5), "dagger damage lost its .5");
        helper.assertTrue(near(modifier(dagger, Attributes.ATTACK_SPEED) + 4.0, 2.0), "dagger attack speed");
        helper.assertTrue(near(modifier(dagger, Attributes.ENTITY_INTERACTION_RANGE), -0.75), "dagger reach");
        helper.assertTrue(!dagger.canPerformAction(ItemAbilities.SWORD_SWEEP), "daggers must not sweep");
        ItemStack pike = new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("gondorian_pike")));
        helper.assertTrue(pike.getItem() instanceof LotrWeaponItem, "gondorian_pike is not a LOTR weapon");
        helper.assertTrue(near(modifier(pike, Attributes.ENTITY_INTERACTION_RANGE), 3.0), "pike reach");
        helper.assertTrue(near(modifier(pike, Attributes.ATTACK_SPEED) + 4.0, 0.667), "pike attack speed");
        ItemStack axe = new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("dwarven_battleaxe")));
        helper.assertTrue(axe.canPerformAction(ItemAbilities.SWORD_SWEEP), "battleaxes sweep");
        helper.assertTrue(modifier(axe, Attributes.SWEEPING_DAMAGE_RATIO) > 0, "battleaxes sweep harder than swords");
        boolean bow = false;
        boolean crossbow = false;
        for (Item item : BuiltInRegistries.ITEM) {
            bow |= item instanceof LotrBowItem;
            crossbow |= item instanceof LotrCrossbowItem;
        }
        helper.assertTrue(bow && crossbow, "LOTR bows and crossbows are plain items");
        helper.succeed();
    }

    private static double modifier(ItemStack stack, Holder<Attribute> attribute) {
        double[] sum = {0};
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        mods.forEach(EquipmentSlot.MAINHAND, (a, m) -> {
            if (a.equals(attribute)) {
                sum[0] += m.amount();
            }
        });
        return sum[0];
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.01;
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
