package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.entity.TardisEntities;
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
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Client setup. Never referenced from common code. */
@EventBusSubscriber(modid = TardisRB.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TardisClient {
    private TardisClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TardisBlocks.WINDER_BE.get(), ShaftRenderer::new);
        event.registerBlockEntityRenderer(TardisBlocks.CONSOLE_BE.get(), ConsoleRenderer::new);
        event.registerBlockEntityRenderer(TardisBlocks.EXTERIOR_BE.get(), ExteriorRenderer::new);
        event.registerBlockEntityRenderer(TardisBlocks.REPAIR_CORE_BE.get(), RepairCoreRenderer::new);
        event.registerEntityRenderer(TardisEntities.BODY.get(), TardisBodyRenderer::new);
        event.registerEntityRenderer(TardisEntities.WORMHOLE.get(), WormholeRenderer::new);
        event.registerEntityRenderer(TardisEntities.ARC.get(), ArcRenderer::new);
        event.registerEntityRenderer(TardisEntities.DEMATERIALISING.get(), DematerialisingRenderer::new);
        event.registerEntityRenderer(TardisEntities.PILOT_CAMERA.get(),
                net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    /** The time vortex's sky settings (its tunnel is drawn by {@link VortexRenderer}); space's (no clouds). */
    @SubscribeEvent
    public static void skies(RegisterDimensionSpecialEffectsEvent event) {
        event.register(TardisRB.id("time_vortex"), new VortexRenderer.Effects());
        event.register(TardisRB.id("space"), new SpaceSky());
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(PilotClient.UP);
        event.register(PilotClient.SETTLE);
        event.register(PilotClient.DOWN);
        event.register(ClientSpacePhysics.ROLL_LEFT);
        event.register(ClientSpacePhysics.ROLL_RIGHT);
        event.register(ClientSpacePhysics.ALIGN);
    }

    @SubscribeEvent
    public static void reloads(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            PoliceBoxModel.reset();
            ConsoleModel.reset();
            ControlModels.reset();
            RepairCoreRenderer.reset();
        });
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        WorkshopNet.clientHandler = (view, ctx) -> WorkshopScreen.show(view);
        SkyNet.clientHandler = GlobeRenderer::set;
        SkyNet.clientMapHandler = GlobeRenderer::setMap;
    }
}
