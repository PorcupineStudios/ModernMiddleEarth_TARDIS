package com.porcupinestudios.lotrport.crafting;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.LotrPort;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipe types of the LOTR port. */
public final class LotrRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, LotrPort.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, LotrPort.MOD_ID);

    public static final Supplier<RecipeType<FactionRecipe>> FACTION_CRAFTING =
            TYPES.register("faction_crafting", () -> RecipeType.simple(LotrPort.id("faction_crafting")));
    public static final Supplier<RecipeSerializer<FactionRecipe>> FACTION_SERIALIZER =
            SERIALIZERS.register("faction_crafting", () -> new RecipeSerializer<>() {
                @Override
                public MapCodec<FactionRecipe> codec() {
                    return FactionRecipe.CODEC;
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf, FactionRecipe> streamCodec() {
                    return FactionRecipe.STREAM_CODEC;
                }
            });

    private LotrRecipes() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
    }
}
