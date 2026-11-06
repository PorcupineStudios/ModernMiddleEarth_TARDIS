package com.porcupinestudios.lotrport.compat.jei;

import com.porcupinestudios.lotrport.crafting.MachineRecipe;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One LOTR machine's recipes: inputs in a row (any order), an arrow, the result. A forge-specific recipe shows its
 * forge under the arrow; a millstone chance below 100% and a brewing time are written under the result.
 */
final class MachineCategory extends AbstractRecipeCategory<RecipeHolder<MachineRecipe>> {
    private static final int MAX_INPUTS = 6;
    private final IGuiHelper gui;
    private final int inputWidth;

    MachineCategory(IGuiHelper gui, MachineRecipe.Machine machine, RecipeType<RecipeHolder<MachineRecipe>> type,
            Item icon) {
        super(type, Component.translatableWithFallback("jei.lotr_port." + machine.id, title(machine)),
                gui.createDrawableItemLike(icon), inputs(machine) * 18 + 60, 44);
        this.gui = gui;
        this.inputWidth = inputs(machine) * 18;
    }

    private static int inputs(MachineRecipe.Machine machine) {
        return switch (machine) {
            case BREWING -> MAX_INPUTS;
            case ALLOY_FORGE -> 2;
            default -> 1;
        };
    }

    private static String title(MachineRecipe.Machine machine) {
        return switch (machine) {
            case ALLOY_FORGE -> "Forge";
            case MILLSTONE -> "Millstone";
            case BREWING -> "Barrel";
            case ENT_JAR -> "Ent Jar";
            case UNSMELTING -> "Unsmeltery";
        };
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
        MachineRecipe recipe = holder.value();
        builder.setShapeless();
        for (int i = 0; i < recipe.ingredients().size() && i < MAX_INPUTS; i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + i * 18, 1).setStandardSlotBackground()
                    .addIngredients(recipe.ingredients().get(i));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, inputWidth + 39, 1).setOutputSlotBackground()
                .addItemStack(recipe.result());
        recipe.station().ifPresent(id -> builder.addSlot(RecipeIngredientRole.CATALYST, inputWidth + 9, 22)
                .addItemStacks(List.of(new ItemStack(BuiltInRegistries.ITEM.get(id)))));
    }

    @Override
    public void draw(RecipeHolder<MachineRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
            double mouseY) {
        gui.getRecipeArrow().draw(graphics, inputWidth + 6, 1);
        MachineRecipe recipe = holder.value();
        var font = Minecraft.getInstance().font;
        int y = 22;
        if (recipe.chance() < 1f) {
            graphics.drawString(font, Math.round(recipe.chance() * 100) + "%", inputWidth + 39, y, 0xFF808080, false);
            y += 10;
        }
        if (recipe.time() > 0) {
            graphics.drawString(font, recipe.time() / 20 + "s", inputWidth + 39, y, 0xFF808080, false);
        }
    }
}
