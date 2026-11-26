package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.porcupinestudios.lotrport.LotrPort;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

/**
 * Players' own skins converted to their race body ({@link RaceSkinRemap}), one texture per skin and body, made on the
 * render thread the first time it's drawn. What each body builds is {@code assets/<ns>/race_skin/<kind>.json}
 * ({@code hang}, {@code ear_y}); resource reloads drop every texture so changes show.
 */
public final class RaceSkins {
    private static final Logger LOG = LogUtils.getLogger();
    /** Skin texture + body -> converted texture; empty: can't be converted (not 64x64), use the race skin. */
    private static final Map<String, Optional<ResourceLocation>> CACHE = new HashMap<>();
    private static final Map<String, RaceSkinRemap.Rules> RULES = new HashMap<>();
    private static int next;

    private RaceSkins() {}

    /** The converted texture of {@code skin} for body {@code kind}, or empty if it can't be made (yet). */
    public static Optional<ResourceLocation> get(ResourceLocation skin, String kind) {
        if (!NpcModels.KINDS.contains(kind)) {
            return Optional.empty(); // the classic 64x32 body takes LOTR skins only
        }
        String key = skin + "|" + kind;
        Optional<ResourceLocation> done = CACHE.get(key);
        if (done != null) {
            return done;
        }
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        // never getTexture(skin): for a skin still downloading it registers a missing-texture stand-in under the
        // skin's id, which was then cached as "can't convert" (the player stayed on the fallback skin for good)
        AbstractTexture texture = textures.getTexture(skin, null);
        if (texture == null) {
            return Optional.empty(); // not downloaded yet: try again next frame
        }
        NativeImage image = download(texture);
        if (image == null) {
            return Optional.empty(); // not uploaded yet: try again next frame
        }
        Optional<ResourceLocation> made = Optional.empty();
        try (image) {
            if (image.getWidth() == RaceSkinRemap.SIZE && image.getHeight() == RaceSkinRemap.SIZE) {
                int[] out = RaceSkinRemap.convert(image.getPixelsRGBA(), rules(kind));
                NativeImage converted = new NativeImage(RaceSkinRemap.SIZE, RaceSkinRemap.SIZE, false);
                for (int y = 0; y < RaceSkinRemap.SIZE; y++) {
                    for (int x = 0; x < RaceSkinRemap.SIZE; x++) {
                        converted.setPixelRGBA(x, y, out[y * RaceSkinRemap.SIZE + x]);
                    }
                }
                ResourceLocation id = LotrPort.id("race_skin/" + kind + "/" + next++);
                textures.register(id, new DynamicTexture(converted));
                made = Optional.of(id);
            } else if (image.getWidth() < RaceSkinRemap.SIZE) {
                return Optional.empty(); // a stand-in, not the skin yet: don't remember it
            } else {
                LOG.debug("Skin {} is {}x{}, not converted to a race body", skin, image.getWidth(), image.getHeight());
            }
        }
        CACHE.put(key, made);
        return made;
    }

    /** Reads a texture back from the GPU (downloaded skins keep no copy of their pixels). */
    private static NativeImage download(AbstractTexture texture) {
        RenderSystem.assertOnRenderThread();
        int id = texture.getId();
        if (id <= 0) {
            return null;
        }
        RenderSystem.bindTexture(id);
        int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w <= 0 || h <= 0) {
            return null;
        }
        NativeImage image = new NativeImage(w, h, false);
        image.downloadTexture(0, false);
        return image;
    }

    private static RaceSkinRemap.Rules rules(String kind) {
        return RULES.computeIfAbsent(kind, k -> {
            JsonObject json = new JsonObject();
            ResourceLocation file = LotrPort.id("race_skin/" + k + ".json");
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(file);
            if (res.isPresent()) {
                try (Reader reader = res.get().openAsReader()) {
                    json = JsonParser.parseReader(reader).getAsJsonObject();
                } catch (Exception e) {
                    LOG.warn("Bad race skin rules {}: {}", file, e.toString());
                }
            }
            return new RaceSkinRemap.Rules(NpcModels.hairRows(k), "hobbit".equals(k),
                    GsonHelper.getAsBoolean(json, "ears", "elf".equals(k)),
                    GsonHelper.getAsBoolean(json, "hang", true), GsonHelper.getAsInt(json, "ear_y", 2),
                    "goblin".equals(k), json.has("orcish") ? orcish(json.getAsJsonObject("orcish")) : null);
        });
    }

    /**
     * The {@code orcish} block of a body's rules: {@code skin} [hue, saturation, value], {@code eyes}, {@code tusks},
     * {@code hand} ("#rrggbb", left out = none), {@code grime}, {@code tolerance}, {@code scars}.
     */
    private static RaceSkinRemap.Orcish orcish(JsonObject o) {
        var skin = GsonHelper.getAsJsonArray(o, "skin");
        return new RaceSkinRemap.Orcish(skin.get(0).getAsFloat(), skin.get(1).getAsFloat(), skin.get(2).getAsFloat(),
                colour(o, "eyes"), colour(o, "tusks"), colour(o, "hand"), GsonHelper.getAsFloat(o, "grime", 0.25f),
                GsonHelper.getAsFloat(o, "tolerance", 0.06f), GsonHelper.getAsInt(o, "scars", 2));
    }

    /** "#rrggbb" as an opaque ABGR pixel (NativeImage's order), 0 if absent. */
    private static int colour(JsonObject o, String key) {
        if (!o.has(key)) {
            return 0;
        }
        int rgb = Integer.parseInt(GsonHelper.getAsString(o, key).replace("#", ""), 16);
        return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }

    /** Drops every converted texture and rule (resource reload). */
    public static void clear() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        CACHE.values().forEach(o -> o.ifPresent(textures::release));
        CACHE.clear();
        RULES.clear();
    }
}
