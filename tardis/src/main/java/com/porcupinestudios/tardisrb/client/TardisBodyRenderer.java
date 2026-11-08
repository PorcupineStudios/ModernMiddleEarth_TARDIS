package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the loose police box ({@link TardisBody}) with the same model and looks as the block
 * ({@link ExteriorRenderer}), doors shut, turned the way it faces.
 */
public final class TardisBodyRenderer extends EntityRenderer<TardisBody> {
    public TardisBodyRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.6F;
    }

    @Override
    public void render(TardisBody body, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        PoliceBoxModel model = PoliceBoxModel.get();
        int look = Math.max(0, Math.min(ExteriorRenderer.LOOKS.length - 1, body.look()));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, body.yRotO, body.getYRot())));
        ExteriorRenderer.drawAll(model, 0, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(
                body))), light);
        if (look >= model.glowFromLook) {
            ExteriorRenderer.drawAll(model, 0, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(
                    ExteriorRenderer.GLOW)), LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
        super.render(body, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TardisBody body) {
        return ExteriorRenderer.LOOKS[Math.max(0, Math.min(ExteriorRenderer.LOOKS.length - 1, body.look()))];
    }
}
