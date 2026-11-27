package com.porcupinestudios.ringbearer.api.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client entry: the mod's Config button in the Mods list opens NeoForge's config screen for all its configs. */
@Mod(value = com.porcupinestudios.ringbearer.api.RingBearerApi.MOD_ID, dist = Dist.CLIENT)
public final class ApiConfigScreen {
    public ApiConfigScreen(net.neoforged.bus.api.IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        com.porcupinestudios.ringbearer.api.client.bugreport.BugReportClient.register(modBus, container);
    }
}
