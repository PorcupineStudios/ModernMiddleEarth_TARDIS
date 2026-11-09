package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
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
 * box, the debris and the pilot are drawn over it. Rebuilt every frame (2 x 48 x 40 quads). Both textures are made by
 * {@code tools/vortex_texture.py} and can be swapped for any seamless 1024 x 1024 image.
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class VortexRenderer {
    private static final ResourceLocation WALL = TardisRB.id("textures/environment/time_vortex.png");
    private static final ResourceLocation GLOW = TardisRB.id("textures/environment/time_vortex_glow.png");
    private static final int AROUND = 48;
    private static final int ALONG = 40;
    /** How far the wall stands from the axis: past the box's reach (radius 9), so dodging shows it moving. */
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

        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, WALL);
        draw(view, ox, oy, (float) cam.z, half, WALL_RADIUS, time * 0.9f, time * 0.02f, 0.15f, 1f);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, GLOW);
        draw(view, ox, oy, (float) cam.z, half, WALL_RADIUS * 0.85f, time * 1.6f, -time * 0.05f, -0.25f, 0.8f);

        Vector3f left = event.getCamera().getLeftVector();
        Vector3f up = event.getCamera().getUpVector();
        long slot = (long) Math.floor(time * BOLT_RATE);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, puff());
        clouds(view, ox, oy, cam.z, half, time, left, up, slot);

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

    // --- the tube's edge: puffy storm clouds charged with lightning (Aaron 2026-11-09) ---

    private static final ResourceLocation PUFF = TardisRB.id("vortex_puff");
    /** Blocks between rings of cloud along the tunnel, and puffs per ring. */
    private static final float CLOUD_GAP = 2;
    private static final int PUFFS = 34;
    /** Lightning: new bolts per second per channel, and channels (bolts that can be lit at once). */
    private static final float BOLT_RATE = 3;
    private static final int BOLT_CHANNELS = 4;
    private static final int BOLT_STEPS = 10;
    private static DynamicTexture puffTexture;

    /** A soft, lumpy cloud puff (white, alpha only), made once in code. */
    private static ResourceLocation puff() {
        if (puffTexture == null) {
            int n = 128;
            NativeImage img = new NativeImage(n, n, true);
            double[][] lumps = {{0, 0, 0.42}, {-0.28, 0.1, 0.3}, {0.3, 0.12, 0.28}, {0.05, -0.3, 0.3},
                    {-0.12, 0.32, 0.26}, {0.26, -0.22, 0.24}};
            for (int py = 0; py < n; py++) {
                for (int px = 0; px < n; px++) {
                    double x = (px + 0.5) / n * 2 - 1;
                    double y = (py + 0.5) / n * 2 - 1;
                    double d = 0;
                    for (double[] l : lumps) {
                        double dx = (x - l[0]) / l[2];
                        double dy = (y - l[1]) / l[2];
                        d += Math.exp(-(dx * dx + dy * dy) * 1.6);
                    }
                    double a = Math.min(1, Math.max(0, (d - 0.12) / 1.3));
                    a = a * a * (3 - 2 * a);
                    int shade = (int) Math.round(255 * (0.78 + 0.22 * (1 - (y + 1) / 2))); // lit from above
                    img.setPixelRGBA(px, py, ((int) Math.round(a * 255) << 24) | (shade << 16) | (shade << 8)
                            | shade);
                }
            }
            puffTexture = new DynamicTexture(img);
            Minecraft.getInstance().getTextureManager().register(PUFF, puffTexture);
        }
        return PUFF;
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

    /**
     * Rings of cloud puffs banked against the wall all along the tunnel, fixed in place (so they rush past as you fly)
     * and slowly turning round it; puffs near a lightning strike light up. Drawn far to near so the soft edges blend.
     */
    private static void clouds(Matrix4f view, float ox, float oy, double camZ, float half, float time, Vector3f left,
            Vector3f up, long slot) {
        Bolt[] bolts = boltsNow(time, slot);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        long first = (long) Math.ceil((camZ - half) / CLOUD_GAP);
        long last = (long) Math.floor((camZ + half) / CLOUD_GAP);
        // far to near: alternate from both ends toward the camera
        long lo = first;
        long hi = last;
        while (lo <= hi) {
            long k;
            if (Math.abs(lo * CLOUD_GAP - camZ) >= Math.abs(hi * CLOUD_GAP - camZ)) {
                k = lo++;
            } else {
                k = hi--;
            }
            float z = (float) (k * CLOUD_GAP - camZ);
            float t = (z + half) / (2 * half);
            float edge = Math.min(1, (1 - Math.abs(2 * t - 1)) * 4);
            float spin = time * (k % 2 == 0 ? 0.06f : -0.04f);
            for (int i = 0; i < PUFFS; i++) {
                float a = (i + hash(k, i, 7)) / PUFFS * (float) (2 * Math.PI) + spin;
                // banks spiralling along the wall with gaps between, so the vortex shows through
                float bank = (float) Math.sin(a * 3 + k * CLOUD_GAP * 0.045f + time * 0.25f) * 0.5f + 0.5f
                        + (hash(k, i, 12) - 0.5f) * 0.4f;
                if (bank < 0.5f) {
                    continue;
                }
                float r = 14.2f + hash(k, i, 8) * 1.8f;
                float size = 2.8f + hash(k, i, 9) * 2.4f;
                float x = ox + (float) Math.cos(a) * r;
                float y = oy + (float) Math.sin(a) * r;
                float pz = z + (hash(k, i, 10) - 0.5f) * CLOUD_GAP;
                float flash = 0;
                for (Bolt b : bolts) {
                    if (b != null && pz > b.z0() - 6 && pz < b.z1() + 6) {
                        float da = Math.abs((float) Math.IEEEremainder(a - (b.a0() + b.a1()) / 2, 2 * Math.PI));
                        flash = Math.max(flash, b.strength() * Math.max(0, 1 - da / 0.9f));
                    }
                }
                float shade = 0.55f + hash(k, i, 11) * 0.45f;
                int red = Math.round(255 * edge * Math.min(1, shade * 0.62f + flash * 0.7f));
                int green = Math.round(255 * edge * Math.min(1, shade * 0.55f + flash * 0.8f));
                int blue = Math.round(255 * edge * Math.min(1, shade * 0.95f + flash));
                int alpha = Math.round(255 * 0.6f * edge * Math.min(1, (bank - 0.5f) * 3)); // thinning at the bank edges
                float lx = left.x() * size;
                float ly = left.y() * size;
                float lz = left.z() * size;
                float ux = up.x() * size;
                float uy = up.y() * size;
                float uz = up.z() * size;
                buf.addVertex(view, x - lx - ux, y - ly - uy, pz - lz - uz).setUv(0, 1).setColor(red, green, blue, alpha);
                buf.addVertex(view, x + lx - ux, y + ly - uy, pz + lz - uz).setUv(1, 1).setColor(red, green, blue, alpha);
                buf.addVertex(view, x + lx + ux, y + ly + uy, pz + lz + uz).setUv(1, 0).setColor(red, green, blue, alpha);
                buf.addVertex(view, x - lx + ux, y - ly + uy, pz - lz + uz).setUv(0, 0).setColor(red, green, blue, alpha);
            }
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
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
