package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.tardisrb.entity.VortexArc;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A lightning arc across the vortex tunnel ({@link VortexArc}): a steady glowing line where it hurts, with bright
 * forked strands crackling along it, reshaped every 2 ticks. The line itself never moves.
 */
final class ArcRenderer extends EntityRenderer<VortexArc> {
    ArcRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(VortexArc arc, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        Vector3f half = arc.half();
        Vector3f a = new Vector3f(half).negate();
        Vec3 c = entityRenderDispatcher.camera.getPosition().subtract(arc.getPosition(partialTick));
        Vector3f cam = new Vector3f((float) c.x, (float) c.y, (float) c.z);
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        int frame = arc.tickCount / 2;
        float t = arc.tickCount + partialTick;
        float pulse = 0.75F + 0.25F * Mth.sin(t * 0.9F);
        // the steady glow along the line: this is where it hurts
        ribbon(vc, m, cam, a, half, arc.thickness() * 2.4F, 0.35F, 0.45F, 1F, 0.35F * pulse);
        ribbon(vc, m, cam, a, half, arc.thickness() * 0.8F, 0.6F, 0.7F, 1F, 0.5F * pulse);
        // crackling strands, jumping about the line
        float length = half.length() * 2;
        int n = Math.max(8, (int) (length / 0.6F));
        Vector3f across = new Vector3f(half).cross(0, 0, 1).normalize();
        Vector3f along = new Vector3f(0, 0, 1);
        for (int s = 0; s < 3; s++) {
            RandomSource r = RandomSource.create(arc.getId() * 7919L + frame * 131L + s * 17L);
            float jitter = s == 0 ? 0.15F : 0.4F;
            Vector3f prev = new Vector3f(a);
            for (int i = 1; i <= n; i++) {
                Vector3f p = new Vector3f(a).lerp(half, i / (float) n);
                if (i < n) {
                    p.add(new Vector3f(across).mul((float) r.nextGaussian() * jitter))
                            .add(new Vector3f(along).mul((float) r.nextGaussian() * jitter));
                }
                float fade = s == 0 ? 0.95F : 0.6F + 0.3F * r.nextFloat();
                ribbon(vc, m, cam, prev, p, 0.11F, 0.95F, 0.97F, 1F, fade);
                ribbon(vc, m, cam, prev, p, 0.4F, 0.5F, 0.6F, 1F, 0.4F * fade);
                prev = p;
            }
        }
        super.render(arc, yaw, partialTick, pose, buffers, light);
    }

    /** A flat strip from {@code p0} to {@code p1}, {@code width} across, turned to face the camera (both sides). */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vector3f cam, Vector3f p0, Vector3f p1, float width,
            float r, float g, float b, float alpha) {
        Vector3f dir = new Vector3f(p1).sub(p0);
        Vector3f view = new Vector3f(p0).add(p1).mul(0.5F).sub(cam);
        Vector3f side = dir.cross(view);
        if (side.lengthSquared() < 1.0E-8F) {
            return;
        }
        side.normalize().mul(width / 2);
        Vector3f[] q = {new Vector3f(p0).sub(side), new Vector3f(p0).add(side), new Vector3f(p1).add(side),
                new Vector3f(p1).sub(side)};
        for (int k : new int[] {0, 1, 2, 3, 3, 2, 1, 0}) {
            vc.addVertex(m, q[k].x, q[k].y, q[k].z).setColor(r, g, b, alpha);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(VortexArc arc) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
