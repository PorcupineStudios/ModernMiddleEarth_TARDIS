package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.vortex.Vortex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The time vortex round you (Aaron 2026-11-09): a tunnel along +z round the lane's axis, its wall painted with
 * {@code textures/environment/time_vortex.png} (rings that rush past and twist) and a second, additive layer of glowing
 * streaks ({@code time_vortex_glow.png}) turning the other way. Drawn right after the (empty) sky without depth, so the
 * box, the debris and the pilot are drawn over it. The wall billows in as cloud ({@link #cloudWall}, 96 x 160 quads) and
 * lightning crackles over it ({@link #bolts}); all rebuilt every frame. Both textures are made by
 * {@code tools/vortex_texture.py} and can be swapped for any seamless 1024 x 1024 image.
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class VortexRenderer {
    private static final ResourceLocation WALL = TardisRB.id("textures/environment/time_vortex.png");
    private static final ResourceLocation GLOW = TardisRB.id("textures/environment/time_vortex_glow.png");
    private static final int AROUND = 48;
    private static final int ALONG = 40;
    /** How far the wall stands from the axis at the folds between its cloud puffs (they billow in to 12). */
    private static final float WALL_RADIUS = 16;
    /** Blocks of tunnel per texture repeat, along and around. */
    private static final float REPEAT = 48;

    private VortexRenderer() {}

    /** The vortex dimension's sky settings: no sky, no clouds, full light; the fog is the tunnel's dark blue. */
    public static final class Effects extends DimensionSpecialEffects {
        public Effects() {
            super(Float.NaN, false, SkyType.NONE, true, true);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fog, float brightness) {
            return new Vec3(0.05, 0.03, 0.16);
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Vortex.DIMENSION) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        float time = (level.getGameTime() % 24000L + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20f;
        float far = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.45f;
        float half = Math.max(24, Math.min(160, far));
        // the axis of the lane the camera is in (lanes are LANE_SPACING apart, at x = n * spacing + 0.5)
        double axisX = Vortex.laneX(cam.x);
        float ox = (float) (axisX - cam.x);
        float oy = (float) (Vortex.LANE_Y - cam.y);
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.applyModelViewMatrix();

        long slot = (long) Math.floor(time * BOLT_RATE);
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, WALL);
        cloudWall(view, ox, oy, cam.z, half, time, time * 0.9f, time * 0.02f, 0.15f, boltsNow(time, slot));

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, GLOW);
        draw(view, ox, oy, (float) cam.z, half, WALL_RADIUS - BILLOW * 1.1f, time * 1.6f, -time * 0.05f, -0.25f,
                0.45f);

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        bolts(view, ox, oy, time, slot);

        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /**
     * One layer: a tube of {@code radius} round the axis at ({@code ox}, {@code oy}) from the camera, {@code half}
     * blocks ahead and behind. The texture runs along the tunnel ({@code scroll} repeats moved), turns round it
     * ({@code spin}) and twists with distance ({@code twist} turns per repeat). The ends fade to black, into the fog.
     */
    private static void draw(Matrix4f view, float ox, float oy, float camZ, float half, float radius, float scroll,
            float spin, float twist, float bright) {
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        float around = (float) Math.max(1, Math.round(2 * Math.PI * radius / REPEAT));
        for (int j = 0; j < ALONG; j++) {
            for (int i = 0; i < AROUND; i++) {
                vertex(buf, view, ox, oy, camZ, half, radius, i, j, around, scroll, spin, twist, bright);
                vertex(buf, view, ox, oy, camZ, half, radius, i + 1, j, around, scroll, spin, twist, bright);
                vertex(buf, view, ox, oy, camZ, half, radius, i + 1, j + 1, around, scroll, spin, twist, bright);
                vertex(buf, view, ox, oy, camZ, half, radius, i, j + 1, around, scroll, spin, twist, bright);
            }
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    // --- the wall is storm cloud, charged with lightning (Aaron 2026-11-09) ---

    /** Lightning: new bolts per second per channel, and channels (bolts that can be lit at once). */
    private static final float BOLT_RATE = 3;
    private static final int BOLT_CHANNELS = 4;
    private static final int BOLT_STEPS = 10;
    /** How far the cloud wall billows in from {@link #WALL_RADIUS} at the top of a puff. */
    private static final float BILLOW = 4;
    /** The cloud wall's mesh: quads round and along (about 1 x 1 block each near you). */
    private static final int CLOUD_AROUND = 96;
    private static final int CLOUD_ALONG = 320;

    /**
     * The whole wall is cloud (Aaron 2026-11-09: "have the clouds cover the skybox completely" with "the same
     * texture/pattern"): the tube billows in at round puffs, big ones with small ones heaped on, fixed along the tunnel (so they rush past)
     * and slowly turning, painted with the vortex texture as it flows through them. Puff tops are bright, the folds
     * between them dark, and puffs near a lightning strike light up blue-white. Same UVs as {@link #draw}.
     */
    private static void cloudWall(Matrix4f view, float ox, float oy, double camZ, float half, float time, float scroll,
            float spin, float twist, Bolt[] bolts) {
        int cols = CLOUD_AROUND + 1;
        float[] px = new float[cols * (CLOUD_ALONG + 1)];
        float[] py = new float[px.length];
        float[] pz = new float[px.length];
        float[] pu = new float[px.length];
        float[] pv = new float[px.length];
        int[] shade = new int[px.length * 3];
        float around = (float) Math.max(1, Math.round(2 * Math.PI * WALL_RADIUS / REPEAT));
        float drift = time * 0.004f;
        for (int j = 0; j <= CLOUD_ALONG; j++) {
            float t = j / (float) CLOUD_ALONG;
            float z = -half + t * 2 * half;
            double world = camZ + z;
            float edge = Math.min(1, (1 - Math.abs(2 * t - 1)) * 4);
            for (int i = 0; i <= CLOUD_AROUND; i++) {
                float f = i / (float) CLOUD_AROUND;
                // big round puffs with smaller ones heaped on them (cauliflower cumulus)
                float big = puffs(f + drift, world, 16, 6, 4.2f, 1);
                float small = puffs(f - drift, world, 36, 3, 1.9f, 2);
                float p = Math.min(1, Math.max(big, small * 0.6f) + 0.2f * small);
                float r = WALL_RADIUS - BILLOW * p;
                double a = f * 2 * Math.PI;
                int k = j * cols + i;
                px[k] = ox + (float) Math.cos(a) * r;
                py[k] = oy + (float) Math.sin(a) * r;
                pz[k] = z;
                pv[k] = (float) (world / REPEAT) - scroll;
                pu[k] = f * around + pv[k] * twist + spin;
                float flash = 0;
                for (Bolt b : bolts) {
                    if (b != null && z > b.z0() - 5 && z < b.z1() + 5) {
                        float da = Math.abs((float) Math.IEEEremainder(a - (b.a0() + b.a1()) / 2, 2 * Math.PI));
                        flash = Math.max(flash, b.strength() * Math.max(0, 1 - da / 0.8f));
                    }
                }
                float bright = 0.4f + 0.75f * p;
                shade[k * 3] = Math.round(255 * edge * Math.min(1, bright + flash * 0.5f));
                shade[k * 3 + 1] = Math.round(255 * edge * Math.min(1, bright + flash * 0.6f));
                shade[k * 3 + 2] = Math.round(255 * edge * Math.min(1, bright + flash * 0.9f));
            }
        }
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int j = 0; j < CLOUD_ALONG; j++) {
            for (int i = 0; i < CLOUD_AROUND; i++) {
                int k = j * cols + i;
                for (int c : new int[] {k, k + 1, k + 1 + cols, k + cols}) {
                    buf.addVertex(view, px[c], py[c], pz[c]).setUv(pu[c], pv[c])
                            .setColor(shade[c * 3], shade[c * 3 + 1], shade[c * 3 + 2], 255);
                }
            }
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    /**
     * Round cloud puffs on the tube: one per cell ({@code cells} round it, {@code len} blocks along), jittered in its
     * cell, about {@code size} blocks in radius. The height (0..1, a dome) of the highest puff over the point at
     * {@code f} round (0..1, wrapping) and {@code z} along.
     */
    private static float puffs(float f, double z, int cells, double len, float size, long seed) {
        double u = (f - Math.floor(f)) * cells;
        double w = z / len;
        long iu = (long) Math.floor(u);
        long iw = (long) Math.floor(w);
        double blocksPerCell = 2 * Math.PI * WALL_RADIUS / cells;
        float best = 0;
        for (long cu = iu - 1; cu <= iu + 1; cu++) {
            for (long cw = iw - 1; cw <= iw + 1; cw++) {
                long hu = Math.floorMod(cu, cells);
                double dx = (u - cu - 0.15 - 0.7 * hash(hu, cw, seed)) * blocksPerCell;
                double dz = (w - cw - 0.15 - 0.7 * hash(hu, cw, seed + 11)) * len;
                double rad = size * (0.7 + 0.6 * hash(hu, cw, seed + 23));
                double d2 = (dx * dx + dz * dz) / (rad * rad);
                if (d2 < 1) {
                    double h = 1 - d2;
                    h = h * (2 - h); // round top, soft rim
                    best = Math.max(best, (float) (h * (0.75 + 0.25 * hash(hu, cw, seed + 37))));
                }
            }
        }
        return best;
    }

    /** A steady pseudo-random 0..1 from a few integers. */
    private static float hash(long a, long b, long c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** One lightning bolt: from angle {@code a0} at {@code z0} (from the camera) to {@code a1} at {@code z1}. */
    private record Bolt(float a0, float z0, float a1, float z1, float strength, long seed) {}

    /** The bolts lit this moment: each channel may strike once per slot, fading over it. */
    private static Bolt[] boltsNow(float time, long slot) {
        Bolt[] out = new Bolt[BOLT_CHANNELS];
        float life = time * BOLT_RATE - slot;
        for (int c = 0; c < BOLT_CHANNELS; c++) {
            if (hash(slot, c, 1) > 0.8f) {
                continue;
            }
            float a0 = hash(slot, c, 2) * (float) (2 * Math.PI);
            float z0 = -24 + hash(slot, c, 3) * 100;
            float a1 = a0 + (hash(slot, c, 4) - 0.5f) * 2.2f;
            float z1 = z0 + 8 + hash(slot, c, 5) * 26;
            // a bright strike that flickers out
            float flicker = hash(slot, c, (long) (time * 24)) < 0.25f ? 0.3f : 1;
            out[c] = new Bolt(a0, z0, a1, z1, Math.max(0, 1 - life * 1.3f) * flicker, slot * 31 + c);
        }
        return out;
    }


    /** Jagged lightning crawling over the clouds' inner face: a wide faint glow and a thin bright core, plus a fork. */
    private static void bolts(Matrix4f view, float ox, float oy, float time, long slot) {
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        long jitter = (long) (time * 20); // the bolt's shape crackles a few times while lit
        for (Bolt b : boltsNow(time, slot)) {
            if (b == null || b.strength() <= 0.02f) {
                continue;
            }
            any = true;
            Vector3f[] pts = new Vector3f[BOLT_STEPS + 1];
            for (int s = 0; s <= BOLT_STEPS; s++) {
                float f = s / (float) BOLT_STEPS;
                float wob = s == 0 || s == BOLT_STEPS ? 0 : 1;
                float a = b.a0() + (b.a1() - b.a0()) * f + wob * (hash(b.seed(), s, jitter) - 0.5f) * 0.25f;
                float r = 12.4f + wob * (hash(b.seed(), s, jitter + 7) - 0.5f) * 1.6f;
                float z = b.z0() + (b.z1() - b.z0()) * f + wob * (hash(b.seed(), s, jitter + 13) - 0.5f) * 1.5f;
                pts[s] = new Vector3f(ox + (float) Math.cos(a) * r, oy + (float) Math.sin(a) * r, z);
            }
            ribbon(buf, view, pts, 0, BOLT_STEPS, 2.6f, Math.round(150 * b.strength()), 120, 160, 255);
            ribbon(buf, view, pts, 0, BOLT_STEPS, 0.35f, Math.round(255 * b.strength()), 200, 225, 255);
            ribbon(buf, view, pts, 0, BOLT_STEPS, 0.15f, Math.round(255 * b.strength()), 255, 255, 255); // white-hot core
            // a short fork from partway along
            int from = 3 + (int) (hash(b.seed(), 99, 0) * 4);
            Vector3f[] fork = new Vector3f[4];
            fork[0] = pts[from];
            for (int s = 1; s < 4; s++) {
                Vector3f p = pts[from + s];
                float off = s * 0.9f;
                fork[s] = new Vector3f(p.x() + (hash(b.seed(), s, 101) - 0.5f) * off * 2,
                        p.y() + (hash(b.seed(), s, 102) - 0.5f) * off * 2, p.z() + off);
            }
            ribbon(buf, view, fork, 0, 3, 0.2f, Math.round(230 * b.strength()), 200, 225, 255);
        }
        if (any) {
            BufferUploader.drawWithShader(buf.buildOrThrow());
        } else {
            buf.build(); // nothing lit: drop the empty batch
        }
    }

    /** A line through {@code pts} (camera-relative) as quads {@code width} wide, turned to face the camera. */
    private static void ribbon(BufferBuilder buf, Matrix4f view, Vector3f[] pts, int from, int to, float width,
            int alpha, int red, int green, int blue) {
        for (int s = from; s < to; s++) {
            Vector3f p = pts[s];
            Vector3f q = pts[s + 1];
            Vector3f dir = new Vector3f(q).sub(p);
            Vector3f toCam = new Vector3f(p).add(q).mul(-0.5f);
            Vector3f side = dir.cross(toCam, new Vector3f());
            if (side.lengthSquared() < 1.0E-6f) {
                continue;
            }
            side.normalize(width / 2);
            buf.addVertex(view, p.x() - side.x(), p.y() - side.y(), p.z() - side.z()).setColor(red, green, blue, alpha);
            buf.addVertex(view, p.x() + side.x(), p.y() + side.y(), p.z() + side.z()).setColor(red, green, blue, alpha);
            buf.addVertex(view, q.x() + side.x(), q.y() + side.y(), q.z() + side.z()).setColor(red, green, blue, alpha);
            buf.addVertex(view, q.x() - side.x(), q.y() - side.y(), q.z() - side.z()).setColor(red, green, blue, alpha);
        }
    }

    private static void vertex(BufferBuilder buf, Matrix4f view, float ox, float oy, float camZ, float half,
            float radius, int i, int j, float around, float scroll, float spin, float twist, float bright) {
        double a = i / (double) AROUND * 2 * Math.PI;
        float t = j / (float) ALONG;
        float z = -half + t * 2 * half;
        float x = ox + (float) Math.cos(a) * radius;
        float y = oy + (float) Math.sin(a) * radius;
        float v = (camZ + z) / REPEAT - scroll;
        float u = i / (float) AROUND * around + v * twist + spin;
        float edge = Math.min(1, (1 - Math.abs(2 * t - 1)) * 4);
        int c = Math.round(255 * bright * edge);
        buf.addVertex(view, x, y, z).setUv(u, v).setColor(c, c, c, 255);
    }
}
