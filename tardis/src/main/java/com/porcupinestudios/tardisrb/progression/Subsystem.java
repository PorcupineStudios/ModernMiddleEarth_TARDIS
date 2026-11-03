package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A part of the TARDIS that is repaired level by level ({@code tardis/subsystem}). {@code levels[n]} lists what level
 * n gives, as named numbers such as {@code range}, {@code charge_cap} or {@code room_cap}; level 0 is the wreck.
 * {@code start_cap} is the highest level reachable before any milestone raises it (default: every level).
 */
public record Subsystem(List<Map<String, Double>> levels, Optional<Integer> startCap) {
    public static final Codec<Subsystem> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).listOf().fieldOf("levels").forGetter(Subsystem::levels),
            Codec.INT.optionalFieldOf("start_cap").forGetter(Subsystem::startCap))
            .apply(i, Subsystem::new));

    public int maxLevel() {
        return Math.max(0, levels.size() - 1);
    }

    public int baseCap() {
        return Math.min(maxLevel(), startCap.orElse(maxLevel()));
    }

    /** The named number at a level, 0 when that level doesn't set it. */
    public double value(int level, String key) {
        if (levels.isEmpty()) {
            return 0;
        }
        return levels.get(Math.max(0, Math.min(level, maxLevel()))).getOrDefault(key, 0.0);
    }
}
