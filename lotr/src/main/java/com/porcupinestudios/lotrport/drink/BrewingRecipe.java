package com.porcupinestudios.lotrport.drink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.RecipeMatcher;

/**
 * A keg recipe ({@code lotr_port:brewing}, LOTR's LOTRBrewingRecipes): its ingredients in the keg's six slots in any
 * order, {@code water} mB of water; makes {@code result} (its count in servings) weak, one strength stronger every
 * {@code brew_time} ticks.
 */
public record BrewingRecipe(List<Ingredient> ingredients, ItemStack result, int water, int brewTime)
        implements Recipe<RecipeInput> {
    public static final MapCodec<BrewingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.LIST_CODEC_NONEMPTY.fieldOf("ingredients").forGetter(BrewingRecipe::ingredients),
            // not STRICT_CODEC: the count is servings, more than a drink's stack of 1
            ItemStack.CODEC.fieldOf("result").forGetter(BrewingRecipe::result),
            Codec.INT.optionalFieldOf("water", 3000).forGetter(BrewingRecipe::water),
            Codec.INT.optionalFieldOf("brew_time", 12000).forGetter(BrewingRecipe::brewTime))
            .apply(i, BrewingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, BrewingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), BrewingRecipe::ingredients,
            ItemStack.STREAM_CODEC, BrewingRecipe::result,
            ByteBufCodecs.VAR_INT, BrewingRecipe::water,
            ByteBufCodecs.VAR_INT, BrewingRecipe::brewTime,
            BrewingRecipe::new);

    @Override
    public boolean matches(RecipeInput input, Level level) {
        List<ItemStack> given = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            if (!input.getItem(i).isEmpty()) {
                given.add(input.getItem(i));
            }
        }
        return given.size() == ingredients.size() && RecipeMatcher.findMatches(given, ingredients) != null;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LotrRecipes.BREWING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return LotrRecipes.BREWING.get();
    }

    /** Not in the recipe book (it has no keg tab). */
    @Override
    public boolean isSpecial() {
        return true;
    }
}
