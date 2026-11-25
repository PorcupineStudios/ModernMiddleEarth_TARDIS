package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.porcupinestudios.lotrport.LotrClientConfig;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.compat.iris.IrisCompat;
import java.io.IOException;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Region colour grading and the Ring's wraith-world: one full-screen pass with saturation, contrast, warmth,
 * brightness, vignette, soft glow, film grain, colour fringes, heat shimmer and edge blur, plus a tint and the Eye
 * (the dark side of the screen leaning toward Barad-dur, a pulse when you face it). The numbers are each atmosphere
 * file's {@code grade} (offsets from neutral), eased by {@link Atmosphere} as you cross borders and scaled by
 * {@code atmosphere.strength}, plus the worn ring's look ({@link RingSight}).
 *
 * <p>Without a shader pack the pass runs after the world, before the hand and the GUI. With an Iris shader pack on
 * ({@link IrisCompat}) Iris composites the world at the end of the frame, so the pass runs over the finished frame
 * instead (a GUI layer under everything: the hand is graded too), with region grading scaled by
 * {@code atmosphere.shaderPackGrading}. Shader: {@code assets/lotr_port/shaders/core/grade.*}.
 */
public final class ColorGrade {
    private static final String[] UNIFORMS = {"Saturation", "Contrast", "Warmth", "Brightness", "Vignette", "Bloom",
            "Grain", "Aberration", "Wobble", "Blur"};
    private static final float[] NEUTRAL = {1, 1, 0, 1, 0, 0, 0, 0, 0, 0};
    /** Effects that move (grain, shimmer): dropped by {@code atmosphere.reduceMotion}. */
    private static final int GRAIN = 6;
    private static final boolean[] MOVING = {false, false, false, false, false, false, true, false, true, false};
    /** Index of {@code smear} in {@link Atmosphere#GRADE}: not a float uniform, it scales the camera smear. */
    private static final int SMEAR = 10;
    /** Longest smear at {@code smear} 1, as a share of the screen. */
    private static final float SMEAR_LENGTH = 0.04f;
    private static ShaderInstance shader;
    private static RenderTarget copy;
    // camera turn tracking for the smear: last rotation and time, the eased amount and screen direction
    private static float lastYaw, lastPitch, smearAmount, smearX, smearY;
    private static long lastNanos;

    private ColorGrade() {}

    static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), LotrPort.id("grade"),
                DefaultVertexFormat.POSITION), s -> shader = s);
    }

    static void layers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(LotrPort.id("grade_over_shader_pack"), ColorGrade::overShaderPack);
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(ColorGrade::afterLevel);
    }

    private static void afterLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || IrisCompat.shaderPackInUse()) {
            return;
        }
        draw(event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false), 1);
    }

    private static void overShaderPack(GuiGraphics g, DeltaTracker delta) {
        if (!IrisCompat.shaderPackInUse() || !LotrClientConfig.ATMOSPHERE_OVER_SHADER_PACKS.get()) {
            return;
        }
        g.flush();
        draw(Minecraft.getInstance().gameRenderer.getMainCamera(), delta.getGameTimeDeltaPartialTick(false),
                LotrClientConfig.ATMOSPHERE_SHADER_PACK_GRADING.get().floatValue());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /**
     * The screen-space smear for this frame (uv offset along the turn, 0 = none). Only sharp turns count: below
     * {@code atmosphere.smearFrom} degrees a second nothing happens, so small mouse tremors never smear, and the
     * smear grows to its full length at {@code atmosphere.smearFull}. It comes in over ~40 ms and fades over ~150 ms,
     * so a flick leaves a short trail. A jump of more than 150 degrees in one frame (teleport, respawn) is a cut, not a
     * turn. Tracked every frame, even when no look smears, so it never sees a stale rotation.
     */
    private static float[] smear(Camera camera, float strength) {
        long now = System.nanoTime();
        float yaw = camera.getYRot(), pitch = camera.getXRot();
        float dt = lastNanos == 0 ? 0 : Math.min(0.25f, (now - lastNanos) / 1e9f);
        float dYaw = net.minecraft.util.Mth.wrapDegrees(yaw - lastYaw), dPitch = pitch - lastPitch;
        lastNanos = now;
        lastYaw = yaw;
        lastPitch = pitch;
        if (dt <= 0.0005f || Math.abs(dYaw) > 150 || Math.abs(dPitch) > 150) {
            smearAmount = 0;
            return new float[2];
        }
        double from = LotrClientConfig.ATMOSPHERE_SMEAR_FROM.get();
        double full = Math.max(from + 1, LotrClientConfig.ATMOSPHERE_SMEAR_FULL.get());
        float speed = (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch) / dt;
        float t = (float) net.minecraft.util.Mth.clamp((speed - from) / (full - from), 0, 1);
        float target = t * t * (3 - 2 * t);
        float rate = target > smearAmount ? dt / 0.04f : dt / 0.15f;
        smearAmount += (target - smearAmount) * Math.min(1, rate);
        if (target > 0.01f) {
            // screen direction of the turn: yaw across the screen, pitch up and down, in shares of the field of view
            Minecraft mc = Minecraft.getInstance();
            float vFov = mc.options.fov().get();
            float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
            float hFov = (float) Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(vFov / 2)) * aspect));
            // in screen heights, so the direction is right on a wide screen
            float sx = dYaw / hFov * aspect, sy = dPitch / vFov;
            float len = (float) Math.sqrt(sx * sx + sy * sy);
            if (len > 0) {
                float k = Math.min(1, dt / 0.04f);
                smearX += (sx / len - smearX) * k;
                smearY += (sy / len - smearY) * k;
            }
        }
        float l = smearAmount * strength * SMEAR_LENGTH; // in screen heights
        if (l < 0.0005f) {
            return new float[2];
        }
        float d = (float) Math.sqrt(smearX * smearX + smearY * smearY);
        return d == 0 ? new float[2] : new float[] {smearX / d * l / aspect(), smearY / d * l};
    }

    private static float aspect() {
        var win = Minecraft.getInstance().getWindow();
        return (float) win.getWidth() / Math.max(1, win.getHeight());
    }

    private static void draw(Camera camera, double pt, float regionScale) {
        if (shader == null) {
            return;
        }
        boolean region = LotrClientConfig.ATMOSPHERE_GRADE.get() && LotrClientConfig.ATMOSPHERE.get();
        boolean still = LotrClientConfig.ATMOSPHERE_REDUCE_MOTION.get();
        float strength = LotrClientConfig.ATMOSPHERE_STRENGTH.get().floatValue() * regionScale;
        float[] v = new float[UNIFORMS.length];
        boolean any = false;
        for (int i = 0; i < v.length; i++) {
            float off = (region ? Atmosphere.grade(i, pt) * strength : 0) + RingSight.grade(i, pt);
            if (still && MOVING[i]) {
                off = 0;
            }
            if (i == GRAIN) {
                off *= LotrClientConfig.ATMOSPHERE_GRAIN.get().floatValue();
            }
            any |= Math.abs(off) > 0.002f;
            v[i] = i == 2 ? off : Math.max(0, NEUTRAL[i] + off); // warmth goes both ways
        }
        float smearGrade = (region ? Atmosphere.grade(SMEAR, pt) * strength : 0) + RingSight.grade(SMEAR, pt);
        float[] smear = smear(camera, still ? 0 : smearGrade * LotrClientConfig.ATMOSPHERE_SMEAR.get().floatValue());
        any |= smear[0] != 0 || smear[1] != 0;
        float[] tint = RingSight.tint(pt);
        float[] glow = new float[3];
        float[] eye = RingSight.eye(camera, pt, glow);
        any |= tint[3] > 0.002f || eye[2] > 0.002f;
        if (!any) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = main.width, h = main.height;
        if (copy == null) {
            copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
        }
        // the frame so far into our copy, then drawn back graded
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        main.bindWrite(true);

        for (int i = 0; i < v.length; i++) {
            shader.safeGetUniform(UNIFORMS[i]).set(v[i]);
        }
        shader.safeGetUniform("Smear").set(smear[0], smear[1]);
        shader.safeGetUniform("Tint").set(tint[0], tint[1], tint[2], tint[3]);
        shader.safeGetUniform("Eye").set(eye[0], eye[1], still ? 0 : 1, eye[2]);
        shader.safeGetUniform("EyeGlow").set(glow[0], glow[1], glow[2]);
        var level = Minecraft.getInstance().level;
        float time = level == null ? 0 : (float) ((level.getGameTime() % 72000L + pt) / 20.0);
        shader.safeGetUniform("Time").set(still ? 0 : time);
        shader.safeGetUniform("InSize").set((float) w, (float) h);
        shader.setSampler("DiffuseSampler", copy.getColorTextureId());
        RenderSystem.setShader(() -> shader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.addVertex(-1, -1, 0);
        b.addVertex(1, -1, 0);
        b.addVertex(1, 1, 0);
        b.addVertex(-1, 1, 0);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }
}
