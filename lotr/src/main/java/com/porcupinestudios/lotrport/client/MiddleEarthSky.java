package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.porcupinestudios.lotrport.LotrClientConfig;
import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Middle-earth's sky, after LOTR's sky renderer: vanilla's sky colour and sunrise glow, LOTR's smaller sun and its
 * 8-phase moon, a painted night sky (one of LOTR's three, a new one each night) that turns with the stars, and
 * Eärendil, the evening and morning star that follows the sun just under the horizon at dusk and dawn. Clouds, fog
 * and the region looks ({@link Atmosphere}) stay as they are. Client config {@code sky.enabled} turns it off.
 */
public class MiddleEarthSky extends DimensionSpecialEffects {
    private static final ResourceLocation SUN = tex("sun");
    private static final ResourceLocation MOON = tex("moon");
    private static final ResourceLocation EARENDIL = tex("earendil");
    private static final ResourceLocation[] NIGHT = {tex("night_0"), tex("night_1"), tex("night_2")};
    /** Half-sizes at distance 100, LOTR's numbers (vanilla's sun is 30, its moon 20). */
    private static final float SUN_SIZE = 10, MOON_SIZE = 10, EARENDIL_SIZE = 1.5f;

    public MiddleEarthSky() {
        // the overworld's settings: clouds at 192, normal sky, no constant light
        super(192.0F, true, SkyType.NORMAL, false, false);
    }

    private static ResourceLocation tex(String name) {
        return LotrPort.id("textures/environment/middle_earth/" + name + ".png");
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return color.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera,
            Matrix4f projection, boolean isFoggy, Runnable setupFog) {
        if (!LotrClientConfig.SKY.get()) {
            return false;
        }
        setupFog.run();
        FogType fluid = camera.getFluidInCamera();
        if (isFoggy || fluid == FogType.POWDER_SNOW || fluid == FogType.LAVA) {
            return true;
        }
        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);
        Tesselator tess = Tesselator.getInstance();

        // sky dome in the biome's sky colour
        Vec3 sky = level.getSkyColor(camera.getPosition(), partialTick);
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor((float) sky.x, (float) sky.y, (float) sky.z, 1);
        disc(tess, pose.last().pose(), 16);
        RenderSystem.enableBlend();

        float timeOfDay = level.getTimeOfDay(partialTick);
        float[] glow = getSunriseColor(timeOfDay, partialTick);
        if (glow != null) {
            sunriseGlow(tess, pose, level, partialTick, glow);
        }

        RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        float clear = 1.0F - level.getRainLevel(partialTick);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.XP.rotationDegrees(timeOfDay * 360.0F));
        Matrix4f m = pose.last().pose();

        // night sky first, so sun and moon draw over it
        float stars = level.getStarBrightness(partialTick) * clear;
        if (stars > 0) {
            long night = Math.floorDiv(level.getDayTime() + 6000L, 24000L);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, NIGHT[(int) Math.floorMod(night, NIGHT.length)]);
            RenderSystem.setShaderColor(1, 1, 1, stars);
            FogRenderer.setupNoFog();
            nightBox(tess, pose);
            setupFog.run();
        }

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, clear);
        RenderSystem.setShaderTexture(0, SUN);
        quad(tess, m, SUN_SIZE, 100, 0, 0, 1, 1);

        int phase = level.getMoonPhase() & 7;
        RenderSystem.setShaderTexture(0, MOON);
        quad(tess, m, MOON_SIZE, -100, (phase + 1) / 8F, 1, phase / 8F, 0);

        // Earendil: brightest a quarter-turn from noon (sunset/sunrise), gone by deep night and full day
        float fromMidnight = Math.abs(timeOfDay - 0.5F);
        if (fromMidnight > 0.15F && fromMidnight < 0.3F) {
            float a = Mth.cos((fromMidnight - 0.225F) / 0.075F * Mth.HALF_PI) * clear;
            if (a > 0) {
                pose.pushPose();
                pose.mulPose(Axis.XP.rotationDegrees(Math.signum(timeOfDay - 0.5F) * 18.0F));
                RenderSystem.setShaderTexture(0, EARENDIL);
                RenderSystem.setShaderColor(1, 1, 1, a);
                quad(tess, pose.last().pose(), EARENDIL_SIZE, 100, 0, 0, 1, 1);
                pose.popPose();
            }
        }
        pose.popPose();

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();

        // below the horizon: vanilla's dark lower half
        double belowHorizon = camera.getEntity().getEyePosition(partialTick).y - level.getLevelData().getHorizonHeight(level);
        if (belowHorizon < 0) {
            pose.pushPose();
            pose.translate(0, 12, 0);
            RenderSystem.setShader(GameRenderer::getPositionShader);
            RenderSystem.setShaderColor(0, 0, 0, 1);
            disc(tess, pose.last().pose(), -16);
            pose.popPose();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.depthMask(true);
        return true;
    }

    /** Flat fan at height y, radius 512 (vanilla's sky and dark discs). */
    private static void disc(Tesselator tess, Matrix4f m, float y) {
        BufferBuilder b = tess.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        float r = Math.signum(y) * 512.0F;
        b.addVertex(m, 0, y, 0);
        for (int i = -180; i <= 180; i += 45) {
            float a = i * Mth.DEG_TO_RAD;
            b.addVertex(m, r * Mth.cos(a), y, 512.0F * Mth.sin(a));
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void sunriseGlow(Tesselator tess, PoseStack pose, ClientLevel level, float pt, float[] c) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(level.getSunAngle(pt)) < 0 ? 180.0F : 0.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
        Matrix4f m = pose.last().pose();
        BufferBuilder b = tess.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(m, 0, 100, 0).setColor(c[0], c[1], c[2], c[3]);
        for (int i = 0; i <= 16; i++) {
            float a = i * Mth.TWO_PI / 16.0F;
            float s = Mth.sin(a), co = Mth.cos(a);
            b.addVertex(m, s * 120.0F, co * 120.0F, -co * 40.0F * c[3]).setColor(c[0], c[1], c[2], 0.0F);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        pose.popPose();
    }

    /** A square facing the centre at height y with the given texture corners. */
    private static void quad(Tesselator tess, Matrix4f m, float s, float y, float u0, float v0, float u1, float v1) {
        BufferBuilder b = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        if (y > 0) {
            b.addVertex(m, -s, y, -s).setUv(u0, v0);
            b.addVertex(m, s, y, -s).setUv(u1, v0);
            b.addVertex(m, s, y, s).setUv(u1, v1);
            b.addVertex(m, -s, y, s).setUv(u0, v1);
        } else {
            b.addVertex(m, -s, y, s).setUv(u0, v1);
            b.addVertex(m, s, y, s).setUv(u1, v1);
            b.addVertex(m, s, y, -s).setUv(u1, v0);
            b.addVertex(m, -s, y, -s).setUv(u0, v0);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /**
     * LOTR's night skies are cube maps laid out 3 x 2 (six 768 px faces). Each face is drawn on the inside of a
     * cube of half-size 100 that turns with the sun.
     */
    private static void nightBox(Tesselator tess, PoseStack pose) {
        float[][] turns = {{0, 0}, {90, 0}, {-90, 0}, {180, 0}, {0, 90}, {0, -90}};
        for (int side = 0; side < 6; side++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(turns[side][0]));
            pose.mulPose(Axis.ZP.rotationDegrees(turns[side][1]));
            float u0 = (side % 3) / 3F, v0 = (side / 3) / 2F;
            Matrix4f m = pose.last().pose();
            BufferBuilder b = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            b.addVertex(m, -100, -100, -100).setUv(u0, v0);
            b.addVertex(m, -100, -100, 100).setUv(u0, v0 + 0.5F);
            b.addVertex(m, 100, -100, 100).setUv(u0 + 1 / 3F, v0 + 0.5F);
            b.addVertex(m, 100, -100, -100).setUv(u0 + 1 / 3F, v0);
            BufferUploader.drawWithShader(b.buildOrThrow());
            pose.popPose();
        }
    }
}
