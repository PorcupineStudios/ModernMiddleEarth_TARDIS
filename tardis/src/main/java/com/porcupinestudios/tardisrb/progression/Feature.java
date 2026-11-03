package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.Optional;

/**
 * A switchable feature ({@code tardis/feature}), such as short hops or a room type. It exists only if a file defines
 * it; {@code enabled: false} turns it off for every TARDIS, and the server config can force it on or off.
 */
public record Feature(boolean enabled, Optional<Condition> requirements) {
    public static final Codec<Feature> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Feature::enabled),
            Conditions.CODEC.optionalFieldOf("requirements").forGetter(Feature::requirements))
            .apply(i, Feature::new));
}
