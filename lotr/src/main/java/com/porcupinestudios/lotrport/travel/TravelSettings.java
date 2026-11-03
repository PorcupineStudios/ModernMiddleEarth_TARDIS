package com.porcupinestudios.lotrport.travel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Fast travel tuning: {@code data/<namespace>/fast_travel/settings.json}. The wait after a trip grows with the
 * distance travelled, from {@code cooldownMin} to {@code cooldownMax} seconds at {@code fullDistance} blocks or more.
 *
 * @param hostileRange travel is refused while a mob within this many blocks is attacking the player
 */
public record TravelSettings(int cooldownMin, int cooldownMax, int fullDistance, double hostileRange) {
    public static final TravelSettings DEFAULT = new TravelSettings(180, 1800, 50000, 16);

    public static final Codec<TravelSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("cooldown_min", DEFAULT.cooldownMin()).forGetter(TravelSettings::cooldownMin),
            Codec.INT.optionalFieldOf("cooldown_max", DEFAULT.cooldownMax()).forGetter(TravelSettings::cooldownMax),
            Codec.INT.optionalFieldOf("full_distance", DEFAULT.fullDistance()).forGetter(TravelSettings::fullDistance),
            Codec.DOUBLE.optionalFieldOf("hostile_range", DEFAULT.hostileRange())
                    .forGetter(TravelSettings::hostileRange))
            .apply(i, TravelSettings::new));

    /** Cooldown in ticks after travelling {@code distance} blocks. */
    public long cooldownTicks(double distance) {
        double f = Math.min(1.0, distance / Math.max(1, fullDistance));
        return Math.round((cooldownMin + (cooldownMax - cooldownMin) * f) * 20);
    }
}
