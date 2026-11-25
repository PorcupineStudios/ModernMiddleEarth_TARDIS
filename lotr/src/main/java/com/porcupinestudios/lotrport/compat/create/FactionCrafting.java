package com.porcupinestudios.lotrport.compat.create;

import com.porcupinestudios.lotrport.crafting.FactionRecipe;
import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInput;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/** What a crafter grid makes when Create found no recipe: the faction table recipes of its faction crafters. */
public final class FactionCrafting {
    private FactionCrafting() {}

    /** The tables of every faction crafter in the grid {@code crafter} belongs to. */
    public static Set<ResourceLocation> tables(MechanicalCrafterBlockEntity crafter) {
        Set<ResourceLocation> tables = new HashSet<>();
        List<MechanicalCrafterBlockEntity> chain = RecipeGridHandler.getAllCraftersOfChain(crafter);
        for (MechanicalCrafterBlockEntity be : chain == null ? List.of(crafter) : chain) {
            if (be.getBlockState().getBlock() instanceof FactionCrafterBlock b) {
                tables.add(b.table());
            }
        }
        return tables;
    }

    /** The result, or null as Create expects when nothing matches. */
    public static ItemStack craft(MechanicalCrafterBlockEntity crafter, Level level,
            RecipeGridHandler.GroupedItems items) {
        Set<ResourceLocation> tables = tables(crafter);
        if (tables.isEmpty()) {
            return null;
        }
        CraftingInput input = MechanicalCraftingInput.of(items);
        for (RecipeHolder<FactionRecipe> holder : level.getRecipeManager().getAllRecipesFor(
                LotrRecipes.FACTION_CRAFTING.get())) {
            FactionRecipe r = holder.value();
            // recipes that need a player's alignment (the crafters themselves) are never made by machines
            if (tables.contains(r.table()) && r.alignment().isEmpty() && r.matches(input, level)) {
                ItemStack out = r.assemble(input, level.registryAccess());
                return out.isEmpty() ? null : out;
            }
        }
        return null;
    }
}
