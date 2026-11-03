package com.porcupinestudios.ringbearer.api;

import com.mojang.logging.LogUtils;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import com.porcupinestudios.ringbearer.api.history.HistoryRecorder;
import com.porcupinestudios.ringbearer.api.provider.Providers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Shared API of the Ring Bearer pack. TARDIS: Ring Bearer and the LOTR port both depend on it and never on each other.
 * It holds three things: the world history log, condition types, and providers. It depends on nothing in either mod.
 */
@Mod(RingBearerApi.MOD_ID)
public final class RingBearerApi {
    public static final String MOD_ID = "ringbearer_api";
    public static final Logger LOG = LogUtils.getLogger();

    public RingBearerApi(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ApiConfig.SPEC);
        Conditions.register(modBus);
        Providers.register(modBus);
        HistoryRecorder.register(NeoForge.EVENT_BUS);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
