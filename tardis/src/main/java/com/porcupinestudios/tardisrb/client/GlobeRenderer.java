package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.net.SkyNet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Draws the planets seen from space: a shaded ball per sky link of the space dimension the player is in. Placeholder
 * colours (sea, land, ice) until the globe is drawn from a map of the real terrain.
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class GlobeRenderer {
    private static final int LON = 64;
    private static final int LAT = 32;
    private static final Vec3 SUN = new Vec3(0.6, 0.5, 0.62).normalize();
    private static List<SkyNet.Globe> globes = List.of();

    private GlobeRenderer() {}

    public static void set(SkyNet.Globes g) {
        globes = g.globes();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        globes = List.of();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS || globes.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (SkyNet.Globe g : globes) {
            Vec3 c = new Vec3(g.x(), g.y(), g.z()).subtract(cam);
            for (int i = 0; i < LON; i++) {
                for (int j = 0; j < LAT; j++) {
                    double lon0 = (i / (double) LON - 0.5) * 2 * Math.PI;
                    double lon1 = ((i + 1) / (double) LON - 0.5) * 2 * Math.PI;
                    double lat0 = (j / (double) LAT - 0.5) * Math.PI;
                    double lat1 = ((j + 1) / (double) LAT - 0.5) * Math.PI;
                    int color = color((lon0 + lon1) / 2, (lat0 + lat1) / 2);
                    vertex(buf, view, c, g.radius(), lon0, lat0, color);
                    vertex(buf, view, c, g.radius(), lon1, lat0, color);
                    vertex(buf, view, c, g.radius(), lon1, lat1, color);
                    vertex(buf, view, c, g.radius(), lon0, lat1, color);
                }
            }
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.enableCull();
        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void vertex(BufferBuilder buf, Matrix4f view, Vec3 c, double r, double lon, double lat, int color) {
        Vec3 d = new Vec3(Math.cos(lat) * Math.sin(lon), Math.sin(lat), Math.cos(lat) * Math.cos(lon));
        double light = 0.25 + 0.75 * Math.max(0, d.dot(SUN));
        int red = (int) (((color >> 16) & 0xFF) * light);
        int green = (int) (((color >> 8) & 0xFF) * light);
        int blue = (int) ((color & 0xFF) * light);
        buf.addVertex(view, (float) (c.x + d.x * r), (float) (c.y + d.y * r), (float) (c.z + d.z * r))
                .setColor(red, green, blue, 255);
    }

    /** Placeholder surface: ice at the poles, land and sea from a few overlapping waves. */
    private static int color(double lon, double lat) {
        if (Math.abs(lat) > 1.25) {
            return 0xE8F0F8;
        }
        double v = Math.sin(3 * lon) * Math.cos(2 * lat) + 0.6 * Math.sin(7 * lon + 1) * Math.sin(5 * lat + 0.5)
                + 0.3 * Math.cos(13 * lon - 2 * lat);
        return v > 0.35 ? 0x4C8A3C : v > 0.2 ? 0xC8B878 : 0x2A5AA8;
    }
}
