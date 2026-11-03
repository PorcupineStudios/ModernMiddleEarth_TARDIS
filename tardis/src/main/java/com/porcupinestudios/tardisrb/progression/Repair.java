package com.porcupinestudios.tardisrb.progression;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * One repair ({@code tardis/repair}): raises {@code subsystem} to {@code level} (from the level below) for
 * {@code cost}, when {@code requirements} hold. A physical repair has {@code part_pos}: where its broken part block
 * stands in the first console room (offset from the room's corner); the crew fix it by hand there.
 */
public record Repair(ResourceLocation subsystem, int level, Either<ResourceLocation, Cost> cost,
        Optional<Condition> requirements, Optional<BlockPos> partPos) {
    public static final Codec<Repair> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("subsystem").forGetter(Repair::subsystem),
            Codec.intRange(1, 1000).fieldOf("level").forGetter(Repair::level),
            Cost.REF_CODEC.optionalFieldOf("cost", Cost.NONE).forGetter(Repair::cost),
            Conditions.CODEC.optionalFieldOf("requirements").forGetter(Repair::requirements),
            BlockPos.CODEC.optionalFieldOf("part_pos").forGetter(Repair::partPos))
            .apply(i, Repair::new));
}
