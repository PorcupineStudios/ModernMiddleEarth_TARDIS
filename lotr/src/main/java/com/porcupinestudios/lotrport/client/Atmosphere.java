package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrClientConfig;
import com.porcupinestudios.lotrport.LotrPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Region looks: fog distance, fog colour and a screen filter per biome, from resource-pack files
 * {@code assets/<ns>/atmosphere/<name>.json} (F3+T reloads them). Fields, all optional:
 * {@code biomes} (ids, {@code #tags}, or {@code prefix*}), {@code priority}, {@code fog_start} / {@code fog_end}
 * (share of the view distance), {@code grade} (offsets from neutral, see {@link ColorGrade}), {@code fog_color} ("#rrggbb") + {@code fog_color_strength} (0..1),
 * {@code filter} ("#rrggbb") + {@code filter_alpha} (0..1). The look eases in over about two seconds.
 * Client config {@code atmosphere.*} turns fog or filters off and scales them.
 */
public final class Atmosphere {
    record Look(List<String> biomes, int priority, float fogStart, float fogEnd, int fogColor, float fogColorStrength,
            int filter, float filterAlpha, float[] grade) {}

    /** Colour grade offsets from neutral, eased like the rest: saturation, contrast, warmth, brightness, vignette, bloom. */
    static final String[] GRADE = {"saturation", "contrast", "warmth", "brightness", "vignette", "bloom"};
    private static final int G0 = 9;

    /** The eased state: everything blends between "none" (vanilla) and the current look. */
    private static final float[] cur = new float[15];
    private static final float[] prev = new float[15];
    private static List<Look> looks = List.of();
    private static Holder<Biome> lastBiome;
    private static Look lastLook;

    private Atmosphere() {}

    /** Mod bus registrations, called from {@link LotrClient}. */
    static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new Loader());
    }

    static void layers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(LotrPort.id("atmosphere_filter"), Atmosphere::renderFilter);
    }

    /** Game bus registrations. */
    static void init() {
        NeoForge.EVENT_BUS.addListener(Atmosphere::tick);
        NeoForge.EVENT_BUS.addListener(Atmosphere::fog);
        NeoForge.EVENT_BUS.addListener(Atmosphere::fogColor);
    }

    static Look lookFor(Holder<Biome> biome) {
        for (Look l : looks) {
            for (String m : l.biomes()) {
                if (matches(biome, m)) {
                    return l;
                }
            }
        }
        return null;
    }

    private static boolean matches(Holder<Biome> biome, String m) {
        if (m.startsWith("#")) {
            return biome.is(TagKey.create(Registries.BIOME, id(m.substring(1))));
        }
        var key = biome.unwrapKey();
        if (key.isEmpty()) {
            return false;
        }
        ResourceLocation b = key.get().location();
        if (m.endsWith("*")) {
            String p = m.substring(0, m.length() - 1);
            return (p.contains(":") ? b.toString() : b.getPath()).startsWith(p);
        }
        return m.contains(":") ? b.toString().equals(m) : b.getNamespace().equals(LotrPort.MOD_ID) && b.getPath().equals(m);
    }

    private static ResourceLocation id(String s) {
        return s.contains(":") ? ResourceLocation.parse(s) : LotrPort.id(s);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        System.arraycopy(cur, 0, prev, 0, cur.length);
        Look look = null;
        if (mc.level != null && mc.player != null && LotrClientConfig.ATMOSPHERE.get()) {
            Holder<Biome> b = mc.level.getBiome(mc.player.blockPosition());
            if (b != lastBiome) {
                lastBiome = b;
                lastLook = lookFor(b);
            }
            look = lastLook;
        }
        float[] target = new float[15];
        if (look != null) {
            target[0] = 1;
            target[1] = look.fogStart();
            target[2] = look.fogEnd();
            float s = look.fogColorStrength();
            target[3] = (look.fogColor() >> 16 & 255) / 255f * s;
            target[4] = (look.fogColor() >> 8 & 255) / 255f * s;
            target[5] = (look.fogColor() & 255) / 255f * s;
            target[6] = s;
            target[7] = look.filterAlpha();
            target[8] = look.filter();
            System.arraycopy(look.grade(), 0, target, G0, GRADE.length);
        }
        for (int i = G0; i < cur.length; i++) {
            cur[i] += Math.max(-0.01f, Math.min(0.01f, target[i] - cur[i]));
        }
        // filter colour jumps (index 8) only while the filter is faded out, so colours never blend through grey
        for (int i = 0; i < 8; i++) {
            float step = i == 1 || i == 2 ? 0.02f : 0.025f;
            cur[i] += Math.max(-step, Math.min(step, target[i] - cur[i]));
        }
        if (cur[7] <= 0.001f || target[8] == cur[8]) {
            cur[8] = target[8];
        } else {
            cur[7] = Math.max(0, cur[7] - 0.05f);
        }
    }

    /** The eased grade at this frame: index into {@link #GRADE}, offset from neutral. */
    static float grade(int i, double pt) {
        return lerp(G0 + i, pt);
    }

    private static float lerp(int i, double pt) {
        return prev[i] + (cur[i] - prev[i]) * (float) pt;
    }

    private static void fog(ViewportEvent.RenderFog event) {
        if (!LotrClientConfig.ATMOSPHERE_FOG.get() || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN
                || event.getType() != FogType.NONE) {
            return;
        }
        double pt = event.getPartialTick();
        float on = lerp(0, pt);
        float start = lerp(1, pt);
        float end = lerp(2, pt);
        if (on <= 0 || end <= 0) {
            return;
        }
        float strength = LotrClientConfig.ATMOSPHERE_STRENGTH.get().floatValue();
        float far = event.getFarPlaneDistance();
        float near = event.getNearPlaneDistance();
        // blend toward the look's distances; strength 2 pulls fog twice as close as written
        float wantFar = far * Math.min(1, end / Math.max(0.1f, strength));
        float wantNear = far * Math.min(1, start / Math.max(0.1f, strength));
        event.setNearPlaneDistance(near + (Math.min(wantNear, wantFar - 1) - near) * on);
        event.setFarPlaneDistance(far + (wantFar - far) * on);
        event.setCanceled(true);
    }

    private static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!LotrClientConfig.ATMOSPHERE_FOG.get()) {
            return;
        }
        double pt = event.getPartialTick();
        float s = lerp(6, pt) * (float) Math.min(1, LotrClientConfig.ATMOSPHERE_STRENGTH.get());
        if (s <= 0) {
            return;
        }
        float k = s / Math.max(1e-4f, lerp(6, pt));
        // keep the day/night/rain brightness vanilla computed: tint by colour, scaled by its brightness
        float bright = Math.max(event.getRed(), Math.max(event.getGreen(), event.getBlue()));
        event.setRed(event.getRed() * (1 - s) + lerp(3, pt) * k * bright * 1.4f);
        event.setGreen(event.getGreen() * (1 - s) + lerp(4, pt) * k * bright * 1.4f);
        event.setBlue(event.getBlue() * (1 - s) + lerp(5, pt) * k * bright * 1.4f);
    }

    private static void renderFilter(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!LotrClientConfig.ATMOSPHERE_FILTER.get() || mc.options.hideGui) {
            return;
        }
        float a = lerp(7, delta.getGameTimeDeltaPartialTick(false))
                * LotrClientConfig.ATMOSPHERE_STRENGTH.get().floatValue();
        if (a <= 0.002f) {
            return;
        }
        int alpha = Math.min(255, (int) (Math.min(1, a) * 255));
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha << 24 | ((int) cur[8] & 0xFFFFFF));
    }

    static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new com.google.gson.Gson(), "atmosphere");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller p) {
            List<Look> out = new ArrayList<>();
            files.forEach((id, json) -> {
                try {
                    JsonObject o = json.getAsJsonObject();
                    if (!GsonHelper.getAsBoolean(o, "enabled", true)) {
                        return;
                    }
                    List<String> biomes = new ArrayList<>();
                    GsonHelper.getAsJsonArray(o, "biomes").forEach(e -> biomes.add(e.getAsString()));
                    out.add(new Look(biomes, GsonHelper.getAsInt(o, "priority", 0),
                            GsonHelper.getAsFloat(o, "fog_start", 0), GsonHelper.getAsFloat(o, "fog_end", 0),
                            color(o, "fog_color"), GsonHelper.getAsFloat(o, "fog_color_strength", 0),
                            color(o, "filter"), GsonHelper.getAsFloat(o, "filter_alpha", 0), grade(o)));
                } catch (RuntimeException ex) {
                    com.mojang.logging.LogUtils.getLogger().warn("Bad atmosphere file {}: {}", id, ex.getMessage());
                }
            });
            out.sort(Comparator.comparingInt(Look::priority).reversed());
            looks = List.copyOf(out);
            lastBiome = null;
        }

        private static float[] grade(JsonObject o) {
            float[] g = new float[GRADE.length];
            JsonObject j = GsonHelper.getAsJsonObject(o, "grade", new JsonObject());
            for (int i = 0; i < g.length; i++) {
                g[i] = GsonHelper.getAsFloat(j, GRADE[i], 0);
            }
            return g;
        }

        private static int color(JsonObject o, String key) {
            String s = GsonHelper.getAsString(o, key, "#000000");
            return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16);
        }
    }
}
