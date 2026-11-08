package com.porcupinestudios.tardisrb.entity;

import com.porcupinestudios.tardisrb.TardisRB;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's entities: the police box when it is loose ({@link TardisBody}). */
public final class TardisEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TardisRB.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TardisBody>> BODY = ENTITIES.register("tardis_body",
            () -> EntityType.Builder.<TardisBody>of(TardisBody::new, MobCategory.MISC).sized(1.0F, 2.0F)
                    .clientTrackingRange(10).updateInterval(1).fireImmune().build("tardis_body"));

    private TardisEntities() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
