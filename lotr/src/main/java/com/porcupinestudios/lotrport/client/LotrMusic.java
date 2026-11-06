package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Middle-earth's background music: each biome plays a playlist, shuffled (every track once before any repeats).
 * Data: {@code assets/<ns>/music/playlists.json} in every resource pack, merged in pack order:
 * <ul>
 * <li>{@code biomes}: biome path → playlist name (generated from LOTR's music regions; a pack may remap)</li>
 * <li>{@code playlists}: name → sound event ids. The events are the pack's own (its sounds.json), so the tracks
 * live only in the pack, never in the mod.</li>
 * <li>{@code transition}: entering a biome with another playlist, the player must stay {@code settle_ticks} (so
 * walking along a border doesn't flip the music); then, unless {@code keep_shared_track} and the playing track is
 * also on the new list, it fades out over {@code fade_out_ticks}, and the new list starts {@code change_gap_ticks}
 * later, fading in over {@code fade_in_ticks}. Between tracks of one list: a silence of {@code gap_min_ticks} to
 * {@code gap_max_ticks}.</li>
 * </ul>
 * Vanilla's music is held back while in Middle-earth (a lotr_port biome) if the playlist has tracks.
 */
@EventBusSubscriber(modid = "lotr_port", value = Dist.CLIENT)
public final class LotrMusic {
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath("lotr_port",
            "music/playlists.json");
    private static final Map<String, String> BIOMES = new HashMap<>();
    private static final Map<String, List<ResourceLocation>> PLAYLISTS = new HashMap<>();
    private static JsonObject transition = new JsonObject();
    private static final RandomSource RANDOM = RandomSource.create();

    private static String playlist;
    private static final Deque<ResourceLocation> queue = new ArrayDeque<>();
    private static Track playing;
    private static String pending;
    private static int pendingTicks;
    private static int silence;

    private LotrMusic() {}

    private static int num(String key, int fallback) {
        return transition.has(key) ? transition.get(key).getAsInt() : fallback;
    }

    @EventBusSubscriber(modid = "lotr_port", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    static final class Reload {
        @SubscribeEvent
        static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) LotrMusic::load);
        }
    }

    private static void load(ResourceManager rm) {
        BIOMES.clear();
        PLAYLISTS.clear();
        transition = new JsonObject();
        for (Resource r : rm.getResourceStack(FILE)) {
            try (Reader in = r.openAsReader()) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                if (o.has("biomes")) {
                    for (var e : o.getAsJsonObject("biomes").entrySet()) {
                        BIOMES.put(e.getKey(), e.getValue().getAsString());
                    }
                }
                if (o.has("playlists")) {
                    for (var e : o.getAsJsonObject("playlists").entrySet()) {
                        List<ResourceLocation> ids = new ArrayList<>();
                        for (JsonElement t : e.getValue().getAsJsonArray()) {
                            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
                            if (id != null) {
                                ids.add(id);
                            }
                        }
                        // a later pack with an empty list doesn't wipe an earlier pack's tracks
                        if (!ids.isEmpty() || !PLAYLISTS.containsKey(e.getKey())) {
                            PLAYLISTS.put(e.getKey(), ids);
                        }
                    }
                }
                if (o.has("transition")) {
                    for (var e : o.getAsJsonObject("transition").entrySet()) {
                        transition.add(e.getKey(), e.getValue());
                    }
                }
            } catch (Exception e) {
                LogUtils.getLogger().warn("Bad music playlist file in pack {}", r.sourcePackId(), e);
            }
        }
        stop();
    }

    private static void stop() {
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
        }
        playing = null;
        playlist = null;
        pending = null;
        queue.clear();
    }

    /** The playlist for where the player is, or null outside Middle-earth / with no tracks. */
    private static String here(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        return mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                .filter(k -> k.location().getNamespace().equals("lotr_port"))
                .map(k -> BIOMES.get(k.location().getPath()))
                .filter(p -> !PLAYLISTS.getOrDefault(p, List.of()).isEmpty())
                .orElse(null);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        String want = here(mc);
        if (want == null) {
            if (playlist != null && playing != null) {
                playing.fadeOut(num("fade_out_ticks", 80));
            }
            playlist = null;
            pending = null;
            cleanup(mc);
            return;
        }
        if (!want.equals(playlist)) {
            if (!want.equals(pending)) {
                pending = want;
                pendingTicks = playlist == null ? 0 : num("settle_ticks", 100);
            } else if (--pendingTicks <= 0) {
                switchTo(want);
            }
        } else {
            pending = null;
        }
        cleanup(mc);
        if (playing == null && playlist != null && --silence <= 0) {
            playNext(mc);
        }
    }

    private static void switchTo(String next) {
        boolean keep = playing != null && !playing.fading()
                && transition.has("keep_shared_track") && transition.get("keep_shared_track").getAsBoolean()
                && PLAYLISTS.get(next).contains(playing.event);
        playlist = next;
        pending = null;
        queue.clear();
        if (!keep && playing != null) {
            playing.fadeOut(num("fade_out_ticks", 80));
            silence = num("fade_out_ticks", 80) + num("change_gap_ticks", 60);
        } else if (playing == null) {
            silence = num("change_gap_ticks", 60);
        }
    }

    private static void cleanup(Minecraft mc) {
        if (playing != null && (playing.isStopped() || !mc.getSoundManager().isActive(playing))
                && (playing.started || ++playing.waited > 40)) { // a track that never starts: missing file
            boolean faded = playing.fading();
            playing = null;
            if (!faded) {
                int lo = num("gap_min_ticks", 400);
                silence = lo + RANDOM.nextInt(Math.max(1, num("gap_max_ticks", 1800) - lo + 1));
            }
        }
    }

    private static void playNext(Minecraft mc) {
        if (queue.isEmpty()) {
            List<ResourceLocation> list = new ArrayList<>(PLAYLISTS.get(playlist));
            Collections.shuffle(list, new java.util.Random(RANDOM.nextLong()));
            queue.addAll(list);
        }
        ResourceLocation id = queue.poll();
        playing = new Track(id, (float) (transition.has("volume") ? transition.get("volume").getAsDouble() : 1.0),
                num("fade_in_ticks", 40));
        mc.getSoundManager().play(playing);
    }

    /** Vanilla's music stays quiet in Middle-earth while a playlist has tracks. */
    @SubscribeEvent
    static void vanillaMusic(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s != null && s.getSource() == SoundSource.MUSIC && !(s instanceof Track)
                && here(Minecraft.getInstance()) != null) {
            event.setSound(null);
        }
    }

    /** One track, with fades. */
    static final class Track extends AbstractTickableSoundInstance {
        final ResourceLocation event;
        private final float full;
        private final int fadeIn;
        private int age;
        private int fadeOut = -1;
        private int fadeTotal;
        boolean started;
        int waited;

        Track(ResourceLocation event, float volume, int fadeIn) {
            super(SoundEvent.createVariableRangeEvent(event), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.event = event;
            this.full = volume;
            this.fadeIn = Math.max(1, fadeIn);
            this.volume = 0.001f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.looping = false;
        }

        void fadeOut(int ticks) {
            if (fadeOut < 0) {
                fadeTotal = Math.max(1, ticks);
                fadeOut = fadeTotal;
            }
        }

        boolean fading() {
            return fadeOut >= 0;
        }

        @Override
        public void tick() {
            started = true;
            age++;
            float v = full * Math.min(1f, age / (float) fadeIn);
            if (fadeOut >= 0) {
                v *= fadeOut / (float) fadeTotal;
                if (--fadeOut <= 0) {
                    stop();
                }
            }
            volume = Math.max(0.001f, v);
        }
    }
}
