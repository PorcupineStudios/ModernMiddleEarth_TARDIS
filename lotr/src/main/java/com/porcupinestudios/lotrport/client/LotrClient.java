package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.npc.LotrEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client setup: entity renderers and model layers. Never referenced from common code. */
@EventBusSubscriber(modid = LotrPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LotrClient {
    /** LOTR people: the classic humanoid, for 64x32 skins and for 64x64 ones. */
    public static final ModelLayerLocation NPC_LAYER = new ModelLayerLocation(LotrPort.id("npc"), "main");
    public static final ModelLayerLocation NPC_TALL_LAYER = new ModelLayerLocation(LotrPort.id("npc"), "tall");

    private LotrClient() {}

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NPC_LAYER,
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 32));
        event.registerLayerDefinition(NPC_TALL_LAYER,
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64));
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(LotrEntities.NPC.get(), NpcRenderer::new);
    }
}
