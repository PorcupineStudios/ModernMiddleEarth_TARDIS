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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
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
 * one list for every mood. The events are the pack's own (its sounds.json): the built-in "Middle-earth Music" pack
 * registered below, or any resource pack over it. One track may sit on several lists.</li>
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
    private static final ResourceLocation TRACKS_FILE = ResourceLocation.fromNamespaceAndPath("lotr_port",
            "music/tracks.json");
    /** Title and album per track event (assets/<ns>/music/tracks.json, written by music_pack.py). */
    private static final Map<ResourceLocation, String[]> TRACKS = new HashMap<>();
    /** Tracks the player took out of the shuffle on the jukebox screen; saved in config/lotr_port_jukebox.json. */
    private static final java.util.Set<ResourceLocation> DISABLED = new java.util.TreeSet<>();
    private static boolean disabledLoaded;
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

        /**
         * The soundtrack can ship in the jar as its own built-in pack (resources/resourcepacks/middle_earth_music),
         * always on: its sounds.json and playlists.json merge over the mod's. The Music slider sets its volume. The pack
         * is not in this repo: it lives in the private repo PorcupineStudios/middle-earth-music and is copied into that
         * folder for a private build (lotr/docs/music.md). Without it the mod builds and runs with no soundtrack.
         */
        @SubscribeEvent
        static void pack(AddPackFindersEvent event) {
            if (LotrMusic.class.getResource("/resourcepacks/middle_earth_music/pack.mcmeta") == null) {
                return;
            }
            event.addPackFinders(ResourceLocation.fromNamespaceAndPath("lotr_port", "resourcepacks/middle_earth_music"),
                    PackType.CLIENT_RESOURCES, Component.literal("Middle-earth Music"), PackSource.BUILT_IN, true,
                    Pack.Position.TOP);
        }
    }

    private static void load(ResourceManager rm) {
        TRACKS.clear();
        for (Resource r : rm.getResourceStack(TRACKS_FILE)) {
            try (Reader in = r.openAsReader()) {
                for (var e : JsonParser.parseReader(in).getAsJsonObject().entrySet()) {
                    ResourceLocation id = ResourceLocation.tryParse(e.getKey());
                    JsonObject t = e.getValue().getAsJsonObject();
                    if (id != null) {
                        TRACKS.put(id, new String[] { t.has("title") ? t.get("title").getAsString() : "",
                                t.has("album") ? t.get("album").getAsString() : "" });
                    }
                }
            } catch (Exception e) {
                LogUtils.getLogger().warn("Bad music track file in pack {}", r.sourcePackId(), e);
            }
        }
        loadDisabled();
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
        // on a road or underground: the land's own list for that place ("shire_road"), then the place's list for
        // all of Middle-earth ("road", "underground"), then the land's; each with its mood fallbacks
        List<String> lists = place.isEmpty() ? List.of(r) : List.of(r + "_" + place, place, r);
        for (String list : lists) {
            for (Mood m : mood.fallbacks) {
                String key = list + "/" + m.id;
                if (!PLAYLISTS.getOrDefault(key, List.of()).isEmpty()) {
                    return key;
                }
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
    /** Where the player is, besides the land: on a road, underground, or neither (""). */
    private static String place = "";
    private static long placeSince;
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
            boolean underground = underground(level, p.blockPosition());
            updatePlace(level, p.blockPosition(), underground);
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

    /**
     * Underground: no sky above, no daylight reaching the spot, and at least {@code underground_depth} blocks below
     * the surface of that column (so caves under mountains count, a house's cellar doesn't).
     */
    private static boolean underground(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (level.canSeeSky(pos) || level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) > 0) {
            return false;
        }
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, pos.getX(),
                pos.getZ());
        return surface - pos.getY() >= num("underground_depth", 8);
    }

    /** Distance to the nearest of LOTR's roads (the map's), searched up to {@code range} blocks. */
    private static double roadDistance(int x, int z, int range) {
        var roads = com.porcupinestudios.lotrport.world.map.Roads.get();
        double best = Double.POSITIVE_INFINITY;
        int[][] probes = { { 0, 0 }, { range, 0 }, { -range, 0 }, { 0, range }, { 0, -range } };
        for (int[] pr : probes) {
            for (var seg : roads.near(x + pr[0], z + pr[1])) {
                best = Math.min(best, Math.sqrt(seg.distanceSq(x + 0.5, z + 0.5)));
            }
        }
        return best - roads.width();
    }

    /**
     * The place, with hysteresis so a road's edge or a cave mouth doesn't flip it: a road is joined within
     * {@code road_range} blocks of its edge and left past {@code road_leave_range}; you must be above ground and
     * in Middle-earth's own world (the roads are the map's). A new place must hold {@code place_settle_ticks}.
     */
    private static void updatePlace(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            boolean underground) {
        String want;
        if (underground) {
            want = "underground";
        } else {
            double d = roadDistance(pos.getX(), pos.getZ(), num("road_leave_range", 40));
            boolean onRoad = d <= num(place.equals("road") ? "road_leave_range" : "road_range",
                    place.equals("road") ? 40 : 16);
            // the road has to be near the ground you're on, not far below a cliff or bridge
            int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    pos.getX(), pos.getZ());
            want = onRoad && Math.abs(pos.getY() - surface) <= num("road_height", 12) ? "road" : "";
        }
        if (!want.equals(place)) {
            if (placeSince == 0) {
                placeSince = ticks;
            } else if (ticks - placeSince >= num("place_settle_ticks", 100)) {
                place = want;
                placeSince = 0;
            }
        } else {
            placeSince = 0;
        }
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
        list.removeAll(DISABLED);
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
        announce(mc, id);
    }

    // ------------------------------------------------------------------ jukebox

    /** The jukebox line above the hotbar, as a record in a jukebox shows: title and album. */
    private static void announce(Minecraft mc, ResourceLocation id) {
        if (num("announce", 1) == 0) {
            return;
        }
        mc.gui.setNowPlaying(net.minecraft.network.chat.Component.literal(describe(id)));
    }

    /** "Title — Album" (or the event id when the pack gives no title). */
    public static String describe(ResourceLocation id) {
        String[] t = TRACKS.get(id);
        if (t == null || t[0].isEmpty()) {
            return id.getPath().replace("music.track.", "").replace('_', ' ');
        }
        return t[1].isEmpty() ? t[0] : t[0] + " \u2014 " + t[1];
    }

    public static String title(ResourceLocation id) {
        String[] t = TRACKS.get(id);
        return t == null || t[0].isEmpty() ? id.getPath().replace("music.track.", "").replace('_', ' ') : t[0];
    }

    public static String album(ResourceLocation id) {
        String[] t = TRACKS.get(id);
        return t == null ? "" : t[1];
    }

    public static ResourceLocation nowPlaying() {
        return playing == null ? null : playing.event;
    }

    /** The list playing now ("shire/calm", "road/fight"...), or null. */
    public static String currentList() {
        return playlist;
    }

    /**
     * Every list that can play where the player stands, in the order they're tried: for the current place (if any)
     * the land's own, then Middle-earth's, then the land's lists; each in calm, suspense, fight.
     */
    public static java.util.LinkedHashMap<String, List<ResourceLocation>> eligible(Minecraft mc) {
        var out = new java.util.LinkedHashMap<String, List<ResourceLocation>>();
        String r = region(mc);
        if (r == null) {
            return out;
        }
        List<String> lists = place.isEmpty() ? List.of(r) : List.of(r + "_" + place, place, r);
        for (String list : lists) {
            for (Mood m : Mood.values()) {
                List<ResourceLocation> ids = PLAYLISTS.get(list + "/" + m.id);
                if (ids != null && !ids.isEmpty()) {
                    out.put(list + "/" + m.id, ids);
                }
            }
        }
        return out;
    }

    public static String region() {
        return region(Minecraft.getInstance());
    }

    public static String place() {
        return place;
    }

    public static String mood() {
        return mood.id;
    }

    public static boolean enabled(ResourceLocation id) {
        return !DISABLED.contains(id);
    }

    /** Puts a track in or out of every shuffle and saves the choice. Taking out the playing track skips it. */
    public static void setEnabled(ResourceLocation id, boolean on) {
        if (on ? DISABLED.remove(id) : DISABLED.add(id)) {
            queue.remove(id);
            saveDisabled();
            if (!on && playing != null && playing.event.equals(id)) {
                skip();
            }
        }
    }

    /** Fades the current track out; the next one in the shuffle follows after a short pause. */
    public static void skip() {
        if (playing != null) {
            playing.fadeTo(0f, num("skip_fade_ticks", 30));
            outgoing.add(playing);
            playing = null;
            silence = num("skip_gap_ticks", 20);
        }
    }

    /** Plays this track now (fading out the current one), whatever the shuffle had next. */
    public static void playNow(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        skip();
        queue.remove(id);
        queue.addFirst(id);
        silence = num("skip_gap_ticks", 20);
        if (playlist == null) {
            String here = here(mc);
            if (here != null) {
                playlist = here;
            }
        }
    }

    private static java.nio.file.Path disabledFile() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("lotr_port_jukebox.json");
    }

    private static void loadDisabled() {
        if (disabledLoaded) {
            return;
        }
        disabledLoaded = true;
        try {
            var f = disabledFile();
            if (java.nio.file.Files.exists(f)) {
                JsonObject o = JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
                for (JsonElement e : o.getAsJsonArray("disabled")) {
                    ResourceLocation id = ResourceLocation.tryParse(e.isJsonObject()
                            ? e.getAsJsonObject().get("id").getAsString() : e.getAsString());
                    if (id != null) {
                        DISABLED.add(id);
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.getLogger().warn("Could not read the jukebox's saved choices", e);
        }
    }

    /** Saved readable: each removed track with its title and album, so the file doubles as a to-change list. */
    private static void saveDisabled() {
        JsonObject o = new JsonObject();
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (ResourceLocation id : DISABLED) {
            JsonObject t = new JsonObject();
            t.addProperty("id", id.toString());
            t.addProperty("title", title(id));
            t.addProperty("album", album(id));
            com.google.gson.JsonArray in = new com.google.gson.JsonArray();
            PLAYLISTS.forEach((list, ids) -> {
                if (ids.contains(id)) {
                    in.add(list);
                }
            });
            t.add("lists", in);
            a.add(t);
        }
        o.add("disabled", a);
        try {
            java.nio.file.Files.writeString(disabledFile(),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o));
        } catch (java.io.IOException e) {
            LogUtils.getLogger().warn("Could not save the jukebox's choices", e);
        }
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
