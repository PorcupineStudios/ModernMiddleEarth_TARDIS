package com.porcupinestudios.tardisrb.progression;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A research tree node ({@code tardis/research}): needs every {@code parents} node first, costs {@code cost}, takes
 * {@code time} ticks. What it unlocks is written on the other side, as {@code tardis_rb:research} requirements.
 */
public record Research(List<ResourceLocation> parents, Either<ResourceLocation, Cost> cost, int time,
        Optional<Condition> requirements) {
    public static final Codec<Research> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("parents", List.of()).forGetter(Research::parents),
            Cost.REF_CODEC.optionalFieldOf("cost", Cost.NONE).forGetter(Research::cost),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("time", 0).forGetter(Research::time),
            Conditions.CODEC.optionalFieldOf("requirements").forGetter(Research::requirements))
            .apply(i, Research::new));
}
