package com.porcupinestudios.ringbearer.harness.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.Harnessing;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/** Draws {@link HarnessModel} on a horse-family animal that wears a harness, posed like the horse (as armour is). */
public class HarnessLayer<T extends AbstractHorse, M extends HorseModel<T>> extends RenderLayer<T, M> {
    public static final ResourceLocation TEXTURE = HarnessMod.id("textures/entity/harness.png");
    private final HorseModel<T> model;

    public HarnessLayer(RenderLayerParent<T, M> parent, ModelPart root) {
        super(parent);
        this.model = new HorseModel<>(root);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, T horse, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!Harnessing.wears(horse) || horse.isInvisible()) {
            return;
        }
        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(horse, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(horse, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light,
                LivingEntityRenderer.getOverlayCoords(horse, 0), -1);
    }
}
