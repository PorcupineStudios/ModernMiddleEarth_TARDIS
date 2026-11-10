package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.entity.Wormhole;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A wormhole (Aaron 2026-11-09): an opaque disc facing along the tunnel, painted with the vortex texture wound into a
 * spiral that turns inward, dark in the middle, with a glowing rim. When it is see-through ({@link Wormhole#open}) an
 * Immersive Portals portal fills the middle, so only the rim is drawn.
 */
final class WormholeRenderer extends EntityRenderer<Wormhole> {
    private static final ResourceLocation TEXTURE = TardisRB.id("textures/environment/time_vortex.png");
    private static final int RINGS = 10;
    private static final int SEGMENTS = 48;

    WormholeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Wormhole w, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = (w.tickCount + partialTick) / 20F;
        float r = w.radius();
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-w.getYRot())); // a rift faces the way the box comes out
        PoseStack.Pose p = pose.last();
        VertexConsumer disc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        float inner = w.open() ? 0.86F : 0; // the portal fills the middle 0.9
        for (int i = 0; i < RINGS; i++) {
            float f0 = Math.max(inner, i / (float) RINGS);
            float f1 = Math.max(inner, (i + 1) / (float) RINGS);
            if (f1 <= f0) {
                continue;
            }
            for (int j = 0; j < SEGMENTS; j++) {
                float a0 = j * Mth.TWO_PI / SEGMENTS;
                float a1 = (j + 1) * Mth.TWO_PI / SEGMENTS;
                vertex(disc, p, r, f0, a0, t);
                vertex(disc, p, r, f1, a0, t);
                vertex(disc, p, r, f1, a1, t);
                vertex(disc, p, r, f0, a1, t);
            }
        }
        // the glowing rim, flickering
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        for (int j = 0; j < SEGMENTS; j++) {
            float a0 = j * Mth.TWO_PI / SEGMENTS;
            float a1 = (j + 1) * Mth.TWO_PI / SEGMENTS;
            float flick = 0.6F + 0.4F * Mth.sin(t * 9 + j * 1.7F);
            glowVertex(glow, p, r * 0.95F, a0, 0.55F * flick);
            glowVertex(glow, p, r * 1.35F, a0, 0);
            glowVertex(glow, p, r * 1.35F, a1, 0);
            glowVertex(glow, p, r * 0.95F, a1, 0.55F * flick);
        }
        pose.popPose();
        super.render(w, yaw, partialTick, pose, buffers, light);
    }

    /** A point on the disc at {@code f} (0 middle, 1 rim) and angle {@code a}: the texture winds inward as it turns. */
    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float r, float f, float a, float t) {
        float shade = 0.15F + 0.85F * (float) Math.pow(f, 0.8);
        vc.addVertex(p, Mth.cos(a) * r * f, Mth.sin(a) * r * f, 0)
                .setColor(shade * 0.8F, shade * 0.7F, shade, 1)
                .setUv(a / Mth.TWO_PI * 2 + f * 1.4F - t * 0.35F, f * 0.8F + t * 0.12F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(p, 0, 0, -1);
    }

    private static void glowVertex(VertexConsumer vc, PoseStack.Pose p, float r, float a, float alpha) {
        vc.addVertex(p, Mth.cos(a) * r, Mth.sin(a) * r, 0).setColor(0.55F, 0.6F, 1F, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(Wormhole w) {
        return TEXTURE;
    }
}
