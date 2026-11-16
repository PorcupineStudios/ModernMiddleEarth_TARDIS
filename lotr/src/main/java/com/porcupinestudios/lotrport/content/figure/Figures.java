package com.porcupinestudios.lotrport.content.figure;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The figure entity type (statues and rugs). */
public final class Figures {
    private static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LotrPort.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<Figure>> TYPE = TYPES.register("figure",
            () -> EntityType.Builder.<Figure>of(Figure::new, MobCategory.MISC).sized(1.0f, 1.0f)
                    .clientTrackingRange(10).updateInterval(Integer.MAX_VALUE).build(LotrPort.MOD_ID + ":figure"));

    private Figures() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
