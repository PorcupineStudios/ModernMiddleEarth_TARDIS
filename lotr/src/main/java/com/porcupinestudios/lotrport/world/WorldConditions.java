package com.porcupinestudios.lotrport.world;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.ringbearer.api.condition.ConditionType;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Condition types about the world: {@code lotr_port:middle_earth_world} holds when the overworld itself is
 * Middle-earth (the Middle-earth world type). The TARDIS uses it to offer the vanilla Overworld
 * ({@code lotr_port:vanilla_overworld}) only where the real one was replaced.
 */
public final class WorldConditions {
    private static final DeferredRegister<ConditionType<?>> TYPES =
            DeferredRegister.create(Conditions.REGISTRY_KEY, LotrPort.MOD_ID);

    public static final DeferredHolder<ConditionType<?>, ConditionType<MiddleEarthWorld>> MIDDLE_EARTH_WORLD =
            TYPES.register("middle_earth_world", () -> new ConditionType<>(MiddleEarthWorld.CODEC));

    private WorldConditions() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    /** {@code {"type": "lotr_port:middle_earth_world"}}. */
    public record MiddleEarthWorld() implements Condition {
        public static final MapCodec<MiddleEarthWorld> CODEC = MapCodec.unit(new MiddleEarthWorld());

        @Override
        public ConditionType<?> type() {
            return MIDDLE_EARTH_WORLD.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return LotrDimensions.overworldIsMiddleEarth(context.level().getServer());
        }
    }
}
