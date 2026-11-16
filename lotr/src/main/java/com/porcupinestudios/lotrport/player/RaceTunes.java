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

    /** A preset's size and the reach added for blocks and for hitting. */
    public record Tune(double scale, double blockReach, double entityReach) {
        public static final Codec<Tune> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("scale").forGetter(Tune::scale),
                Codec.DOUBLE.fieldOf("block_reach").forGetter(Tune::blockReach),
                Codec.DOUBLE.fieldOf("entity_reach").forGetter(Tune::entityReach)).apply(i, Tune::new));
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
