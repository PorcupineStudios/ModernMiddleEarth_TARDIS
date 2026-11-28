package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.porcupinestudios.lotrport.LotrClientConfig;
import com.porcupinestudios.lotrport.LotrPort;
import java.io.Reader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * LOTR's title screen backdrop (LOTRGuiMainMenu): the Middle-earth map, drawn crisp like the map screen, drifting slowly
 * from one place on land to another under LOTR's parchment overlay. Without the LOTR jar there is no map, so the
 * panorama shows instead.
 */
public final class MenuBackdrop {
    private static final ResourceLocation START = LotrPort.id("menu_map.json");
    private static final RandomSource RANDOM = RandomSource.create();
    private static float x = -1;
    private static float z;
    private static float velX;
    private static float velZ;
    private static float targetX;
    private static float targetZ;
    private static long last;

    private MenuBackdrop() {}

    /** Draws the map over the whole screen at {@code fade} alpha; false if the panorama should show instead. */
    public static boolean render(GuiGraphics g, int width, int height, float fade) {
        if (LotrClientConfig.MENU_BACKDROP.get() != LotrClientConfig.MenuBackdrop.MAP) {
            return false;
        }
        int w = MapTextures.width();
        int h = MapTextures.height();
        if (w < 64 || h < 64) {
            return false; // no LOTR jar: MapTextures made a 1x1 stand-in
        }
        float scale = LotrClientConfig.MENU_MAP_ZOOM.get().floatValue();
        float halfW = Math.min(width / 2f / scale, w / 2f);
        float halfH = Math.min(height / 2f / scale, h / 2f);
        long now = Util.getMillis();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        if (x < 0) {
            start();
            x = Mth.clamp(x, halfW, w - halfW);
            z = Mth.clamp(z, halfH, h - halfH);
            pick(w, h, halfW, halfH);
        }
        float speed = LotrClientConfig.MENU_MAP_SPEED.get().floatValue();
        float dx = targetX - x;
        float dz = targetZ - z;
        float dist = Mth.sqrt(dx * dx + dz * dz);
        if (dist < halfW / 4) {
            pick(w, h, halfW, halfH); // nearly there: turn toward the next place before stopping
        } else {
            // steer, don't snap, so each turn is a slow curve
            float k = Math.min(1, dt * 0.4f);
            velX += (dx / dist * speed - velX) * k;
            velZ += (dz / dist * speed - velZ) * k;
        }
        x = Mth.clamp(x + velX * dt, halfW, w - halfW);
        z = Mth.clamp(z + velZ * dt, halfH, h - halfH);

        g.fill(0, 0, width, height, 0xFF000000);
        ResourceLocation tex = MapTextures.get(false);
        Minecraft.getInstance().getTextureManager().getTexture(tex).setFilter(false, false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float cx = width / 2f;
        float cy = height / 2f;
        MapScreen.quad(g, tex, cx - halfW * scale, cy - halfH * scale, cx + halfW * scale, cy + halfH * scale,
                (x - halfW) / w, (z - halfH) / h, (x + halfW) / w, (z + halfH) / h, fade);
        MapScreen.quad(g, MapScreen.OVERLAY, 0, 0, width, height, 0, 0, 1, 1, 0.2f * fade);
        RenderSystem.disableBlend();
        // darker top and bottom, so the logo and the buttons stay readable
        int a = Math.round(fade * 0x90) << 24;
        g.fillGradient(0, 0, width, height / 3, a, 0);
        g.fillGradient(0, height * 2 / 3, width, height, 0, a);
        return true;
    }

    /** The first place shown: {@code assets/lotr_port/menu_map.json} (the Shire), or the map's middle. */
    private static void start() {
        x = MapTextures.width() / 2f;
        z = MapTextures.height() / 2f;
        try (Reader in = Minecraft.getInstance().getResourceManager().openAsReader(START)) {
            JsonObject json = JsonParser.parseReader(in).getAsJsonObject();
            x = json.get("start_x").getAsFloat();
            z = json.get("start_z").getAsFloat();
        } catch (Exception e) {
            LotrPort.LOG.warn("[menu map] can't read {}: {}", START, e.toString());
        }
    }

    /** A new place to drift to: on land, and far enough that the trip shows a stretch of the map. */
    private static void pick(int w, int h, float halfW, float halfH) {
        for (int tries = 0; tries < 50; tries++) {
            float px = halfW + RANDOM.nextFloat() * (w - 2 * halfW);
            float pz = halfH + RANDOM.nextFloat() * (h - 2 * halfH);
            float d = Mth.sqrt((px - x) * (px - x) + (pz - z) * (pz - z));
            if (!MapTextures.isSea((int) px, (int) pz) && (x < 0 || d > halfW && d < halfW * 4)) {
                targetX = px;
                targetZ = pz;
                return;
            }
        }
        targetX = w / 2f; // all sea or too far: head for the middle
        targetZ = h / 2f;
    }
}
