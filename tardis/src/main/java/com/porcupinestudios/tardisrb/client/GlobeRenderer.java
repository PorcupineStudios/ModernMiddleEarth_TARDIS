package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.net.SkyNet;
import com.porcupinestudios.tardisrb.space.Planet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * Draws the planet below you in space, and the cloud bank you pass through on the way up or down.
 *
 * <p>Space is a scale model of each planet (Aaron, 2026-11-04: Middle-earth stays full size, so cheat as long as it's
 * immersive). The globe is drawn under each player from their own spot, at full size and their full-size altitude
 * ({@link Planet#altitude}), so the ground you'd land on is always straight below you, and it shrinks to a ball as you
 * climb. The map is unwrapped round that spot ({@link GlobeSurface}), so it never stretches where you look. It is drawn behind everything (right after the sky, without depth), shrunk towards the camera to fit inside
 * the far plane, which keeps its look the same. One draw call for the surface and one for day and night: the meshes
 * are built once ({@link GlobeMesh}) and the detail drops as the globe gets smaller on screen. The swap between the
 * world and space happens inside a cloud bank (fog and a white haze) so it doesn't show.
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class GlobeRenderer {
    private static final float[] CLOUD = {0.93f, 0.95f, 0.98f};
    private static List<Planet> planets = List.of();
    private static ResourceLocation blank;

    private GlobeRenderer() {}

    public static void set(SkyNet.Planets p) {
        planets = p.planets();
    }

    public static void setMap(SkyNet.PlanetMap map) {
        GlobeTextures.setMap(map);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        planets = List.of();
        RenderSystem.recordRenderCall(() -> {
            GlobeTextures.clear();
            GlobeMesh.clear();
        });
    }

    /** The planet a space dimension point is over: the layer it's in, else the nearest. */
    static Planet planetBelow(Level level, double x, double z) {
        Planet best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Planet p : planets) {
            if (p.link().space().equals(level.dimension())) {
                double d = p.distanceToLayer(x, z);
                if (d < bestDistance) {
                    best = p;
                    bestDistance = d;
                }
            }
        }
        return best;
    }

    static Planet planetOf(Level level) {
        for (Planet p : planets) {
            if (p.link().dimension().equals(level.dimension())) {
                return p;
            }
        }
        return null;
    }

    /** 0 in clear air, 1 deep in the cloud bank under the ceiling (on the planet) or over the floor (in space). */
    static double cloudiness(Level level, Vec3 cam) {
        Planet own = planetOf(level);
        if (own != null && own.link().cloudBand() > 0) {
            int band = own.link().cloudBand();
            return smooth((cam.y - (own.link().ceiling() - band)) / band);
        }
        Planet below = planetBelow(level, cam.x, cam.z);
        if (below != null && below.link().cloudBand() > 0) {
            int band = below.link().cloudBand();
            return smooth(1 - (cam.y - below.link().orbit().floor()) / band);
        }
        return 0;
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Vanilla's sun angle (0 = noon) from the day time, ignoring a fixed time (space is always night). */
    static float sunAngle(Level level, float partial) {
        double d = ((level.getDayTime() + partial) / 24000.0 - 0.25) % 1.0;
        if (d < 0) {
            d += 1;
        }
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (float) ((d * 2.0 + e) / 3.0);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || planets.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Planet planet = planetBelow(level, cam.x, cam.z);
        double cloud = cloudiness(level, cam);
        if (planet == null && cloud <= 0) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        if (planet != null) {
            drawGlobe(mc, event, level, planet, cam);
        }
        if (cloud > 0) {
            drawHaze(new Matrix4f(event.getModelViewMatrix()), event.getProjectionMatrix(), (float) cloud);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void drawGlobe(Minecraft mc, RenderLevelStageEvent event, Level level, Planet p, Vec3 cam) {
        double radius = p.radius();
        double altitude = p.altitude(cam.y);
        double distance = radius + altitude;
        // shrink everything towards the camera so the ball fits inside the far plane; it looks the same
        double far = mc.gameRenderer.getDepthFar();
        double k = Math.min(1.0, far * 0.45 / distance);
        float r = (float) (radius * k);
        Matrix4f view = new Matrix4f(event.getModelViewMatrix()).translate(0, (float) (-distance * k), 0);
        Matrix4f ball = new Matrix4f(view).scale(r);

        // detail from the globe's size on screen
        double angular = Math.asin(Math.min(1, radius / distance));
        double fov = Math.toRadians(mc.options.fov().get());
        double pixels = angular / Math.max(0.1, fov) * mc.getWindow().getHeight() * 2;
        int detail = GlobeMesh.DETAIL[GlobeMesh.DETAIL.length - 1];
        for (int d : GlobeMesh.DETAIL) {
            if (d * 4 >= pixels) {
                detail = d;
                break;
            }
        }

        ResourceLocation texture = GlobeTextures.texture(p);
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        if (texture == null) {
            RenderSystem.setShaderTexture(0, blank());
            RenderSystem.setShaderColor(0.16f, 0.32f, 0.62f, 1);
        } else {
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        // the map spot below the camera, and the ball's radius in map widths and heights
        double u = (p.surfaceX(cam.x) - p.minX()) / p.width();
        double v = (p.surfaceZ(cam.z) - p.minZ()) / p.height();
        GlobeMesh.surface(detail).draw(ball, event.getProjectionMatrix(), RenderSystem.getShader(), u, v,
                radius / p.width(), radius / p.height(), 1.0 / 4096);
        RenderSystem.setShaderColor(1, 1, 1, 1);

        // day and night: the shade sphere's lit pole turned to the sun, which stands where it does in the world
        float angle = (float) (sunAngle(level, event.getPartialTick().getGameTimeDeltaPartialTick(false))
                * Math.PI * 2);
        Matrix4f shade = new Matrix4f(view).rotateZ(angle).scale(r);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var shadeMesh = GlobeMesh.shade();
        shadeMesh.bind();
        shadeMesh.drawWithShader(shade, event.getProjectionMatrix(), RenderSystem.getShader());
        com.mojang.blaze3d.vertex.VertexBuffer.unbind();
    }

    /** A white box round the camera, as see-through as the cloud bank is thin. */
    private static void drawHaze(Matrix4f view, Matrix4f projection, float amount) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int a = Math.round(255 * Math.min(1, amount));
        int red = Math.round(CLOUD[0] * 255);
        int green = Math.round(CLOUD[1] * 255);
        int blue = Math.round(CLOUD[2] * 255);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float s = 1;
        float[][] faces = {
                {-s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s}, {-s, s, -s, -s, s, s, s, s, s, s, s, -s},
                {-s, -s, -s, -s, s, -s, s, s, -s, s, -s, -s}, {-s, -s, s, s, -s, s, s, s, s, -s, s, s},
                {-s, -s, -s, -s, -s, s, -s, s, s, -s, s, -s}, {s, -s, -s, s, s, -s, s, s, s, s, -s, s}};
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) {
                buf.addVertex(view, f[i], f[i + 1], f[i + 2]).setColor(red, green, blue, a);
            }
        }
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.applyModelViewMatrix();
        Matrix4f oldProjection = RenderSystem.getProjectionMatrix();
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
    }

    private static ResourceLocation blank() {
        if (blank == null) {
            NativeImage white = new NativeImage(1, 1, false);
            white.setPixelRGBA(0, 0, 0xFFFFFFFF);
            blank = TardisRB.id("globe/blank");
            Minecraft.getInstance().getTextureManager().register(blank, new DynamicTexture(white));
        }
        return blank;
    }

    /** Thick fog in the cloud bank, so the world below (or nothing, in space) fades into white. */
    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        Level level = Minecraft.getInstance().level;
        if (level == null || planets.isEmpty()) {
            return;
        }
        double c = cloudiness(level, event.getCamera().getPosition());
        if (c <= 0) {
            return;
        }
        float far = event.getFarPlaneDistance();
        float near = event.getNearPlaneDistance();
        event.setNearPlaneDistance((float) (near * (1 - c)));
        event.setFarPlaneDistance((float) (far + (6 - far) * c));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Level level = Minecraft.getInstance().level;
        if (level == null || planets.isEmpty()) {
            return;
        }
        float c = (float) cloudiness(level, event.getCamera().getPosition());
        if (c <= 0) {
            return;
        }
        event.setRed(event.getRed() + (CLOUD[0] - event.getRed()) * c);
        event.setGreen(event.getGreen() + (CLOUD[1] - event.getGreen()) * c);
        event.setBlue(event.getBlue() + (CLOUD[2] - event.getBlue()) * c);
    }
}
