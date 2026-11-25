package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrClientConfig;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.ring.RingPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * What a ring of power looks like from inside: while you wear a ring whose item a resource-pack file
 * {@code assets/<ns>/ring_look/<id>.json} names, the screen goes into the wraith-world. Fields (all optional but
 * {@code items}): {@code grade} (offsets like an atmosphere file's: saturation ... blur, see {@link Atmosphere#GRADE}),
 * {@code tint} ("#rrggbb") + {@code tint_strength}, {@code fade_in_seconds}, {@code fade_out_seconds},
 * {@code start} (share of the look when put on) and {@code grow_minutes} (worn this long, the full look),
 * {@code eye} ({@code x}, {@code z} in Middle-earth blocks, {@code strength}, {@code glow} "#rrggbb": the dark side of
 * the screen leans toward it, and facing it a pulsing glow shows), {@code hide_wearer} (others don't draw a wearer
 * at all: no armour, held items or name) and {@code wearers_see_wearers} (someone wearing a ring sees other wearers).
 * F3+T reloads. Client config {@code ring.look} / {@code ring.strength}. Drawn by {@link ColorGrade}.
 */
public final class RingSight {
    record Look(Set<Item> items, float[] grade, int tint, float tintStrength, float fadeIn, float fadeOut, float start,
            float growMinutes, boolean eye, double eyeX, double eyeZ, float eyeStrength, int eyeGlow,
            boolean hideWearer, boolean wearersSeeWearers) {}

    private static List<Look> looks = List.of();
    private static Look active;
    private static float on, prevOn;
    private static int wornTicks;

    private RingSight() {}

    static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new Loader());
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(RingSight::tick);
        NeoForge.EVENT_BUS.addListener(RingSight::hideWearers);
    }

    static Look lookOf(ItemStack stack) {
        for (Look l : looks) {
            if (l.items().contains(stack.getItem())) {
                return l;
            }
        }
        return null;
    }

    /** The look of the ring a player wears, or null. Other players' rings come from Curios' sync. */
    static Look worn(Player player) {
        if (looks.isEmpty()) {
            return null;
        }
        ItemStack s = RingPowers.wornStack(player, st -> lookOf(st) != null);
        return s.isEmpty() ? null : lookOf(s);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        prevOn = on;
        Look look = mc.player == null || mc.level == null ? null : worn(mc.player);
        if (look != null) {
            active = look;
            wornTicks++;
            on = Math.min(1, on + 1f / Math.max(1, look.fadeIn() * 20));
        } else {
            wornTicks = 0;
            on = active == null ? 0 : Math.max(0, on - 1f / Math.max(1, active.fadeOut() * 20));
            if (on <= 0) {
                active = null;
            }
        }
    }

    /** How much of the look shows this frame (fade x growth x config), 0..2. */
    static float factor(double pt) {
        if (active == null || !LotrClientConfig.RING_LOOK.get()) {
            return 0;
        }
        float fade = Mth.lerp((float) pt, prevOn, on);
        float grow = active.growMinutes() <= 0 ? 1
                : Mth.clamp(active.start() + (1 - active.start()) * wornTicks / (active.growMinutes() * 1200), 0, 1);
        if (wornTicks == 0) {
            grow = 1; // fading out: keep the strength it had
        }
        fade = fade * fade * (3 - 2 * fade);
        return fade * grow * LotrClientConfig.RING_STRENGTH.get().floatValue();
    }

    static float grade(int i, double pt) {
        return active == null ? 0 : active.grade()[i] * factor(pt);
    }

    /** rgb + amount of the ring's tint. */
    static float[] tint(double pt) {
        if (active == null) {
            return new float[4];
        }
        int c = active.tint();
        return new float[] {(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f,
                Math.min(1, active.tintStrength() * factor(pt))};
    }

    /**
     * Where the eye is from the camera: x = -1 left .. 1 right (behind: fully to its side), y = how much the camera
     * faces it (-1..1), z = strength (0 without an eye or outside Middle-earth). {@code glow} gets its colour.
     */
    static float[] eye(Camera camera, double pt, float[] glow) {
        Minecraft mc = Minecraft.getInstance();
        if (active == null || !active.eye() || mc.level == null
                || !LotrPort.id("middle_earth").equals(mc.level.dimensionType().effectsLocation())) {
            return new float[3];
        }
        double dx = active.eyeX() - camera.getPosition().x;
        double dz = active.eyeZ() - camera.getPosition().z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1) {
            return new float[3];
        }
        dx /= len;
        dz /= len;
        float yaw = camera.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        float side = (float) (dx * rx + dz * rz);
        float facing = (float) (dx * fx + dz * fz);
        if (facing < 0) {
            side = side >= 0 ? 1 : -1;
        }
        int c = active.eyeGlow();
        glow[0] = (c >> 16 & 255) / 255f;
        glow[1] = (c >> 8 & 255) / 255f;
        glow[2] = (c & 255) / 255f;
        return new float[] {side, facing, active.eyeStrength() * factor(pt)};
    }

    /** Others don't draw a ring-hidden player at all (vanilla still draws an invisible player's armour and items). */
    private static void hideWearers(RenderLivingEvent.Pre<?, ?> event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getEntity() instanceof Player p) || p == mc.player || !p.isInvisible() || looks.isEmpty()) {
            return;
        }
        Look theirs = worn(p);
        if (theirs == null || !theirs.hideWearer()) {
            return;
        }
        if (theirs.wearersSeeWearers() && mc.player != null && worn(mc.player) != null) {
            return;
        }
        event.setCanceled(true);
    }

    static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new com.google.gson.Gson(), "ring_look");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller p) {
            List<Look> out = new ArrayList<>();
            files.forEach((id, json) -> {
                try {
                    JsonObject o = json.getAsJsonObject();
                    Set<Item> items = new java.util.HashSet<>();
                    GsonHelper.getAsJsonArray(o, "items").forEach(e -> BuiltInRegistries.ITEM
                            .getOptional(ResourceLocation.parse(e.getAsString())).ifPresent(items::add));
                    float[] g = new float[Atmosphere.GRADE.length];
                    JsonObject j = GsonHelper.getAsJsonObject(o, "grade", new JsonObject());
                    for (int i = 0; i < g.length; i++) {
                        g[i] = GsonHelper.getAsFloat(j, Atmosphere.GRADE[i], 0);
                    }
                    JsonObject eye = GsonHelper.getAsJsonObject(o, "eye", null);
                    out.add(new Look(items, g, color(o, "tint"), GsonHelper.getAsFloat(o, "tint_strength", 0),
                            GsonHelper.getAsFloat(o, "fade_in_seconds", 1.5f),
                            GsonHelper.getAsFloat(o, "fade_out_seconds", 1f), GsonHelper.getAsFloat(o, "start", 1),
                            GsonHelper.getAsFloat(o, "grow_minutes", 0), eye != null,
                            eye == null ? 0 : GsonHelper.getAsDouble(eye, "x"),
                            eye == null ? 0 : GsonHelper.getAsDouble(eye, "z"),
                            eye == null ? 0 : GsonHelper.getAsFloat(eye, "strength", 0.5f),
                            eye == null ? 0xFF5010 : color(eye, "glow"),
                            GsonHelper.getAsBoolean(o, "hide_wearer", true),
                            GsonHelper.getAsBoolean(o, "wearers_see_wearers", true)));
                } catch (RuntimeException ex) {
                    com.mojang.logging.LogUtils.getLogger().warn("Bad ring_look file {}: {}", id, ex.getMessage());
                }
            });
            looks = List.copyOf(out);
            com.mojang.logging.LogUtils.getLogger().info("Ring looks: {}", files.keySet().stream()
                    .map(ResourceLocation::toString).collect(Collectors.joining(", ")));
        }

        private static int color(JsonObject o, String key) {
            String s = GsonHelper.getAsString(o, key, "#000000");
            return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16);
        }
    }
}
