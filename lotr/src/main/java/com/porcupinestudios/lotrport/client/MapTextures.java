package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.porcupinestudios.lotrport.LotrPort;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The map image for the map screen, at full size and halved three times (box-filtered), plain and sepia (LOTR's
 * sepia option). Drawing the 3200x4000 image straight onto a few hundred screen pixels skips most of its pixels and
 * shimmers while panning; the screen picks the copy nearest its zoom and draws it with linear filtering when
 * shrinking, so zoomed-out maps look like LOTR's painted map instead of noise. Made once, on first use.
 */
final class MapTextures {
    static final ResourceLocation SOURCE = LotrPort.id("textures/gui/map.png");
    static final int LEVELS = 4;
    /** Colour beyond the map's edge: the map's own ocean pixels (LOTR draws its ocean biome colour there). */
    static final int OCEAN = 0xFF02598D;

    private static ResourceLocation[][] ids;
    private static int width;
    private static int height;

    private MapTextures() {}

    /** The texture for a level (0 = full size, each next one half as big) and sepia or not. */
    static ResourceLocation get(int level, boolean sepia) {
        if (ids == null) {
            load();
        }
        return ids[sepia ? 1 : 0][Math.max(0, Math.min(LEVELS - 1, level))];
    }

    static int width() {
        if (ids == null) {
            load();
        }
        return width;
    }

    static int height() {
        if (ids == null) {
            load();
        }
        return height;
    }

    static int ocean(boolean sepia) {
        return sepia ? sepia(OCEAN) : OCEAN;
    }

    private static void load() {
        ids = new ResourceLocation[2][LEVELS];
        Minecraft mc = Minecraft.getInstance();
        NativeImage full;
        try (InputStream in = mc.getResourceManager().open(SOURCE)) {
            full = NativeImage.read(in);
        } catch (IOException e) {
            LotrPort.LOG.error("could not read the map image", e);
            full = new NativeImage(1, 1, false);
        }
        width = full.getWidth();
        height = full.getHeight();
        NativeImage level = full;
        for (int i = 0; i < LEVELS; i++) {
            if (i > 0) {
                level = half(level);
            }
            ids[0][i] = register("map_" + i, copy(level, false));
            ids[1][i] = register("map_sepia_" + i, copy(level, true));
        }
        full.close();
    }

    private static ResourceLocation register(String name, NativeImage image) {
        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation id = LotrPort.id("dynamic/" + name);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
    }

    private static NativeImage copy(NativeImage src, boolean sepia) {
        NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), false);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int abgr = src.getPixelRGBA(x, y);
                out.setPixelRGBA(x, y, sepia ? abgr(sepia(argb(abgr))) : abgr);
            }
        }
        return out;
    }

    /** Half size, each pixel the average of four. Leaves {@code src} open (it is kept as the level above). */
    private static NativeImage half(NativeImage src) {
        int w = Math.max(1, src.getWidth() / 2);
        int h = Math.max(1, src.getHeight() / 2);
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = 0;
                int b = 0;
                int g = 0;
                int r = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int p = src.getPixelRGBA(Math.min(src.getWidth() - 1, x * 2 + dx),
                                Math.min(src.getHeight() - 1, y * 2 + dy));
                        a += p >>> 24;
                        b += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        r += p & 0xFF;
                    }
                }
                out.setPixelRGBA(x, y, (a / 4) << 24 | (b / 4) << 16 | (g / 4) << 8 | (r / 4));
            }
        }
        return out;
    }

    private static int argb(int abgr) {
        return (abgr & 0xFF00FF00) | (abgr & 0xFF) << 16 | (abgr >> 16) & 0xFF;
    }

    private static int abgr(int argb) {
        return argb(argb);
    }

    /** LOTR's sepia tone. */
    static int sepia(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float r2 = Math.min(1, r * 0.79f + g * 0.39f + b * 0.26f);
        float g2 = Math.min(1, r * 0.52f + g * 0.35f + b * 0.19f);
        float b2 = Math.min(1, r * 0.35f + g * 0.26f + b * 0.15f);
        return (argb & 0xFF000000) | Math.round(r2 * 255) << 16 | Math.round(g2 * 255) << 8 | Math.round(b2 * 255);
    }
}
