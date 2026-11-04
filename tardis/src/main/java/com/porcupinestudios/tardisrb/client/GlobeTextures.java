package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.porcupinestudios.ringbearer.api.provider.PlanetSurface;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.net.SkyNet;
import com.porcupinestudios.tardisrb.space.Planet;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.Inflater;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The globes' textures: one image of each planet's surface rectangle (north up, looping), from the planet's picture (the LOTR map for
 * Middle-earth) or the server's sampled biome map. Pictures are read and resized off the render thread, once; the
 * finished image is mipmapped so a far-off globe doesn't shimmer, and drawn blocky (nearest) up close.
 */
final class GlobeTextures {
    /** Longer side of a texture made from a picture (Middle-earth: 2048 x 2560, 200 blocks a pixel). */
    private static final int PICTURE_SIZE = 2560;
    private static final Map<ResourceLocation, Entry> TEXTURES = new HashMap<>();
    private static final Map<ResourceLocation, SkyNet.PlanetMap> MAPS = new HashMap<>();

    private static final class Entry {
        final Object source;
        ResourceLocation id;
        DynamicTexture texture;
        CompletableFuture<NativeImage> pending;

        Entry(Object source) {
            this.source = source;
        }
    }

    private GlobeTextures() {}

    static void setMap(SkyNet.PlanetMap map) {
        MAPS.put(map.planet(), map);
    }

    static void clear() {
        TEXTURES.values().forEach(GlobeTextures::release);
        TEXTURES.clear();
        MAPS.clear();
    }

    /** The planet's texture, or null while it is still being made (or there's nothing to make it from). */
    static ResourceLocation texture(Planet planet) {
        Object source = planet.link().picture().isPresent() ? planet.link().picture().get() : MAPS.get(planet.id());
        if (source == null) {
            return null;
        }
        Entry e = TEXTURES.get(planet.id());
        if (e == null || e.source != source && !e.source.equals(source)) {
            if (e != null) {
                release(e);
            }
            e = new Entry(source);
            TEXTURES.put(planet.id(), e);
            e.pending = source instanceof PlanetSurface.Picture pic ? fromPicture(planet, pic)
                    : CompletableFuture.supplyAsync(() -> fromMap((SkyNet.PlanetMap) source), Util.backgroundExecutor());
        }
        if (e.texture == null && e.pending != null && e.pending.isDone()) {
            NativeImage image = e.pending.getNow(null);
            e.pending = null;
            if (image != null) {
                e.texture = new DynamicTexture(image);
                e.id = TardisRB.id("globe/" + planet.id().getNamespace() + "/" + planet.id().getPath());
                Minecraft.getInstance().getTextureManager().register(e.id, e.texture);
                mipmap(e.texture);
            }
        }
        return e.id;
    }

    private static void release(Entry e) {
        if (e.pending != null) {
            e.pending.thenAccept(img -> {
                if (img != null) {
                    img.close();
                }
            });
        }
        if (e.id != null) {
            Minecraft.getInstance().getTextureManager().release(e.id);
        }
    }

    private static void mipmap(DynamicTexture texture) {
        RenderSystem.assertOnRenderThread();
        GlStateManager._bindTexture(texture.getId());
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    }

    private static NativeImage fromMap(SkyNet.PlanetMap map) {
        byte[] rgb = new byte[map.width() * map.height() * 3];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(map.packed());
            int n = 0;
            while (n < rgb.length && !inflater.finished()) {
                int got = inflater.inflate(rgb, n, rgb.length - n);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                n += got;
            }
        } catch (java.util.zip.DataFormatException e) {
            TardisRB.LOG.warn("Bad globe map for {}", map.planet());
            return null;
        } finally {
            inflater.end();
        }
        NativeImage image = new NativeImage(map.width(), map.height(), false);
        for (int z = 0; z < map.height(); z++) {
            for (int x = 0; x < map.width(); x++) {
                int i = (z * map.width() + x) * 3;
                image.setPixelRGBA(x, z, abgr(((rgb[i] & 0xFF) << 16) | ((rgb[i + 1] & 0xFF) << 8)
                        | (rgb[i + 2] & 0xFF)));
            }
        }
        return image;
    }

    /** Reads the picture on the render thread's resource manager, then resizes it onto the globe off-thread. */
    private static CompletableFuture<NativeImage> fromPicture(Planet planet, PlanetSurface.Picture pic) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(pic.texture());
                    NativeImage src = NativeImage.read(in)) {
                return paint(planet, pic, src);
            } catch (Exception e) {
                TardisRB.LOG.warn("Couldn't make the globe of {} from {}: {}", planet.id(), pic.texture(),
                        e.toString());
                return null;
            }
        }, Util.backgroundExecutor());
    }

    private static NativeImage paint(Planet planet, PlanetSurface.Picture pic, NativeImage src) {
        int w = (int) Math.max(1, Math.round(PICTURE_SIZE * Math.min(1, planet.width() / planet.height())));
        int h = (int) Math.max(1, Math.round(PICTURE_SIZE * Math.min(1, planet.height() / planet.width())));
        NativeImage out = new NativeImage(w, h, false);
        int fill = pic.fill() >= 0 ? abgr(pic.fill()) : (src.getPixelRGBA(0, 0) | 0xFF000000);
        double pw = pic.maxX() - (double) pic.minX();
        double ph = pic.maxZ() - (double) pic.minZ();
        for (int py = 0; py < h; py++) {
            double z = planet.minZ() + (py + 0.5) / h * planet.height();
            int sy = (int) Math.floor((z - pic.minZ()) / ph * src.getHeight());
            for (int px = 0; px < w; px++) {
                double x = planet.minX() + (px + 0.5) / w * planet.width();
                int sx = (int) Math.floor((x - pic.minX()) / pw * src.getWidth());
                int c = sx < 0 || sy < 0 || sx >= src.getWidth() || sy >= src.getHeight() ? fill
                        : src.getPixelRGBA(sx, sy) | 0xFF000000;
                out.setPixelRGBA(px, py, c);
            }
        }
        return out;
    }

    /** NativeImage stores ABGR. */
    private static int abgr(int rgb) {
        return 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
    }
}
