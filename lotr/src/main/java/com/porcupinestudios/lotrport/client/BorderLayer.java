package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.border.BorderNet;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The border layer of the map screen, from the server's {@link BorderNet.Snapshot}: the original map's holders by
 * biome, with the changed pixels forwarded on top. Drawn as one texture at half the map's resolution (a texel is 2 x 2
 * map pixels; a changed pixel wins its texel): a light wash of each holder's colour, stronger along borders.
 */
public final class BorderLayer {
    private static final ResourceLocation TEXTURE = LotrPort.id("map_borders");
    private static final int FILL_ALPHA = 0x30;
    private static final int EDGE_ALPHA = 0xC0;

    private static BorderNet.Snapshot snapshot;
    private static int[] base = new int[0];
    private static com.porcupinestudios.lotrport.border.WaterFill water;
    private static final Map<Integer, Integer> overlay = new HashMap<>();
    private static DynamicTexture texture;
    private static boolean dirty;

    private BorderLayer() {}

    public static void accept(BorderNet.Snapshot s) {
        snapshot = s;
        int[] next = s.base().stream().mapToInt(Integer::intValue).toArray();
        if (water == null || !java.util.Arrays.equals(next, base)) {
            int[] b = next; // rivers and lakes take the holder around them, as on the server
            water = com.porcupinestudios.lotrport.border.WaterFill.of(MiddleEarthMap.get(),
                    i -> i >= 0 && i < b.length ? b[i] : -1);
        }
        base = next;
        overlay.clear();
        for (int i = 0; i < s.pixels().size(); i++) {
            overlay.put(s.pixels().get(i), s.owners().get(i));
        }
        dirty = true;
    }

    /** Holder index at a map pixel now (-1 nobody). */
    private static int holderAt(MiddleEarthMap map, int px, int pz) {
        Integer o = overlay.get(pz * map.width() + px);
        return o != null ? o : original(map, px, pz);
    }

    private static int original(MiddleEarthMap map, int px, int pz) {
        int b = map.pixelIndex(px, pz);
        int o = b >= 0 && b < base.length ? base[b] : -1;
        return o == com.porcupinestudios.lotrport.border.WaterFill.FILL && water != null
                ? water.ownerOf(pz * map.width() + px) : Math.max(-1, o);
    }

    /** The layer's texture (made or remade when a snapshot came), or null before the first. */
    static ResourceLocation texture() {
        if (snapshot == null) {
            return null;
        }
        if (dirty) {
            rebuild();
        }
        return TEXTURE;
    }

    private static void rebuild() {
        dirty = false;
        MiddleEarthMap map = MiddleEarthMap.get();
        int tw = map.width() / 2;
        int th = map.height() / 2;
        int[] own = new int[tw * th];
        for (int ty = 0; ty < th; ty++) {
            for (int tx = 0; tx < tw; tx++) {
                own[ty * tw + tx] = original(map, tx * 2, ty * 2);
            }
        }
        overlay.forEach((i, o) -> {
            int t = (i / map.width() / 2) * tw + (i % map.width()) / 2;
            if (t >= 0 && t < own.length) {
                own[t] = o;
            }
        });
        List<BorderNet.Holder> holders = snapshot.holders();
        NativeImage img = new NativeImage(tw, th, true);
        for (int ty = 0; ty < th; ty++) {
            for (int tx = 0; tx < tw; tx++) {
                int o = own[ty * tw + tx];
                if (o < 0 || o >= holders.size()) {
                    img.setPixelRGBA(tx, ty, 0);
                    continue;
                }
                boolean edge = (tx > 0 && own[ty * tw + tx - 1] != o) || (tx < tw - 1 && own[ty * tw + tx + 1] != o)
                        || (ty > 0 && own[(ty - 1) * tw + tx] != o) || (ty < th - 1 && own[(ty + 1) * tw + tx] != o);
                int rgb = holders.get(o).color() & 0xFFFFFF;
                int a = edge ? EDGE_ALPHA : FILL_ALPHA;
                // NativeImage wants ABGR
                img.setPixelRGBA(tx, ty, a << 24 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF));
            }
        }
        if (texture == null) {
            texture = new DynamicTexture(img);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
        } else {
            texture.setPixels(img);
            texture.upload();
        }
    }

    /** Hover line for a map pixel: who holds it, and who did on the original map if that changed. */
    static Component hoverLine(int px, int pz) {
        if (snapshot == null) {
            return null;
        }
        MiddleEarthMap map = MiddleEarthMap.get();
        if (px < 0 || pz < 0 || px >= map.width() || pz >= map.height()) {
            return null;
        }
        int now = holderAt(map, px, pz);
        int was = original(map, px, pz);
        if (now < 0 && was < 0) {
            return null;
        }
        Component nowName = name(now);
        if (now == was) {
            return Component.translatableWithFallback("screen.lotr_port.map.held_by", "Land of %s", nowName);
        }
        return Component.translatableWithFallback("screen.lotr_port.map.taken_by", "Land of %s (once %s)", nowName,
                name(was));
    }

    private static Component name(int holder) {
        return holder >= 0 && holder < snapshot.holders().size() ? snapshot.holders().get(holder).name()
                : Component.translatableWithFallback("screen.lotr_port.map.nobody", "nobody");
    }

    /** The tick the borders are from when the player is in the past (time travel), else -1. */
    static long pastTime() {
        return snapshot == null ? -1 : snapshot.time();
    }

    /** Kingdom names at their capitals. */
    static List<BorderNet.Label> labels() {
        return snapshot == null ? List.of() : snapshot.labels();
    }
}
