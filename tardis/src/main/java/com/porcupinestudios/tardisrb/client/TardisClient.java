package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.net.SkyNet;
import com.porcupinestudios.tardisrb.net.WorkshopNet;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Client setup. Never referenced from common code. */
@EventBusSubscriber(modid = TardisRB.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TardisClient {
    private TardisClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TardisBlocks.WINDER_BE.get(), ShaftRenderer::new);
        event.registerBlockEntityRenderer(TardisBlocks.CONSOLE_BE.get(), ConsoleRenderer::new);
        event.registerBlockEntityRenderer(TardisBlocks.EXTERIOR_BE.get(), ExteriorRenderer::new);
    }

    @SubscribeEvent
    public static void reloads(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            PoliceBoxModel.reset();
            ConsoleModel.reset();
            ControlModels.reset();
        });
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        WorkshopNet.clientHandler = (view, ctx) -> WorkshopScreen.show(view);
        SkyNet.clientHandler = GlobeRenderer::set;
        SkyNet.clientMapHandler = GlobeRenderer::setMap;
    }
}
