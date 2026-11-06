package com.porcupinestudios.lotrport.compat.jei;

import com.porcupinestudios.lotrport.crafting.FactionRecipe;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** A faction table recipe: the 3x3 grid, the result, and below the arrow the table it must be made at. */
final class FactionCraftingCategory extends AbstractRecipeCategory<RecipeHolder<FactionRecipe>> {
    private final IGuiHelper gui;

    FactionCraftingCategory(IGuiHelper gui, Item icon) {
        super(LotrJeiPlugin.FACTION, Component.translatableWithFallback("jei.lotr_port.faction_crafting",
                "Faction Crafting"), gui.createDrawableItemLike(icon), 116, 54);
        this.gui = gui;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FactionRecipe> holder, IFocusGroup focuses) {
        FactionRecipe recipe = holder.value();
        NonNullList<Ingredient> ings = recipe.getIngredients();
        int w = 3;
        if (recipe.recipe() instanceof ShapedRecipe shaped) {
            w = shaped.getWidth();
        } else {
            builder.setShapeless();
        }
        for (int i = 0; i < ings.size() && i < 9; i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % w) * 18, 1 + (i / w) * 18)
                    .setStandardSlotBackground().addIngredients(ings.get(i));
        }
        Level level = Minecraft.getInstance().level;
        ItemStack result = level == null ? ItemStack.EMPTY : recipe.getResultItem(level.registryAccess());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 19).setOutputSlotBackground().addItemStack(result);
        Item table = BuiltInRegistries.ITEM.get(recipe.table());
        builder.addSlot(RecipeIngredientRole.CATALYST, 61, 37).addItemStacks(List.of(new ItemStack(table)));
    }

    @Override
    public void draw(RecipeHolder<FactionRecipe> recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
            double mouseY) {
        gui.getRecipeArrow().draw(graphics, 60, 18);
    }
}
