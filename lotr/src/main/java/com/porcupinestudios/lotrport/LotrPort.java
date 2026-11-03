package com.porcupinestudios.lotrport;

import com.mojang.logging.LogUtils;
import com.porcupinestudios.lotrport.command.LotrCommand;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.lotrport.shared.SharedApiHooks;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(LotrPort.MOD_ID)
public final class LotrPort {
    public static final String MOD_ID = "lotr_port";
    public static final Logger LOG = LogUtils.getLogger();

    public LotrPort(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, LotrConfig.SPEC);
        LotrRegistries.register(modBus);
        SharedApiHooks.register(modBus);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> LotrCommand.register(event.getDispatcher()));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
