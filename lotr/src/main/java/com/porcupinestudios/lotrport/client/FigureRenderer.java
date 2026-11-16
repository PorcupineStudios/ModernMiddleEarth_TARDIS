package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.figure.Figure;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws a statue or rug on its LOTR creature model in the rest pose; rugs lie flat, face down. */
public class FigureRenderer extends EntityRenderer<Figure> {
    public FigureRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(Figure figure, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        CreatureModel model = CreatureRenderer.model(figure.model());
        if (model == null) {
            return;
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - figure.getYRot()));
        if (figure.flat()) {
            // laid out on the floor: belly down, flattened like a skin
            pose.translate(0, 0.05, 0);
            pose.scale(1f, 0.12f, 1f);
        }
        pose.scale(-1f, -1f, 1f); // a living renderer's model frame
        pose.translate(0, -1.501, 0);
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(figure)));
        model.renderRest(pose, buffer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(figure, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Figure figure) {
        return LotrPort.id("textures/entity/" + figure.texture());
    }
}
