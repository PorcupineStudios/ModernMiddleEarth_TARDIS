package com.porcupinestudios.lotrport.land;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The planted banner entity. */
public final class LandEntities {
    private static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LotrPort.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ClaimBanner>> BANNER = TYPES.register(
            "claim_banner", () -> EntityType.Builder.<ClaimBanner>of(ClaimBanner::new, MobCategory.MISC)
                    .sized(0.5f, 2.6f).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE)
                    .build(LotrPort.MOD_ID + ":claim_banner"));

    private LandEntities() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        Claims.register();
        Protection.register();
    }
}
