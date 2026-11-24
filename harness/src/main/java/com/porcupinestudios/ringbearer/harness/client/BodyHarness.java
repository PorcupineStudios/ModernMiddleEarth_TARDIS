package com.porcupinestudios.ringbearer.harness.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.ringbearer.harness.content.Harnessing;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The harness on animals that aren't drawn by vanilla's horse renderer (the LOTR port's mounts, wargs, spiders,
 * Rhosgobel rabbits...): straps fitted to a box round the torso from the animal's harness fit ({@code body},
 * {@code collar}), turned with its body yaw. It doesn't bend with the model's animation (the body pitching as a
 * rabbit hops), so the bands sit a little proud of the torso. Same texture as {@link HarnessModel}.
 */
public final class BodyHarness {
    /** Material squares in {@code textures/entity/harness.png} (64x32): leather, brass, collar padding. */
    private static final float[] LEATHER = {0, 0}, BRASS = {32, 0}, PAD = {0, 16};
    private static final float STRAP = 0.07f, PROUD = 0.04f;

    private BodyHarness() {}

    public static void render(LivingEntity animal, LivingEntityRenderer<?, ?> renderer, float partialTick,
                              PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (renderer instanceof AbstractHorseRenderer<?, ?> || !Harnessing.wears(animal) || animal.isInvisible()) {
            return;
        }
        HarnessFits.Fit fit = HarnessFits.of(animal);
        if (fit.body().isEmpty()) {
            return;
        }
        double yaw = Math.toRadians(Mth.rotLerp(partialTick, animal.yBodyRotO, animal.yBodyRot));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Box b = new Box(poseStack.last().pose(), buffers.getBuffer(RenderType.entityCutoutNoCull(HarnessLayer.TEXTURE)),
                right, fwd, light, LivingEntityRenderer.getOverlayCoords(animal, 0));

        List<Double> t = fit.body();
        float half = (float) (t.get(0) / 2) + PROUD, bottom = t.get(1).floatValue() - PROUD,
                top = t.get(2).floatValue() + PROUD, rear = t.get(3).floatValue(), front = t.get(4).floatValue();
        float len = front - rear, tall = top - bottom;
        // girth behind the forelegs, breeching round the hindquarters (upper part), hip strap over the croup
        float girth = front - 0.3f * len;
        ring(b, half, bottom, top, girth, STRAP * 1.6f, LEATHER);
        ring(b, half, bottom + 0.45f * tall, top, rear + 0.12f, STRAP * 1.6f, LEATHER);
        // backpad on top, from the girth back a little; spine strap to the breeching
        b.box(-half * 0.45f, top, girth - 0.05f, half * 0.45f, top + STRAP, girth + 0.25f, LEATHER);
        b.box(-0.06f, top, rear + 0.12f, 0.06f, top + STRAP * 0.6f, girth, LEATHER);
        // side straps along the flanks at the traces' height, chest to breeching
        float sideY = bottom + 0.5f * tall;
        for (int s = -1; s <= 1; s += 2) {
            float x = s * half;
            b.box(Math.min(x, x + s * STRAP * 0.6f), sideY - 0.04f, rear + 0.1f, Math.max(x, x + s * STRAP * 0.6f),
                    sideY + 0.04f, front, LEATHER);
        }
        // brass tug rings where the ropes end
        List<Double> a = fit.ropeAnchor();
        for (int s = -1; s <= 1; s += 2) {
            float x = (float) (a.get(0) * s), y = a.get(1).floatValue(), z = a.get(2).floatValue();
            b.box(x - 0.05f, y - 0.07f, z - 0.08f, x + 0.05f, y + 0.07f, z + 0.08f, BRASS);
        }
        if (!fit.collar().isEmpty()) {
            List<Double> c = fit.collar();
            float y = c.get(0).floatValue(), z = c.get(1).floatValue(), w = c.get(2).floatValue() / 2,
                    h = c.get(3).floatValue() / 2, d = c.get(4).floatValue();
            ring(b, w, y - h, y + h, z, d, PAD);
            for (int s = -1; s <= 1; s += 2) {
                float x = s * (w + 0.02f);
                b.box(x - 0.03f, y - h - 0.05f, z - 0.03f, x + 0.03f, y + h + 0.08f, z + 0.03f, BRASS);
            }
        }
    }

    /** A band round a box section: four slabs (top, bottom, both sides) of depth {@code d} at {@code z}. */
    private static void ring(Box b, float half, float bottom, float top, float z, float d, float[] mat) {
        float z0 = z - d / 2, z1 = z + d / 2, t = STRAP;
        b.box(-half - t, top, z0, half + t, top + t, z1, mat);
        b.box(-half - t, bottom - t, z0, half + t, bottom, z1, mat);
        b.box(-half - t, bottom, z0, -half, top, z1, mat);
        b.box(half, bottom, z0, half + t, top, z1, mat);
    }

    /** Emits axis-aligned boxes in the animal's frame (x right, y up, z forward, blocks) into the world. */
    private record Box(Matrix4f pose, VertexConsumer vc, Vec3 right, Vec3 fwd, int light, int overlay) {
        void box(float x0, float y0, float z0, float x1, float y1, float z1, float[] mat) {
            float u0 = mat[0] / 64f, v0 = mat[1] / 32f, u1 = (mat[0] + 8) / 64f, v1 = (mat[1] + 8) / 32f;
            // six faces: corner indices into (x, y, z) choices, and the local normal
            quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, u0, v0, u1, v1);
            quad(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, u0, v0, u1, v1);
            quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, u0, v0, u1, v1);
            quad(x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, u0, v0, u1, v1);
            quad(x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, u0, v0, u1, v1);
            quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, u0, v0, u1, v1);
        }

        private void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                          float dx, float dy, float dz, float nx, float ny, float nz, float u0, float v0, float u1,
                          float v1) {
            Vec3 n = right.scale(nx).add(0, ny, 0).add(fwd.scale(nz));
            vertex(ax, ay, az, u0, v1, n);
            vertex(bx, by, bz, u1, v1, n);
            vertex(cx, cy, cz, u1, v0, n);
            vertex(dx, dy, dz, u0, v0, n);
        }

        private void vertex(float x, float y, float z, float u, float v, Vec3 n) {
            Vec3 w = right.scale(x).add(0, y, 0).add(fwd.scale(z));
            vc.addVertex(pose, (float) w.x, (float) w.y, (float) w.z).setColor(-1).setUv(u, v).setOverlay(overlay)
                    .setLight(light).setNormal((float) n.x, (float) n.y, (float) n.z);
        }
    }
}
