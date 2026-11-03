package com.porcupinestudios.tardisrb.progression;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One repair ({@code tardis/repair}): raises {@code subsystem} to {@code level} (from the level below) for
 * {@code cost}, when {@code requirements} hold. {@code part} names the broken part block for physical repairs.
 */
public record Repair(ResourceLocation subsystem, int level, Either<ResourceLocation, Cost> cost,
        Optional<Condition> requirements, Optional<ResourceLocation> part) {
    public static final Codec<Repair> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("subsystem").forGetter(Repair::subsystem),
            Codec.intRange(1, 1000).fieldOf("level").forGetter(Repair::level),
            Cost.REF_CODEC.optionalFieldOf("cost", Cost.NONE).forGetter(Repair::cost),
            Conditions.CODEC.optionalFieldOf("requirements").forGetter(Repair::requirements),
            ResourceLocation.CODEC.optionalFieldOf("part").forGetter(Repair::part))
            .apply(i, Repair::new));
}
