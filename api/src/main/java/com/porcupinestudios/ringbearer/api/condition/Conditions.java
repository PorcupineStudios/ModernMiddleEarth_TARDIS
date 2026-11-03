package com.porcupinestudios.ringbearer.api.condition;

import com.mojang.serialization.Codec;
import com.porcupinestudios.ringbearer.api.RingBearerApi;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * The condition type registry, its codec, and the built-in types. Mods register their own types with a
 * {@link DeferredRegister} on {@link #REGISTRY_KEY}, for example {@code lotr_port:alignment}.
 */
public final class Conditions {
    public static final ResourceKey<Registry<ConditionType<?>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(RingBearerApi.id("condition_type"));
    public static final Registry<ConditionType<?>> REGISTRY = new RegistryBuilder<>(REGISTRY_KEY).create();

    /** Reads and writes any condition: {@code {"type": "<id>", ...fields of that type}}. */
    public static final Codec<Condition> CODEC = Codec.lazyInitialized(
            () -> REGISTRY.byNameCodec().dispatch(Condition::type, ConditionType::codec));

    private static final DeferredRegister<ConditionType<?>> TYPES =
            DeferredRegister.create(REGISTRY_KEY, RingBearerApi.MOD_ID);

    public static final DeferredHolder<ConditionType<?>, ConditionType<BuiltinConditions.Always>> ALWAYS =
            TYPES.register("always", () -> new ConditionType<>(BuiltinConditions.Always.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<BuiltinConditions.All>> ALL =
            TYPES.register("all", () -> new ConditionType<>(BuiltinConditions.All.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<BuiltinConditions.Any>> ANY =
            TYPES.register("any", () -> new ConditionType<>(BuiltinConditions.Any.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<BuiltinConditions.Not>> NOT =
            TYPES.register("not", () -> new ConditionType<>(BuiltinConditions.Not.CODEC));

    private Conditions() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((NewRegistryEvent event) -> event.register(REGISTRY));
        TYPES.register(modBus);
    }
}
