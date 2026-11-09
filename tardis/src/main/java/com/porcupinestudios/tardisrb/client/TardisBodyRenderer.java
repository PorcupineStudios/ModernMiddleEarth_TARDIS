package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the loose police box ({@link TardisBody}) with the same model and looks as the block
 * ({@link ExteriorRenderer}), turned the way it faces, its doors swinging open and shut like the block's.
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
        float turn = Mth.rotLerp(partialTick, body.yRotO, body.getYRot());
        if (body.isVehicle() && !body.doorsOpen()) {
            // flown: the box spins as it flies, for show (its collision box doesn't turn)
            turn += (float) ((body.tickCount + partialTick) * TardisContent.tuning().flight().piloting().spin());
        }
        pose.mulPose(Axis.YP.rotationDegrees(180 - turn));
        float open = doorProgress(body, partialTick, model.swingTicks);
        float angle = model.swingDegrees * open * open * (3 - 2 * open);
        ExteriorRenderer.drawAll(model, angle, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(
                body))), light);
        if (look >= model.glowFromLook) {
            ExteriorRenderer.drawAll(model, angle, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(
                    ExteriorRenderer.GLOW)), LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
        super.render(body, yaw, partialTick, pose, buffers, light);
    }

    /** How far open the doors are drawn (0 shut, 1 open), moving toward the body's door state over the swing time. */
    private static float doorProgress(TardisBody body, float partialTick, float swingTicks) {
        float now = body.level().getGameTime() + partialTick;
        float delta = body.doorAnimTime < 0 ? swingTicks : Math.max(0, Math.min(swingTicks, now - body.doorAnimTime));
        body.doorAnimTime = now;
        float step = delta / Math.max(1, swingTicks);
        body.doorAnim = body.doorsDrawnOpen() ? Math.min(1, body.doorAnim + step) : Math.max(0, body.doorAnim - step);
        return body.doorAnim;
    }

    @Override
    public ResourceLocation getTextureLocation(TardisBody body) {
        return ExteriorRenderer.LOOKS[Math.max(0, Math.min(ExteriorRenderer.LOOKS.length - 1, body.look()))];
    }
}
