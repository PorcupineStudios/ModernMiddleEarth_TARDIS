package com.porcupinestudios.lotrport.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Admins' in-game changes to race presets (size and reach), saved with the world ({@code lotr_port_race_tunes}). They
 * win over the {@code player_race} files until reset.
 */
public final class RaceTunes extends SavedData {
    public static final int DATA_VERSION = 1;
    private static final String FILE = "lotr_port_race_tunes";
    private static final SavedData.Factory<RaceTunes> FACTORY =
            new SavedData.Factory<>(RaceTunes::new, RaceTunes::load, null);

    /**
     * A preset's admin numbers: size, reach added for blocks and for hitting, health added (half-hearts), movement
     * speed added (base is 0.1) and hunger drain multiplier. Fields after reach default for saves made before them.
     */
    public record Tune(double scale, double blockReach, double entityReach, double health, double speed,
            double hunger) {
        public static final Codec<Tune> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("scale").forGetter(Tune::scale),
                Codec.DOUBLE.fieldOf("block_reach").forGetter(Tune::blockReach),
                Codec.DOUBLE.fieldOf("entity_reach").forGetter(Tune::entityReach),
                Codec.DOUBLE.optionalFieldOf("health", 0.0).forGetter(Tune::health),
                Codec.DOUBLE.optionalFieldOf("speed", 0.0).forGetter(Tune::speed),
                Codec.DOUBLE.optionalFieldOf("hunger", 1.0).forGetter(Tune::hunger)).apply(i, Tune::new));

        /** This tune with one admin-screen stepper changed ({@code value} as the screen sends it), or null if unknown. */
        public Tune with(String stepper, int value) {
            return switch (stepper) {
                case "size" -> new Tune(clamp(value, 10, 400) / 100.0, blockReach, entityReach, health, speed, hunger);
                case "block" -> new Tune(scale, clamp(value, -40, 40) / 10.0, entityReach, health, speed, hunger);
                case "entity" -> new Tune(scale, blockReach, clamp(value, -40, 40) / 10.0, health, speed, hunger);
                case "health" -> new Tune(scale, blockReach, entityReach, clamp(value, -18, 80), speed, hunger);
                case "speed" -> new Tune(scale, blockReach, entityReach, health, (clamp(value, 25, 300) - 100) / 1000.0,
                        hunger);
                case "hunger" -> new Tune(scale, blockReach, entityReach, health, speed, clamp(value, 0, 500) / 100.0);
                default -> null;
            };
        }

        private static int clamp(int v, int min, int max) {
            return Math.max(min, Math.min(max, v));
        }
    }

    private static final Codec<Map<ResourceLocation, Tune>> MAP = Codec.unboundedMap(ResourceLocation.CODEC, Tune.CODEC);

    private final Map<ResourceLocation, Tune> tunes = new HashMap<>();

    public static RaceTunes get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Optional<Tune> tune(ResourceLocation race) {
        return Optional.ofNullable(tunes.get(race));
    }

    /** Sets a preset's tune, or clears it (back to the file's numbers) with null. */
    public void set(ResourceLocation race, Tune tune) {
        if (tune == null) {
            tunes.remove(race);
        } else {
            tunes.put(race, tune);
        }
        setDirty();
    }

    private static RaceTunes load(CompoundTag tag, HolderLookup.Provider registries) {
        RaceTunes t = new RaceTunes();
        MAP.parse(NbtOps.INSTANCE, tag.get("tunes")).ifSuccess(t.tunes::putAll);
        return t;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        MAP.encodeStart(NbtOps.INSTANCE, tunes).ifSuccess(n -> tag.put("tunes", n));
        return tag;
    }
}
