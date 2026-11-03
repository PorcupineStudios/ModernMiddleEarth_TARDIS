package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A dimension the TARDIS can jump to ({@code tardis/destination}): which {@code dimension}, the {@code requirements}
 * that open it, and the {@code charge} a jump costs. A dimension with no file is never a destination; the world's
 * start dimension is always open, so the TARDIS can get home.
 */
public record Destination(ResourceKey<Level> dimension, Optional<Condition> requirements, long charge) {
    public static final Codec<Destination> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Destination::dimension),
            Conditions.CODEC.optionalFieldOf("requirements").forGetter(Destination::requirements),
            Codec.LONG.optionalFieldOf("charge", 0L).forGetter(Destination::charge))
            .apply(i, Destination::new));
}
