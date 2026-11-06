package com.porcupinestudios.lotrport.compat.jei;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.crafting.FactionRecipe;
import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import com.porcupinestudios.lotrport.crafting.MachineRecipe;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * JEI support (optional: JEI loads this only when it is installed). Faction crafting tables get one category, where
 * each recipe also shows the table it needs. Each LOTR machine (alloy forges, millstone, barrel, Ent jar, unsmeltery)
 * gets its own category, with its blocks as catalysts. Ordinary converted recipes (crafting, smelting) already show
 * in JEI's vanilla categories.
 */
@JeiPlugin
public final class LotrJeiPlugin implements IModPlugin {
    static final RecipeType<RecipeHolder<FactionRecipe>> FACTION =
            RecipeType.createRecipeHolderType(LotrPort.id("faction_crafting"));
    static final Map<MachineRecipe.Machine, RecipeType<RecipeHolder<MachineRecipe>>> MACHINES =
            new EnumMap<>(MachineRecipe.Machine.class);
    /** The blocks that run each machine's recipes (shown as catalysts). */
    private static final Map<MachineRecipe.Machine, List<String>> MACHINE_BLOCKS = Map.of(
            MachineRecipe.Machine.ALLOY_FORGE, List.of("alloy_forge", "dwarven_forge", "elven_forge", "orc_forge"),
            MachineRecipe.Machine.MILLSTONE, List.of("millstone"),
            MachineRecipe.Machine.BREWING, List.of("barrel"),
            MachineRecipe.Machine.ENT_JAR, List.of("ent_jar"),
            MachineRecipe.Machine.UNSMELTING, List.of("unsmeltery"));

    static {
        for (MachineRecipe.Machine m : MachineRecipe.Machine.values()) {
            MACHINES.put(m, RecipeType.createRecipeHolderType(LotrPort.id(m.id)));
        }
    }

    @Override
    public ResourceLocation getPluginUid() {
        return LotrPort.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new FactionCraftingCategory(gui, icon("gondorian_crafting_table")));
        for (MachineRecipe.Machine m : MachineRecipe.Machine.values()) {
            registration.addRecipeCategories(new MachineCategory(gui, m, MACHINES.get(m),
                    icon(MACHINE_BLOCKS.get(m).get(0))));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager manager = recipeManager();
        if (manager == null) {
            return;
        }
        registration.addRecipes(FACTION, manager.getAllRecipesFor(LotrRecipes.FACTION_CRAFTING.get()));
        for (MachineRecipe.Machine m : MachineRecipe.Machine.values()) {
            registration.addRecipes(MACHINES.get(m), manager.getAllRecipesFor(LotrRecipes.MACHINE_TYPES.get(m).get()));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        RecipeManager manager = recipeManager();
        if (manager != null) {
            Set<ResourceLocation> tables = new LinkedHashSet<>();
            for (RecipeHolder<FactionRecipe> h : manager.getAllRecipesFor(LotrRecipes.FACTION_CRAFTING.get())) {
                tables.add(h.value().table());
            }
            for (ResourceLocation t : tables) {
                Item item = BuiltInRegistries.ITEM.get(t);
                if (item != Items.AIR) {
                    registration.addRecipeCatalysts(FACTION, item);
                }
            }
        }
        for (MachineRecipe.Machine m : MachineRecipe.Machine.values()) {
            List<Item> blocks = new ArrayList<>();
            for (String id : MACHINE_BLOCKS.get(m)) {
                Item item = BuiltInRegistries.ITEM.get(LotrPort.id(id));
                if (item != Items.AIR) {
                    blocks.add(item);
                }
            }
            registration.addRecipeCatalysts(MACHINES.get(m), blocks.toArray(Item[]::new));
        }
    }

    private static RecipeManager recipeManager() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getRecipeManager();
    }

    static Item icon(String id) {
        Item item = BuiltInRegistries.ITEM.get(LotrPort.id(id));
        return item == Items.AIR ? Items.CRAFTING_TABLE : item;
    }
}
