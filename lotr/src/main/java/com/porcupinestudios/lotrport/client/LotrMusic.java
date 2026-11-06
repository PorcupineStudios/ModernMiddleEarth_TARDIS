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
import java.util.Iterator;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
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
 * <li>{@code playlists}: name → {@code {"calm": [...], "suspense": [...], "fight": [...]}} of sound event ids, or
 * one list for every mood. The events are the pack's own (its sounds.json), so the tracks live only in the pack,
 * never in the mod. One track may sit on several lists.</li>
 * <li>{@code transition} and {@code mood}: timings and mood triggers, see {@code lotr/docs/music-pack.md}.</li>
 * </ul>
 * The mood: fight while a mob or NPC within {@code fight_range} is aggressive (has a target); suspense near
 * hostiles, at night, in thunder, deep underground or on low health; calm otherwise. Each holds a while after its
 * trigger ends. The list played is the land's list for the mood, internally keyed "land/mood", so every rule below
 * (shared tracks, settling, stepping back) works for mood changes too; a fight cuts in faster.
 * Changing playlist:
 * <ul>
 * <li>If the playing track is also on the new list, it plays on untouched and the new list's shuffle follows it
 * (without that track coming straight back). This happens at once: nothing audible changes.</li>
 * <li>Otherwise, once the player has stayed {@code settle_ticks}, it crossfades: the track fades out over
 * {@code fade_out_ticks} while the new list's first track starts {@code crossfade_delay_ticks} later and fades in
 * over {@code fade_in_ticks}.</li>
 * <li>Stepping back onto a list whose track is still fading out brings that track back up instead of cutting it.</li>
 * <li>Arriving during the silence between tracks, the new list starts within {@code crossfade_delay_ticks}.</li>
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

    /** The list now playing (null outside Middle-earth). */
    private static String playlist;
    /** What's left of this round of the shuffle. */
    private static final Deque<ResourceLocation> queue = new ArrayDeque<>();
    /** The track at (or fading in to) full volume. */
    private static Track playing;
    /** The last track started, so a new round of the shuffle doesn't open with it. */
    private static ResourceLocation last;
    /** Tracks fading out after a change; each stops itself at silence. */
    private static final List<Track> outgoing = new ArrayList<>();
    private static String pending;
    private static int pendingTicks;
    private static int silence;

    private LotrMusic() {}

    private static int num(String key, int fallback) {
        return transition.has(key) ? transition.get(key).getAsInt() : fallback;
    }

    private static boolean keepShared() {
        return !transition.has("keep_shared_track") || transition.get("keep_shared_track").getAsBoolean();
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
                        // {"calm": [...], "suspense": [...], "fight": [...]}, or one list for every mood
                        JsonElement v = e.getValue();
                        for (Mood m : Mood.values()) {
                            JsonElement list = v.isJsonObject() ? v.getAsJsonObject().get(m.id) : v;
                            if (list != null && list.isJsonArray()) {
                                put(e.getKey() + "/" + m.id, list);
                            }
                        }
                    }
                }
                for (String section : new String[] {"transition", "mood"}) {
                    if (o.has(section)) {
                        for (var e : o.getAsJsonObject(section).entrySet()) {
                            transition.add(e.getKey(), e.getValue());
                        }
                    }
                }
            } catch (Exception e) {
                LogUtils.getLogger().warn("Bad music playlist file in pack {}", r.sourcePackId(), e);
            }
        }
        stop();
    }

    private static void put(String key, JsonElement list) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (JsonElement t : list.getAsJsonArray()) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        // a later pack with an empty list doesn't wipe an earlier pack's tracks
        if (!ids.isEmpty() || !PLAYLISTS.containsKey(key)) {
            PLAYLISTS.put(key, ids);
        }
    }

    private static void stop() {
        var sm = Minecraft.getInstance().getSoundManager();
        if (playing != null) {
            sm.stop(playing);
        }
        outgoing.forEach(sm::stop);
        outgoing.clear();
        playing = null;
        playlist = null;
        pending = null;
        last = null;
        queue.clear();
    }

    /** The region's playlist for where the player is, or null outside Middle-earth. */
    private static String region(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        return mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                .filter(k -> k.location().getNamespace().equals("lotr_port"))
                .map(k -> BIOMES.get(k.location().getPath()))
                .orElse(null);
    }

    /**
     * The list to play: "region/mood", falling back to the nearest mood that has tracks (fight → suspense → calm,
     * suspense → calm → fight, calm → suspense → fight). Null outside Middle-earth or with no tracks at all.
     */
    private static String here(Minecraft mc) {
        String r = region(mc);
        if (r == null) {
            return null;
        }
        for (Mood m : mood.fallbacks) {
            String key = r + "/" + m.id;
            if (!PLAYLISTS.getOrDefault(key, List.of()).isEmpty()) {
                return key;
            }
        }
        return null;
    }

    /** Which music the moment calls for. Triggers and hold times: the "mood" section of playlists.json. */
    enum Mood {
        CALM("calm"), SUSPENSE("suspense"), FIGHT("fight");

        final String id;
        Mood[] fallbacks;

        Mood(String id) {
            this.id = id;
        }

        static {
            CALM.fallbacks = new Mood[] {CALM, SUSPENSE, FIGHT};
            SUSPENSE.fallbacks = new Mood[] {SUSPENSE, CALM, FIGHT};
            FIGHT.fallbacks = new Mood[] {FIGHT, SUSPENSE, CALM};
        }
    }

    private static Mood mood = Mood.CALM;
    private static long lastFight = Long.MIN_VALUE / 2;
    private static long lastSuspense = Long.MIN_VALUE / 2;
    private static long ticks;

    private static Mood updateMood(Minecraft mc) {
        ticks++;
        var p = mc.player;
        var level = mc.level;
        if (p == null || level == null) {
            return Mood.CALM;
        }
        if (ticks % 5 == 0) { // looking around 4 times a second is plenty
            double fightRange = num("fight_range", 16);
            double suspenseRange = num("suspense_range", 32);
            boolean fight = false;
            boolean danger = false;
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(suspenseRange),
                    m -> m.isAlive() && !m.isSpectator())) {
                double d = m.distanceTo(p);
                // the aggressive flag is synced: vanilla melee mobs and every LotrNpc set it while they have a target
                if (m.isAggressive() && d <= fightRange) {
                    fight = true;
                } else if (m instanceof Enemy || m.isAggressive()) {
                    danger = true;
                }
            }
            // hit while a hostile is close: someone is fighting us even if we can't see who
            if (p.hurtTime > 0 && danger && num("hurt_starts_fight", 1) != 0) {
                fight = true;
            }
            long day = level.getDayTime() % 24000;
            boolean night = !level.dimensionType().hasFixedTime() && day > 13000 && day < 23000;
            boolean underground = !level.canSeeSky(p.blockPosition())
                    && p.getBlockY() < level.getSeaLevel() - num("underground_depth", 8);
            boolean hurt = p.getHealth() / p.getMaxHealth() < num("low_health_percent", 35) / 100f;
            if (fight) {
                lastFight = ticks;
            }
            if (danger || hurt || (night && num("night_is_suspense", 1) != 0)
                    || (level.isThundering() && num("storm_is_suspense", 1) != 0)
                    || (underground && num("underground_is_suspense", 1) != 0)) {
                lastSuspense = ticks;
            }
        }
        // each mood holds a while after its last trigger, so a lull mid-fight doesn't flip the music
        if (ticks - lastFight <= num("fight_hold_ticks", 400)) {
            return Mood.FIGHT;
        }
        if (ticks - lastSuspense <= num("suspense_hold_ticks", 300)) {
            return Mood.SUSPENSE;
        }
        return Mood.CALM;
    }

    private static boolean shares(String list, Track t) {
        return t != null && PLAYLISTS.getOrDefault(list, List.of()).contains(t.event);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        cleanup(mc);
        mood = updateMood(mc);
        String want = here(mc);
        if (want == null) {
            if (playlist != null) {
                retire();
            }
            playlist = null;
            pending = null;
            return;
        }
        if (!want.equals(playlist)) {
            // no settle wait when nothing would cut off: the track plays on, or we're undoing a fade
            boolean smooth = keepShared() && (shares(want, playing) || outgoing.stream().anyMatch(t -> shares(want, t)));
            if (playlist == null || smooth) {
                switchTo(want);
            } else if (!want.equals(pending)) {
                pending = want;
                // a new land waits settle_ticks (border walking); a new mood in the same land is quicker, a fight
                // quickest. Mood flapping is handled by the hold times, not here.
                boolean sameLand = playlist.substring(0, playlist.indexOf('/')).equals(want.substring(0,
                        want.indexOf('/')));
                pendingTicks = !sameLand ? num("settle_ticks", 100)
                        : want.endsWith("/fight") ? num("fight_settle_ticks", 10) : num("mood_settle_ticks", 40);
            } else if (--pendingTicks <= 0) {
                switchTo(want);
            }
        } else {
            pending = null;
        }
        if (playing == null && --silence <= 0) {
            playNext(mc);
        }
    }

    private static void switchTo(String next) {
        playlist = next;
        pending = null;
        queue.clear();
        if (keepShared() && shares(next, playing)) {
            refill(playing.event, null); // the shared track plays on; the new list's shuffle follows it
            return;
        }
        // stepping back onto a list whose track is still fading out: bring it back up rather than cut it
        Track back = null;
        if (keepShared()) {
            for (Track t : outgoing) {
                if (shares(next, t) && (back == null || t.level > back.level)) {
                    back = t;
                }
            }
        }
        retire(fighting() ? num("fight_fade_out_ticks", 40) : num("fade_out_ticks", 100));
        if (back != null) {
            outgoing.remove(back);
            back.fadeTo(1f, fadeIn());
            playing = back;
            refill(back.event, null);
        } else {
            silence = Math.min(silence, num("crossfade_delay_ticks", 20));
        }
    }

    private static boolean fighting() {
        return playlist != null && playlist.endsWith("/fight");
    }

    private static int fadeIn() {
        return fighting() ? num("fight_fade_in_ticks", 30) : num("fade_in_ticks", 80);
    }

    private static void retire() {
        retire(num("fade_out_ticks", 100));
    }

    /** Sends the playing track off to fade out; the next one may start crossfade_delay_ticks later. */
    private static void retire(int fadeTicks) {
        if (playing != null) {
            playing.fadeTo(0f, fadeTicks);
            outgoing.add(playing);
            playing = null;
            silence = num("crossfade_delay_ticks", 20);
        }
    }

    /**
     * A fresh round of the current list's shuffle. {@code exclude} (playing now, so this round has it) is left out;
     * {@code notFirst} (just played) doesn't open the round. Both only when the list has other tracks.
     */
    private static void refill(ResourceLocation exclude, ResourceLocation notFirst) {
        List<ResourceLocation> list = new ArrayList<>(PLAYLISTS.get(playlist));
        Collections.shuffle(list, new java.util.Random(RANDOM.nextLong()));
        if (exclude != null && list.size() > 1) {
            list.remove(exclude);
        }
        if (notFirst != null && list.size() > 1 && list.get(0).equals(notFirst)) {
            list.add(list.remove(0));
        }
        queue.addAll(list);
    }

    private static void cleanup(Minecraft mc) {
        var sm = mc.getSoundManager();
        for (Iterator<Track> it = outgoing.iterator(); it.hasNext();) {
            Track t = it.next();
            if (t.isStopped() || (t.started && !sm.isActive(t)) || (!t.started && ++t.waited > 40)) {
                sm.stop(t);
                it.remove();
            }
        }
        if (playing != null && (playing.isStopped() || !sm.isActive(playing))
                && (playing.started || ++playing.waited > 40)) { // a track that never starts: missing file
            playing = null;
            int lo = num("gap_min_ticks", 400);
            silence = fighting() ? num("fight_gap_ticks", 40) // a fight doesn't go quiet for a minute
                    : lo + RANDOM.nextInt(Math.max(1, num("gap_max_ticks", 1800) - lo + 1));
        }
    }

    private static void playNext(Minecraft mc) {
        if (playlist == null) {
            return;
        }
        if (queue.isEmpty()) {
            refill(null, last);
        }
        ResourceLocation id = queue.poll();
        if (id == null) {
            return;
        }
        mc.getMusicManager().stopPlaying(); // vanilla music that was already going when we arrived
        last = id;
        playing = new Track(id, transition.has("volume") ? transition.get("volume").getAsFloat() : 1f);
        playing.fadeTo(1f, fadeIn());
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

    /** One track; its level glides toward a target, and it stops itself on fading to silence. */
    static final class Track extends AbstractTickableSoundInstance {
        final ResourceLocation event;
        private final float full;
        float level;
        private float target;
        private float step;
        boolean started;
        int waited;

        Track(ResourceLocation event, float volume) {
            super(SoundEvent.createVariableRangeEvent(event), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.event = event;
            this.full = volume;
            this.volume = 0.001f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.looping = false;
        }

        void fadeTo(float to, int ticks) {
            target = to;
            step = Math.max(Math.abs(to - level), 0.001f) / Math.max(1, ticks);
        }

        @Override
        public void tick() {
            started = true;
            if (level < target) {
                level = Math.min(target, level + step);
            } else if (level > target) {
                level = Math.max(target, level - step);
            }
            if (target <= 0f && level <= 0f) {
                stop();
            }
            volume = Math.max(0.001f, full * level);
        }
    }
}
