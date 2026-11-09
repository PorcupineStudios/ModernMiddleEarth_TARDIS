package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The electric shock on the loose box ({@link TardisBody#shock}, Aaron 2026-11-09, after his reference pictures):
 * crackling arcs that hug the box's sides and wrap round its edges, a blue glow on it, sparks where it was struck
 * and a few bolts jumping off into the air. Everything is built in the box's own frame (inside its pose), so it turns,
 * tilts and rocks with the box; ribbons face the camera worked out in that frame.
 */
final class ShockRenderer {
    private ShockRenderer() {}

    /** 0 at the shock to 1 when it is over; above 1 when nothing is playing. */
    static float age(TardisBody body, float partialTick) {
        return (body.tickCount - body.shockStart + partialTick) / TardisContent.tuning().flight().shock().ticks();
    }

    /** Rocks the box (about its middle, across the way it was knocked), dying away; before its own turn. */
    static void wobble(TardisBody body, float age, float height, PoseStack pose) {
        if (age >= 1) {
            return;
        }
        Vector3f dir = new Vector3f((float) body.shockDir.x, 0, (float) body.shockDir.z);
        Vector3f axis = dir.lengthSquared() < 1.0E-4F ? new Vector3f(1, 0, 0) : new Vector3f(dir.z, 0, -dir.x).normalize();
        float wobble = (float) TardisContent.tuning().flight().shock().wobble();
        float angle = wobble * (float) (Math.exp(-3 * age) * Math.sin(age * 20));
        pose.translate(0, height / 2, 0);
        pose.mulPose(Axis.of(axis).rotationDegrees(angle));
        pose.translate(0, -height / 2, 0);
    }

    /** Draws the shock over the box's model, in the box's own frame; {@code turn} is the model's own yaw. */
    static void draw(TardisBody body, float age, PoliceBoxModel model, Quaternionf turn, PoseStack pose,
            MultiBufferSource buffers) {
        if (age >= 1 || age < 0) {
            return;
        }
        Tuning.Shock tuning = TardisContent.tuning().flight().shock();
        Matrix4f m = pose.last().pose();
        Shape s = new Shape(model, new Matrix4f(m).invert().transformPosition(new Vector3f()));
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        int frame = (int) (age * tuning.ticks() / 2); // shapes jump every 2 ticks: the crackle
        RandomSource flicker = RandomSource.create(body.shockSeed * 31L + frame);
        float fade = (1 - age) * (0.65F + 0.35F * flicker.nextFloat());

        // the knock's direction in the box's frame: the struck side faces back along it
        Vector3f knock = new Vector3f((float) body.shockDir.x, (float) body.shockDir.y, (float) body.shockDir.z);
        knock = knock.lengthSquared() < 1.0E-6F ? new Vector3f(0, 1, 0) : knock.normalize();
        knock.rotate(new Quaternionf(turn).conjugate());
        Vector3f struck = s.onSurface(new Vector3f(s.c).sub(new Vector3f(knock).mul(4)), 0);

        s.glow(vc, m, 0.30F * fade * fade);
        int arcs = Math.round(tuning.arcs() * (1 - 0.6F * age));
        for (int i = 0; i < arcs; i++) {
            // each arc keeps its ends for the whole shock and crawls between them, redrawn each frame
            RandomSource ends = RandomSource.create(body.shockSeed * 977L + i);
            Vector3f a = i == 0 ? struck : s.onSurface(s.randomOut(ends), 0);
            Vector3f b = s.onSurface(s.randomOut(ends), 0);
            RandomSource shape = RandomSource.create(body.shockSeed * 7919L + i * 131L + frame);
            Vector3f[] path = s.hug(a, b, 16, shape);
            s.bolt(vc, m, path, fade);
            Vector3f from = path[4 + shape.nextInt(8)];
            s.bolt(vc, m, s.hug(from, s.onSurface(s.randomOut(shape), 0), 6, shape), fade * 0.7F);
        }
        if (age < 0.55F) {
            // bolts jumping off the struck side into the air
            for (int j = 0; j < 3; j++) {
                RandomSource r = RandomSource.create(body.shockSeed * 104729L + j * 17L + frame);
                Vector3f out = new Vector3f(knock).negate().add(s.jitter(r, 0.9F)).normalize().mul(2.5F + 2 * r.nextFloat());
                s.bolt(vc, m, s.jagged(struck, new Vector3f(struck).add(out), 18, 0.16F, r), fade * (1 - age / 0.55F));
            }
        }
        if (age < 0.5F) {
            s.sparks(vc, m, struck, knock, body.shockSeed, age * tuning.ticks());
        }
    }

    /** The box's bounds, slightly out, and the camera in the box's frame. */
    private static final class Shape {
        final Vector3f c;
        final Vector3f h;
        final Vector3f cam;

        Shape(PoliceBoxModel model, Vector3f cam) {
            c = new Vector3f((model.min[0] + model.max[0]) / 2, (model.min[1] + model.max[1]) / 2,
                    (model.min[2] + model.max[2]) / 2);
            h = new Vector3f(Math.max(0.3F, (model.max[0] - model.min[0]) / 2 + 0.04F),
                    Math.max(0.5F, (model.max[1] - model.min[1]) / 2 + 0.04F),
                    Math.max(0.3F, (model.max[2] - model.min[2]) / 2 + 0.04F));
            this.cam = cam;
        }

        /** {@code p} pushed in or out from the middle onto the box's surface, then {@code lift} further out. */
        Vector3f onSurface(Vector3f p, float lift) {
            Vector3f d = new Vector3f(p).sub(c);
            float k = Math.max(Math.abs(d.x) / h.x, Math.max(Math.abs(d.y) / h.y, Math.abs(d.z) / h.z));
            if (k < 1.0E-5F) {
                return new Vector3f(c.x, c.y + h.y, c.z);
            }
            d.div(k);
            return d.add(new Vector3f(d).normalize().mul(lift)).add(c);
        }

        Vector3f randomOut(RandomSource r) {
            return new Vector3f(c).add(jitter(r, 1).mul(h.x * 4, h.y * 4, h.z * 4));
        }

        Vector3f jitter(RandomSource r, float size) {
            return new Vector3f(r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1).mul(size);
        }

        /** A squiggle from a to b that clings to the box: each point is put back on the surface, a little out. */
        Vector3f[] hug(Vector3f a, Vector3f b, int steps, RandomSource r) {
            Vector3f[] path = new Vector3f[steps + 1];
            for (int i = 0; i <= steps; i++) {
                Vector3f p = new Vector3f(a).lerp(b, (float) i / steps);
                if (i > 0 && i < steps) {
                    p.add(jitter(r, 0.22F));
                }
                path[i] = onSurface(p, i == 0 || i == steps ? 0.02F : 0.02F + r.nextFloat() * 0.14F);
            }
            return path;
        }

        /** A free bolt from a to b, kinked sideways at each step. */
        Vector3f[] jagged(Vector3f a, Vector3f b, int steps, float kink, RandomSource r) {
            Vector3f[] path = new Vector3f[steps + 1];
            for (int i = 0; i <= steps; i++) {
                path[i] = new Vector3f(a).lerp(b, (float) i / steps);
                if (i > 0 && i < steps) {
                    path[i].add(jitter(r, kink));
                }
            }
            return path;
        }

        /** A wide faint cyan glow and a thin white-blue core along the path. */
        void bolt(VertexConsumer vc, Matrix4f m, Vector3f[] path, float alpha) {
            for (int i = 0; i + 1 < path.length; i++) {
                ribbon(vc, m, path[i], path[i + 1], 0.045F, 0.25F, 0.75F, 1F, 0.35F * alpha);
                ribbon(vc, m, path[i], path[i + 1], 0.018F, 0.85F, 0.95F, 1F, alpha);
            }
        }

        /** A flat strip from a to b turned to face the camera, both sides drawn. */
        void ribbon(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, float width, float red, float green,
                float blue, float alpha) {
            Vector3f along = new Vector3f(b).sub(a);
            Vector3f toCam = new Vector3f(cam).sub(a);
            Vector3f side = along.cross(toCam, new Vector3f());
            if (side.lengthSquared() < 1.0E-8F) {
                return;
            }
            side.normalize().mul(width);
            quad(vc, m, new Vector3f(a).sub(side), new Vector3f(a).add(side), new Vector3f(b).add(side),
                    new Vector3f(b).sub(side), red, green, blue, alpha);
        }

        /** The blue glow on the box: its six sides, just outside it. */
        void glow(VertexConsumer vc, Matrix4f m, float alpha) {
            float x0 = c.x - h.x;
            float x1 = c.x + h.x;
            float y0 = c.y - h.y;
            float y1 = c.y + h.y;
            float z0 = c.z - h.z;
            float z1 = c.z + h.z;
            float[][] sides = {
                    {x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
                    {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                    {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}};
            for (float[] q : sides) {
                quad(vc, m, new Vector3f(q[0], q[1], q[2]), new Vector3f(q[3], q[4], q[5]),
                        new Vector3f(q[6], q[7], q[8]), new Vector3f(q[9], q[10], q[11]), 0.2F, 0.45F, 1F, alpha);
            }
        }

        /** Hot sparks bursting off the struck point, falling and burning out. */
        void sparks(VertexConsumer vc, Matrix4f m, Vector3f at, Vector3f knock, int seed, float ticks) {
            RandomSource r = RandomSource.create(seed * 15485863L);
            for (int i = 0; i < 18; i++) {
                Vector3f v = new Vector3f(knock).negate().add(jitter(r, 1.1F)).normalize().mul(0.12F + 0.18F * r.nextFloat());
                float life = 5 + r.nextFloat() * 7;
                if (ticks > life) {
                    continue;
                }
                Vector3f p = new Vector3f(v).mul(ticks).add(at).sub(0, 0.01F * ticks * ticks, 0);
                Vector3f tail = new Vector3f(v).mul(-1.6F).add(p);
                float heat = 1 - ticks / life;
                ribbon(vc, m, tail, p, 0.03F, 1F, 0.55F + 0.4F * heat, 0.2F + 0.6F * heat * heat, heat);
            }
        }

        void quad(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f c2, Vector3f d, float red,
                float green, float blue, float alpha) {
            for (Vector3f p : new Vector3f[] {a, b, c2, d, d, c2, b, a}) {
                vc.addVertex(m, p.x, p.y, p.z).setColor(red, green, blue, alpha);
            }
        }
    }
}
