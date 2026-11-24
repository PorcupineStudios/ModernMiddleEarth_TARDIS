package com.porcupinestudios.ringbearer.harness.client;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client entry: the Config button in the Mods list opens NeoForge's config screen. */
@Mod(value = HarnessMod.MOD_ID, dist = Dist.CLIENT)
public final class HarnessClient {
    public HarnessClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
