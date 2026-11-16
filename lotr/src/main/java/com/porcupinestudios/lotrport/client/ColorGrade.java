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
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL30;

/**
 * Region colour grading: a full-screen pass after the world is drawn (before the hand and the GUI) with saturation,
 * contrast, warmth, brightness, vignette and a soft glow. The numbers are each atmosphere file's {@code grade}
 * (offsets from neutral), eased by {@link Atmosphere} as you cross borders, scaled by {@code atmosphere.strength}.
 * Shader: {@code assets/lotr_port/shaders/core/grade.*}. Config {@code atmosphere.grading} turns it off.
 */
public final class ColorGrade {
    private static final String[] UNIFORMS = {"Saturation", "Contrast", "Warmth", "Brightness", "Vignette", "Bloom"};
    private static final float[] NEUTRAL = {1, 1, 0, 1, 0, 0};
    private static ShaderInstance shader;
    private static RenderTarget copy;

    private ColorGrade() {}

    static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), LotrPort.id("grade"),
                DefaultVertexFormat.POSITION), s -> shader = s);
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(ColorGrade::render);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || shader == null
                || !LotrClientConfig.ATMOSPHERE_GRADE.get() || !LotrClientConfig.ATMOSPHERE.get()) {
            return;
        }
        double pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float strength = LotrClientConfig.ATMOSPHERE_STRENGTH.get().floatValue();
        float[] v = new float[UNIFORMS.length];
        boolean any = false;
        for (int i = 0; i < v.length; i++) {
            float off = Atmosphere.grade(i, pt) * strength;
            any |= Math.abs(off) > 0.002f;
            v[i] = NEUTRAL[i] + off;
        }
        if (!any) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = main.width, h = main.height;
        if (copy == null) {
            copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
        }
        // the frame so far into our copy, then drawn back graded
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        main.bindWrite(false);

        for (int i = 0; i < v.length; i++) {
            shader.safeGetUniform(UNIFORMS[i]).set(v[i]);
        }
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
