package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.porcupinestudios.tardisrb.space.StarSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a solar system: each planet as a ball painted with its globe map, lit on the side facing the sun, and the sun
 * as a glowing ball. Like the globe, everything is drawn right after the sky without depth, far ones first, and balls
 * past the far plane are shrunk towards the camera (they look the same). One draw call per ball (and three for the
 * sun's glow), meshes built once.
 */
final class SystemRenderer {
    private static final float[] GLOW = {1.25f, 1.6f, 2.2f};
    private static final float[] GLOW_ALPHA = {0.35f, 0.16f, 0.07f};

    private SystemRenderer() {}

    private record Ball(Vec3 rel, double radius, StarSystem.Body body) {}

    static void draw(Minecraft mc, RenderLevelStageEvent event, StarSystem system, Vec3 cam) {
        List<Ball> balls = new ArrayList<>();
        if (system.sun().radius() > 0) {
            balls.add(new Ball(system.sunCenter().subtract(cam), system.sun().radius(), null));
        }
        for (StarSystem.Body b : system.bodies()) {
            balls.add(new Ball(b.center().subtract(cam), b.radius(), b));
        }
        balls.sort(Comparator.comparingDouble((Ball b) -> -b.rel.lengthSqr()));
        double far = mc.gameRenderer.getDepthFar() * 0.45;
        Matrix4f projection = event.getProjectionMatrix();
        VertexBuffer sphere = GlobeMesh.sphere();
        for (Ball ball : balls) {
            double d = ball.rel.length();
            double k = Math.min(1.0, far / (d + ball.radius * (ball.body == null ? GLOW[2] : 1)));
            Matrix4f at = new Matrix4f(event.getModelViewMatrix())
                    .translate((float) (ball.rel.x * k), (float) (ball.rel.y * k), (float) (ball.rel.z * k));
            float r = (float) (ball.radius * k);
            if (ball.body == null) {
                drawSun(at, projection, r, system.sun().color(), sphere);
            } else {
                drawPlanet(at, projection, r, ball.body, system, sphere);
            }
        }
        VertexBuffer.unbind();
    }

    private static void drawPlanet(Matrix4f at, Matrix4f projection, float r, StarSystem.Body body,
            StarSystem system, VertexBuffer sphere) {
        ResourceLocation texture = GlobeTextures.texture(body.planet());
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        if (texture == null) {
            RenderSystem.setShaderTexture(0, GlobeRenderer.blank());
            RenderSystem.setShaderColor(0.16f, 0.32f, 0.62f, 1);
        } else {
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        sphere.bind();
        sphere.drawWithShader(new Matrix4f(at).scale(r), projection, RenderSystem.getShader());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (system.sun().radius() <= 0) {
            return;
        }
        // the shade sphere's lit pole (+y) turned to the sun
        Vec3 toSun = system.sunCenter().subtract(body.center()).normalize();
        Quaternionf turn = new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                new Vector3f((float) toSun.x, (float) toSun.y, (float) toSun.z));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        VertexBuffer shade = GlobeMesh.shade();
        shade.bind();
        shade.drawWithShader(new Matrix4f(at).rotate(turn).scale(r), projection, RenderSystem.getShader());
    }

    private static void drawSun(Matrix4f at, Matrix4f projection, float r, int color, VertexBuffer sphere) {
        float red = ((color >> 16) & 255) / 255f;
        float green = ((color >> 8) & 255) / 255f;
        float blue = (color & 255) / 255f;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, GlobeRenderer.blank());
        sphere.bind();
        // glow shells, added on top of the sky, then the ball itself
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        for (int i = GLOW.length - 1; i >= 0; i--) {
            RenderSystem.setShaderColor(red, green, blue, GLOW_ALPHA[i]);
            sphere.drawWithShader(new Matrix4f(at).scale(r * GLOW[i]), projection, RenderSystem.getShader());
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(Math.min(1, red * 1.1f + 0.1f), Math.min(1, green * 1.1f + 0.1f),
                Math.min(1, blue * 1.1f + 0.1f), 1);
        sphere.drawWithShader(new Matrix4f(at).scale(r), projection, RenderSystem.getShader());
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
}
