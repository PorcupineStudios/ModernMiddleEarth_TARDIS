package com.porcupinestudios.lotrport.npc;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types. One entity plays every NPC type from data. */
public final class LotrEntities {
    private static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LotrPort.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<LotrNpc>> NPC = TYPES.register("npc",
            () -> EntityType.Builder.<LotrNpc>of(LotrNpc::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(10)
                    .build(LotrPort.MOD_ID + ":npc"));

    /** Rocks, snowballs and wraith bolts LOTR's creatures throw. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.porcupinestudios.lotrport.npc.behaviour.LotrProjectile>>
            PROJECTILE = TYPES.register("projectile", () -> EntityType.Builder
                    .<com.porcupinestudios.lotrport.npc.behaviour.LotrProjectile>of(
                            com.porcupinestudios.lotrport.npc.behaviour.LotrProjectile::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(10)
                    .build(LotrPort.MOD_ID + ":projectile"));

    private LotrEntities() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) ->
                event.put(NPC.get(), LotrNpc.createAttributes().build()));
    }
}
