package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A big repair milestone ({@code tardis/milestone}), such as dimension travel. It is passed when its requirements hold
 * and raises the caps on how far subsystems can be repaired ({@code caps}: subsystem id to highest level). Without
 * requirements it is passed only by a command or another mod.
 */
public record Milestone(Optional<Condition> requirements, Map<ResourceLocation, Integer> caps) {
    public static final Codec<Milestone> CODEC = RecordCodecBuilder.create(i -> i.group(
            Conditions.CODEC.optionalFieldOf("requirements").forGetter(Milestone::requirements),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("caps", Map.of())
                    .forGetter(Milestone::caps))
            .apply(i, Milestone::new));
}
