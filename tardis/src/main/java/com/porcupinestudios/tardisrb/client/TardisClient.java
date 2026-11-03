package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.net.ConsoleNet;
import com.porcupinestudios.tardisrb.net.WorkshopNet;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client setup. Never referenced from common code. */
@EventBusSubscriber(modid = TardisRB.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TardisClient {
    private TardisClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TardisBlocks.WINDER_BE.get(), ShaftRenderer::new);
        event.registerBlockEntityRenderer(TardisBlocks.CONSOLE_BE.get(), ConsoleRenderer::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ConsoleRenderer.LAYER, ConsoleRenderer::layer);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        ConsoleNet.clientHandler = (view, ctx) -> ConsoleScreen.show(view);
        WorkshopNet.clientHandler = (view, ctx) -> WorkshopScreen.show(view);
    }
}
