package com.porcupinestudios.lotrport.lair;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.border.TerritorySpawns;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcSpawns;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.lotrport.world.map.Lairs;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * What happens to a lair (Aaron 2026-11-25: spiders spawn round a nest, "with spawning being affected by destroying
 * nests"). Each lair of the map ({@link Lairs}) has egg sacs ({@link EggSacBlock}) in its core. Breaking one hatches a
 * few spiders and counts against the lair; how much of it is left ({@link #strength}: 1 untouched, 0 once
 * {@code dead_at} sacs are gone) is the chance that a spawn on its infested ground still brings the lair's creatures,
 * else the ground spawns as {@code fallback_biome} (the plain enchanted forest: spiders only by night).
 *
 * <p>Data, reloaded by {@code /reload}: {@code data/<ns>/lair/<kind>.json} ({@link Settings}). What was broken is
 * saved per world ({@code lotr_port_lairs}, by lair id); lairs are seeded, so their ids are this world's own.
 */
public final class LairLife {
    public record Hatch(ResourceLocation npc, int min, int max, float chance) {
        public static final Codec<Hatch> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("npc").forGetter(Hatch::npc),
                Codec.INT.optionalFieldOf("min", 1).forGetter(Hatch::min),
                Codec.INT.optionalFieldOf("max", 2).forGetter(Hatch::max),
                Codec.floatRange(0, 1).optionalFieldOf("chance", 1f).forGetter(Hatch::chance)).apply(i, Hatch::new));
    }

    /**
     * A kind's numbers: sacs gone before the lair is dead, what hatches from a broken sac, the biome whose spawns its
     * ground falls back to, and the lines shown to a player who breaks a sac (translation keys).
     */
    public record Settings(int deadAt, Optional<Hatch> hatch, Optional<ResourceLocation> fallbackBiome,
            Optional<String> damaged, Optional<String> destroyed) {
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 64).fieldOf("dead_at").forGetter(Settings::deadAt),
                Hatch.CODEC.optionalFieldOf("hatch").forGetter(Settings::hatch),
                ResourceLocation.CODEC.optionalFieldOf("fallback_biome").forGetter(Settings::fallbackBiome),
                Codec.STRING.optionalFieldOf("damaged").forGetter(Settings::damaged),
                Codec.STRING.optionalFieldOf("destroyed").forGetter(Settings::destroyed)).apply(i, Settings::new));
    }

    private static final CodecJsonLoader<Settings> LOADER = new CodecJsonLoader<>("lair", Settings.CODEC);
    private static final Settings DEFAULT = new Settings(1, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty());

    private LairLife() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
        NeoForge.EVENT_BUS.addListener(LairLife::onSpawn);
    }

    /** A kind's settings (defaults: dead after one sac, nothing hatches, no fallback). */
    public static Settings settings(Lairs.Kind kind) {
        return LOADER.entries().getOrDefault(LotrPort.id(kind.name()), DEFAULT);
    }

    /** How much of a lair is left: 1 untouched, 0 dead. */
    public static double strength(MinecraftServer server, Lairs.Lair lair) {
        int broken = State.get(server).broken(lair.id());
        return Math.max(0, 1 - broken / (double) settings(lair.kind()).deadAt());
    }

    /** An egg sac at {@code pos} is gone (broken, blown up...): it hatches, and counts against its lair if it has one. */
    public static void sacGone(ServerLevel level, BlockPos pos) {
        MiddleEarthMap map = MiddleEarthMap.get();
        Lairs.Lair lair = LotrDimensions.isMiddleEarth(level) ? map.lairAt(pos.getX(), pos.getZ()) : null;
        Settings settings = lair == null ? null : settings(lair.kind());
        Optional<Hatch> hatch = settings != null ? settings.hatch() : defaultHatch();
        hatch.ifPresent(h -> {
            if (level.random.nextFloat() < h.chance()) {
                int n = h.min() + (h.max() > h.min() ? level.random.nextInt(h.max() - h.min() + 1) : 0);
                for (int i = 0; i < n; i++) {
                    LotrNpc.spawn(level, h.npc(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                }
            }
        });
        if (lair == null) {
            return;
        }
        State state = State.get(level.getServer());
        int broken = state.breakSac(lair.id());
        Player near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 12, false);
        Optional<String> line = broken == settings.deadAt() ? settings.destroyed()
                : broken < settings.deadAt() ? settings.damaged() : Optional.empty(); // past dead: nothing more
        if (near != null && line.isPresent()) {
            near.displayClientMessage(Component.translatable(line.get(), broken, settings.deadAt()), true);
        }
    }

    /** Outside any lair a broken sac still hatches, by the first kind's settings (egg sacs placed by hand). */
    private static Optional<Hatch> defaultHatch() {
        for (Lairs.Kind k : MiddleEarthMap.get().lairKinds()) {
            return settings(k).hatch();
        }
        return Optional.empty();
    }

    /** Spawns on a lair's ground bring its creatures only as often as the lair is still whole. */
    private static void onSpawn(TerritorySpawns.SpawnEvent event) {
        BlockPos pos = event.pos();
        Lairs.Lair lair = MiddleEarthMap.get().lairAt(pos.getX(), pos.getZ());
        if (lair == null) {
            return;
        }
        event.setTable(adjust(event.table(), strength(event.level().getServer(), lair), settings(lair.kind()),
                event.level().random.nextDouble()));
    }

    /** The table a spawn on a lair's ground uses: its own with chance {@code strength}, else the fallback's. */
    public static NpcSpawns.Table adjust(NpcSpawns.Table table, double strength, Settings settings, double roll) {
        if (roll < strength) {
            return table;
        }
        return settings.fallbackBiome().flatMap(NpcSpawns::forBiome).orElse(new NpcSpawns.Table(List.of(),
                table.cap(), table.chance(), table.interval(), false, table.radius(), false, 0f));
    }

    /** Broken sacs per lair, saved per world. */
    public static final class State extends SavedData {
        public static final int DATA_VERSION = 1;
        private static final Codec<Map<String, Integer>> BROKEN = Codec.unboundedMap(Codec.STRING, Codec.INT);
        private static final SavedData.Factory<State> FACTORY = new SavedData.Factory<>(State::new, State::load, null);
        private final Map<Long, Integer> broken = new HashMap<>();

        public static State get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "lotr_port_lairs");
        }

        public int broken(long lair) {
            return broken.getOrDefault(lair, 0);
        }

        /** Counts one more broken sac; returns how many are broken now. */
        public int breakSac(long lair) {
            int n = broken.merge(lair, 1, Integer::sum);
            setDirty();
            return n;
        }

        /** For tests: forget a lair's broken sacs. */
        public void heal(long lair) {
            if (broken.remove(lair) != null) {
                setDirty();
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("dataVersion", DATA_VERSION);
            Map<String, Integer> out = new HashMap<>();
            broken.forEach((k, v) -> out.put(Long.toHexString(k), v));
            BROKEN.encodeStart(NbtOps.INSTANCE, out).ifSuccess(t -> tag.put("broken", t));
            return tag;
        }

        static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State s = new State();
            Tag t = tag.get("broken");
            if (t != null) { // dataVersion 1: lair id (hex) -> broken sacs
                BROKEN.parse(NbtOps.INSTANCE, t).ifSuccess(m -> m.forEach((k, v) ->
                        s.broken.put(Long.parseUnsignedLong(k, 16), v)));
            }
            return s;
        }
    }
}
