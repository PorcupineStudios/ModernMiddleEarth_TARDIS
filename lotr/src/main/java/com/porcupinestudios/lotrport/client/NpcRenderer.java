package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws an NPC with its skin, held items and armour; its size comes from the scale attribute. */
public class NpcRenderer extends HumanoidMobRenderer<LotrNpc, HumanoidModel<LotrNpc>> {
    private static final ResourceLocation FALLBACK =
            ResourceLocation.fromNamespaceAndPath("lotr_port", "textures/entity/npc_fallback.png");
    private final HumanoidModel<LotrNpc> classic;
    private final HumanoidModel<LotrNpc> tall;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(LotrClient.NPC_LAYER)), 0.5f);
        classic = model;
        tall = new HumanoidModel<>(context.bakeLayer(LotrClient.NPC_TALL_LAYER));
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public void render(LotrNpc npc, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        model = npc.tallSkin() ? tall : classic; // same shape, the skin's height decides the texture mapping
        super.render(npc, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LotrNpc npc) {
        ResourceLocation skin = npc.skin().isEmpty() ? null : ResourceLocation.tryParse(npc.skin());
        return skin == null ? FALLBACK : skin;
    }
}
