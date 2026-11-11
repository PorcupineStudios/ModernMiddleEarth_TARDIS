package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item data components. LOTR kept a drink's strength and vessel in the item's damage value (138 metadata variants);
 * here a drink is one item and these components carry them.
 */
public final class LotrComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LotrPort.MOD_ID);

    /** Index into {@code drinks.json} strengths (weak, light, moderate, strong, potent). */
    public static final Supplier<DataComponentType<Integer>> DRINK_STRENGTH = COMPONENTS.registerComponentType(
            "drink_strength", b -> b.persistent(Codec.intRange(0, 15)).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The vessel a drink is in ({@code drinks.json} vessels: mug, goblet_gold, skull...). */
    public static final Supplier<DataComponentType<String>> VESSEL = COMPONENTS.registerComponentType(
            "vessel", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** The building type a Building Writ makes (none: the best type that fits). */
    public static final Supplier<DataComponentType<net.minecraft.resources.ResourceLocation>> BUILDING_TYPE =
            COMPONENTS.registerComponentType("building_type", b -> b.persistent(
                    net.minecraft.resources.ResourceLocation.CODEC).networkSynchronized(
                    net.minecraft.resources.ResourceLocation.STREAM_CODEC));

    private LotrComponents() {}

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
