package com.porcupinestudios.lotrport.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A recipe for one of LOTR's machines (alloy forges, millstone, barrel, Ent jar, unsmeltery), converted from LOTR's
 * own registries by the exporter. The inputs match in any order, like a shapeless recipe. JSON:
 * <pre>{"type": "lotr_port:alloy_forge", "station": "lotr_port:dwarven_forge", "ingredients": [...],
 *  "result": {"id": ..., "count": n}, "chance": 1.0, "time": 200}</pre>
 * {@code station} limits a recipe to one block (each faction's forge smelts differently); {@code chance} is LOTR's
 * millstone chance; {@code time} the brewing or cooking time in ticks where LOTR had one. The machines' block
 * entities use these; until each machine's block lands (missing-features task 31) they load and are listed only.
 */
public record MachineRecipe(Machine machine, Optional<ResourceLocation> station, List<Ingredient> ingredients,
        ItemStack result, float chance, int time, int servings) implements Recipe<RecipeInput> {

    public enum Machine {
        ALLOY_FORGE("alloy_forge"), MILLSTONE("millstone"), BREWING("brewing"), ENT_JAR("ent_jar"),
        UNSMELTING("unsmelting");

        public final String id;

        Machine(String id) {
            this.id = id;
        }

        MapCodec<MachineRecipe> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                    ResourceLocation.CODEC.optionalFieldOf("station").forGetter(MachineRecipe::station),
                    Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").forGetter(MachineRecipe::ingredients),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(MachineRecipe::result),
                    Codec.FLOAT.optionalFieldOf("chance", 1f).forGetter(MachineRecipe::chance),
                    Codec.INT.optionalFieldOf("time", 0).forGetter(MachineRecipe::time),
                    Codec.INT.optionalFieldOf("servings", 1).forGetter(MachineRecipe::servings))
                    .apply(i, (s, in, r, c, t, n) -> new MachineRecipe(this, s, in, r, c, t, n)));
        }

        StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec() {
            return StreamCodec.composite(
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), MachineRecipe::station,
                    Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), MachineRecipe::ingredients,
                    ItemStack.STREAM_CODEC, MachineRecipe::result,
                    ByteBufCodecs.FLOAT, MachineRecipe::chance,
                    ByteBufCodecs.VAR_INT, MachineRecipe::time,
                    ByteBufCodecs.VAR_INT, MachineRecipe::servings,
                    (s, in, r, c, t, n) -> new MachineRecipe(this, s, in, r, c, t, n));
        }
    }

    /** The inputs, in any order, each used once; empty slots are ignored. */
    @Override
    public boolean matches(RecipeInput input, Level level) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            if (!input.getItem(i).isEmpty()) {
                items.add(input.getItem(i));
            }
        }
        if (items.size() != ingredients.size()) {
            return false;
        }
        List<Ingredient> left = new ArrayList<>(ingredients);
        for (ItemStack s : items) {
            boolean found = false;
            for (int j = 0; j < left.size(); j++) {
                if (left.get(j).test(s)) {
                    left.remove(j);
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
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
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> out = NonNullList.create();
        out.addAll(ingredients);
        return out;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LotrRecipes.MACHINE_SERIALIZERS.get(machine).get();
    }

    @Override
    public RecipeType<?> getType() {
        return LotrRecipes.MACHINE_TYPES.get(machine).get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }
}
