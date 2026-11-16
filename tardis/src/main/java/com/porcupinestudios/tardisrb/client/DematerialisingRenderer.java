package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.porcupinestudios.tardisrb.entity.Dematerialising;
import com.porcupinestudios.tardisrb.flight.Materialise;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** The police box fading out where it stood, doors shut, with the pulsing fade ({@link Materialise#alpha}). */
public final class DematerialisingRenderer extends EntityRenderer<Dematerialising> {
    public DematerialisingRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(Dematerialising ghost, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        PoliceBoxModel model = PoliceBoxModel.get();
        int look = Math.max(0, Math.min(ExteriorRenderer.LOOKS.length - 1, ghost.look()));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - ghost.getYRot()));
        ExteriorRenderer.drawBox(model, look, 0, Materialise.alpha(ghost.progress(partialTick), true), pose, buffers,
                light);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(Dematerialising ghost) {
        return ExteriorRenderer.LOOKS[0];
    }
}
