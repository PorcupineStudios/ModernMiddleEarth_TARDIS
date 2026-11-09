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
        double axisX = Math.round((cam.x - 0.5) / Vortex.LANE_SPACING) * (double) Vortex.LANE_SPACING + 0.5;
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
