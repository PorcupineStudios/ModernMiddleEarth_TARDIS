package com.porcupinestudios.lotrport.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A recipe that only works at one faction's crafting table: a normal shaped or shapeless crafting recipe plus the id
 * of the table block. JSON: {@code {"type": "lotr_port:faction_crafting", "table": "<block id>", "recipe": {...}}}.
 */
public record FactionRecipe(ResourceLocation table, Recipe<CraftingInput> recipe) implements Recipe<CraftingInput> {
    @SuppressWarnings("unchecked")
    private static final Codec<Recipe<CraftingInput>> INNER = Recipe.CODEC.xmap(r -> (Recipe<CraftingInput>) r, r -> r);
    @SuppressWarnings("unchecked")
    private static final StreamCodec<RegistryFriendlyByteBuf, Recipe<CraftingInput>> INNER_STREAM =
            Recipe.STREAM_CODEC.map(r -> (Recipe<CraftingInput>) r, r -> r);

    public static final MapCodec<FactionRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("table").forGetter(FactionRecipe::table),
            INNER.fieldOf("recipe").forGetter(FactionRecipe::recipe))
            .apply(i, FactionRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionRecipe> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, FactionRecipe::table,
            INNER_STREAM, FactionRecipe::recipe,
            FactionRecipe::new);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return recipe.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return recipe.assemble(input, registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return recipe.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return recipe.getResultItem(registries);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return recipe.getRemainingItems(input);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return recipe.getIngredients();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LotrRecipes.FACTION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return LotrRecipes.FACTION_CRAFTING.get();
    }

    @Override
    public boolean isSpecial() {
        return true; // keeps it out of the vanilla recipe book
    }
}
