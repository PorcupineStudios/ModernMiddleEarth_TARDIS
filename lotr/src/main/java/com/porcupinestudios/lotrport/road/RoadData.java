package com.porcupinestudios.lotrport.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Road tiers ({@code road_tier/}) and the road works settings ({@code road_works/settings.json}); both reload. */
public final class RoadData {
    /** {@code columns_per_tick}: how many ground columns road works lay per server tick (spread over ticks). */
    public record Settings(int columnsPerTick) {
        public static final Settings DEFAULT = new Settings(512);
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 65536).optionalFieldOf("columns_per_tick", DEFAULT.columnsPerTick)
                        .forGetter(Settings::columnsPerTick))
                .apply(i, Settings::new));
    }

    private static final CodecJsonLoader<RoadTier> TIERS = new CodecJsonLoader<>("road_tier", RoadTier.CODEC);
    private static final CodecJsonLoader<Settings> SETTINGS = new CodecJsonLoader<>("road_works", Settings.CODEC);

    private RoadData() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(TIERS);
            e.addListener(SETTINGS);
        });
    }

    public static Map<ResourceLocation, RoadTier> tiers() {
        return TIERS.entries();
    }

    public static Optional<RoadTier> tier(ResourceLocation id) {
        return Optional.ofNullable(TIERS.entries().get(id));
    }

    /** Tier ids, lowest rank first. */
    public static List<ResourceLocation> ladder() {
        List<ResourceLocation> out = new ArrayList<>(TIERS.entries().keySet());
        out.sort(Comparator.comparingInt((ResourceLocation id) -> TIERS.entries().get(id).rank())
                .thenComparing(ResourceLocation::toString));
        return out;
    }

    /** The tier one rank above {@code id}, if any. */
    public static Optional<ResourceLocation> next(ResourceLocation id) {
        List<ResourceLocation> l = ladder();
        int i = l.indexOf(id);
        return i >= 0 && i + 1 < l.size() ? Optional.of(l.get(i + 1)) : Optional.empty();
    }

    public static Settings settings() {
        return SETTINGS.entries().getOrDefault(LotrPort.id("settings"), Settings.DEFAULT);
    }
}
