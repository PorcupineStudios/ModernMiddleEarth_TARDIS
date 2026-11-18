package com.porcupinestudios.lotrport.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The biome config, {@code config/lotr_port-biomes.toml} (COMMON, editable in Mods > Config): every Middle-earth
 * biome under its land and region ({@code land.region.biome.setting}), each with its cave settings (Aaron
 * 2026-11-18: tone down the cave entrances and ravines, keep the caves below; a config for every biome).
 *
 * <p>The list of biomes, the settings and their defaults come from {@code data/lotr_port/biome_config.json}
 * (converter/biomeconfig.py), read from the mod jar when the mod loads, so the config always has every biome. Who
 * uses the settings: the noise caves through the density function {@code lotr_port:cave_setting} ({@link
 * CaveSetting}), ravines and carver tunnels through the port's carvers (world/feature/BiomeCarvers), cave pockets
 * through {@code lotr_port:cave_pocket}. Changes apply to chunks generated afterwards.
 */
public final class BiomeCaveConfig {
    /** One biome's resolved settings. */
    public record Settings(double caveEntrances, double ravines, double carverCaves, int surfaceCover,
            boolean cheeseCaves, boolean spaghettiCaves, boolean noodleCaves, double lushCaves, double dripstoneCaves) {}

    private static final String FILE = "/data/lotr_port/biome_config.json";
    /** Every biome's config values by setting key, by biome name ("shire"). */
    private static final Map<String, Map<String, ModConfigSpec.ConfigValue<?>>> VALUES = new LinkedHashMap<>();
    /** Every biome's land and region, for tests and tools. */
    private static final Map<String, String> PLACES = new LinkedHashMap<>();
    /** The defaults, used before the config has loaded. */
    private static final Map<String, Settings> DEFAULTS = new HashMap<>();
    private static final Settings FALLBACK = new Settings(1, 1, 1, 0, true, true, true, 1, 1);
    public static final ModConfigSpec SPEC = build();

    private static volatile Map<String, Settings> resolved;
    private static volatile Table table;
    /** The last column looked up on this thread: carvers ask for the same column block after block. */
    private static final ThreadLocal<long[]> LAST_KEY = ThreadLocal.withInitial(() -> new long[] {Long.MIN_VALUE});
    private static final ThreadLocal<Settings[]> LAST = ThreadLocal.withInitial(() -> new Settings[1]);

    private BiomeCaveConfig() {}

    private static ModConfigSpec build() {
        JsonObject root;
        try (InputStream in = BiomeCaveConfig.class.getResourceAsStream(FILE)) {
            if (in == null) {
                throw new IllegalStateException("missing " + FILE + " (run converter/biomeconfig.py)");
            }
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("can't read " + FILE, e);
        }
        Map<String, JsonObject> settings = new LinkedHashMap<>();
        for (JsonElement s : root.getAsJsonArray("settings")) {
            settings.put(s.getAsJsonObject().get("key").getAsString(), s.getAsJsonObject());
        }
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        // the file's header goes on the first land's section
        List<String> header = new ArrayList<>(List.of(
                "Middle-earth's cave settings, biome by biome, grouped by land and region (LOTR's waypoint regions).",
                "Each biome has its own section: [land.region.biome]. Changes apply to chunks generated afterwards;",
                "chunks already generated keep their caves. Every setting lists its valid range or options.",
                "", "Settings:"));
        for (JsonObject s : settings.values()) {
            header.add("  " + s.get("key").getAsString() + " (" + options(s) + "): " + s.get("help").getAsString());
        }
        header.add("");
        for (var land : root.getAsJsonObject("lands").entrySet()) {
            header.add("Land: " + pretty(land.getKey()));
            b.comment(header.toArray(String[]::new)).push(land.getKey());
            header.clear();
            for (var region : land.getValue().getAsJsonObject().entrySet()) {
                b.comment("Region: " + pretty(region.getKey())).push(region.getKey());
                for (var biome : region.getValue().getAsJsonObject().entrySet()) {
                    String name = biome.getKey();
                    JsonObject def = biome.getValue().getAsJsonObject();
                    b.comment("Biome: " + pretty(name)).push(name);
                    Map<String, ModConfigSpec.ConfigValue<?>> values = new LinkedHashMap<>();
                    for (var e : def.entrySet()) {
                        JsonObject s = settings.get(e.getKey());
                        if (s == null) {
                            throw new IllegalStateException(FILE + ": " + name + " has unknown setting " + e.getKey());
                        }
                        b.comment(s.get("help").getAsString(), "Valid: " + options(s));
                        values.put(e.getKey(), switch (s.get("kind").getAsString()) {
                            case "switch" -> b.define(e.getKey(), e.getValue().getAsBoolean());
                            case "blocks" -> b.defineInRange(e.getKey(), e.getValue().getAsInt(),
                                    s.get("min").getAsInt(), s.get("max").getAsInt());
                            default -> b.defineInRange(e.getKey(), e.getValue().getAsDouble(),
                                    s.get("min").getAsDouble(), s.get("max").getAsDouble());
                        });
                    }
                    VALUES.put(name, values);
                    PLACES.put(name, land.getKey() + "." + region.getKey());
                    DEFAULTS.put(name, settings(def::has, k -> def.get(k).getAsDouble(), k -> def.get(k).getAsBoolean()));
                    b.pop();
                }
                b.pop();
            }
            b.pop();
        }
        return b.build();
    }

    private static String options(JsonObject s) {
        return switch (s.get("kind").getAsString()) {
            case "switch" -> "true or false";
            case "blocks" -> "whole number " + s.get("min").getAsInt() + " to " + s.get("max").getAsInt();
            default -> "decimal " + s.get("min").getAsDouble() + " to " + s.get("max").getAsDouble();
        };
    }

    private static String pretty(String key) {
        StringBuilder out = new StringBuilder();
        for (String w : key.split("_")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    private static Settings settings(java.util.function.Predicate<String> has,
            java.util.function.ToDoubleFunction<String> number, java.util.function.Predicate<String> flag) {
        return new Settings(number.applyAsDouble("caveEntrances"), number.applyAsDouble("ravines"),
                number.applyAsDouble("carverCaves"), (int) number.applyAsDouble("surfaceCover"),
                flag.test("cheeseCaves"), flag.test("spaghettiCaves"), flag.test("noodleCaves"),
                has.test("lushCaves") ? number.applyAsDouble("lushCaves") : 1,
                has.test("dripstoneCaves") ? number.applyAsDouble("dripstoneCaves") : 1);
    }

    /** Mod bus: a load or an edit (file or Mods > Config) of this config drops the resolved settings. */
    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            reload();
        }
    }

    /** Forgets the resolved settings (after a change). */
    public static void reload() {
        resolved = null;
        table = null;
        LAST_KEY.remove();
        LAST.remove();
    }

    /** The biomes the config has, by name, with their "land.region". */
    public static Map<String, String> places() {
        return java.util.Collections.unmodifiableMap(PLACES);
    }

    /** A biome's config values by setting key (tests change them through these), or null. */
    public static Map<String, ModConfigSpec.ConfigValue<?>> values(String biome) {
        return VALUES.get(biome);
    }

    /** A biome's settings by name ("shire" or "lotr_port:shire"); a biome the config doesn't have gets vanilla's. */
    public static Settings of(String biome) {
        String name = biome.substring(biome.indexOf(':') + 1);
        Map<String, Settings> map = resolved;
        if (map == null) {
            map = resolve();
        }
        return map.getOrDefault(name, FALLBACK);
    }

    private static Map<String, Settings> resolve() {
        Map<String, Settings> map = new HashMap<>();
        boolean loaded = true;
        for (var e : VALUES.entrySet()) {
            Map<String, ModConfigSpec.ConfigValue<?>> v = e.getValue();
            try {
                map.put(e.getKey(), settings(v::containsKey, k -> ((Number) v.get(k).get()).doubleValue(),
                        k -> (Boolean) v.get(k).get()));
            } catch (IllegalStateException notLoaded) { // before the config file has loaded: the defaults
                map.put(e.getKey(), DEFAULTS.get(e.getKey()));
                loaded = false;
            }
        }
        if (loaded) {
            resolved = map;
        }
        return map;
    }

    /** The settings of the Middle-earth biome at a column (the map's biome, the sea of its latitude at sea). */
    public static Settings at(int x, int z) {
        long key = (long) x << 32 | (z & 0xFFFFFFFFL);
        long[] lastKey = LAST_KEY.get();
        Settings[] last = LAST.get();
        if (lastKey[0] == key && last[0] != null) {
            return last[0];
        }
        MiddleEarthMap map = MiddleEarthMap.get();
        Table t = table;
        if (t == null || t.map != map) {
            t = new Table(map);
            if (resolved != null) {
                table = t;
            }
        }
        int i = map.biomeIndexAt(x, z);
        Settings s = i == map.oceanIndex() && t.seas.length > 0 ? t.seas[map.seaAt(x, z)] : t.land[i];
        lastKey[0] = key;
        last[0] = s;
        return s;
    }

    /** The settings by the map's biome index and sea index. */
    private static final class Table {
        final MiddleEarthMap map;
        final Settings[] land;
        final Settings[] seas;

        Table(MiddleEarthMap map) {
            this.map = map;
            List<Settings> l = new ArrayList<>();
            for (MiddleEarthMap.Entry e : map.biomes()) {
                l.add(of(e.id()));
            }
            this.land = l.toArray(Settings[]::new);
            List<Settings> s = new ArrayList<>();
            for (MiddleEarthMap.SeaZone zone : map.seaZones()) {
                s.add(of(zone.biome()));
            }
            this.seas = s.toArray(Settings[]::new);
        }
    }

    // ---- what the world generation asks

    /** Whether a ravine (canyon) or a carver tunnel starting in this chunk is carved: the start biome's share. */
    public static boolean carves(boolean ravine, ChunkPos start) {
        Settings s = at(start.getMinBlockX(), start.getMinBlockZ());
        double share = ravine ? s.ravines() : s.carverCaves();
        if (share >= 1) {
            return true;
        }
        if (share <= 0) {
            return false;
        }
        long h = start.x * 0x9E3779B97F4A7C15L ^ start.z * 0xC2B2AE3D27D4EB4FL ^ (ravine ? 0x5851F42D4C957F2DL : 0);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53 < share;
    }

    /** Whether a carver must leave this block: it is within the biome's surface cover of the ground. */
    public static boolean covered(ChunkAccess chunk, BlockPos pos) {
        int cover = at(pos.getX(), pos.getZ()).surfaceCover();
        return cover > 0 && pos.getY() > chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, pos.getX(), pos.getZ()) - cover;
    }

    /** How many cave pockets of a kind ("lush", "dripstone") the biome at a column has, against the default 1. */
    public static double pockets(String kind, int x, int z) {
        Settings s = at(x, z);
        return switch (kind) {
            case "lush" -> s.lushCaves();
            case "dripstone" -> s.dripstoneCaves();
            default -> 1;
        };
    }

    /**
     * A {@link CaveSetting} value at a column. Entrances: how much the near-ground part of the noise caves is held
     * back, for the big pits (the cave_entrance noise) and the tunnel mouths (spaghetti): 0 at caveEntrances 1
     * (vanilla), more as it falls, 64 (none) at 0. The cave types: 1 on, 0 off.
     */
    public static double value(String setting, int x, int z) {
        Settings s = at(x, z);
        return switch (setting) {
            case "entrance_pits" -> s.caveEntrances() <= 0 ? 64 : (1 - s.caveEntrances()) * PIT_HOLD;
            case "entrance_tunnels" -> s.caveEntrances() <= 0 ? 64 : (1 - s.caveEntrances()) * TUNNEL_HOLD;
            case "cheese" -> s.cheeseCaves() ? 1 : 0;
            case "spaghetti" -> s.spaghettiCaves() ? 1 : 0;
            case "noodle" -> s.noodleCaves() ? 1 : 0;
            default -> throw new IllegalArgumentException("unknown cave setting " + setting);
        };
    }

    /**
     * How far vanilla's entrance noises are pushed back at caveEntrances 0+: the pits open where 0.37 + the
     * cave_entrance noise is under about 0..0.3, so 0.6 leaves only the noise's rare deep dips; the tunnel mouths are
     * within about 0.15 of opening.
     */
    private static final double PIT_HOLD = 0.6;
    private static final double TUNNEL_HOLD = 0.15;

    /** The setting names {@link CaveSetting} takes. */
    public static final List<String> SETTING_NAMES = List.of("entrance_pits", "entrance_tunnels", "cheese",
            "spaghetti", "noodle");
}
