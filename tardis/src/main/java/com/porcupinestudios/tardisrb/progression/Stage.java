package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A progression stage ({@code tardis/stage}). A TARDIS starts in the stage with the lowest {@code order}, finishes it
 * when every repair in {@code repairs} is done, and moves on to the next stage once that stage's {@code milestone}
 * (if it has one) is passed.
 */
public record Stage(int order, List<ResourceLocation> repairs, Optional<ResourceLocation> milestone) {
    public static final Codec<Stage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("order").forGetter(Stage::order),
            ResourceLocation.CODEC.listOf().optionalFieldOf("repairs", List.of()).forGetter(Stage::repairs),
            ResourceLocation.CODEC.optionalFieldOf("milestone").forGetter(Stage::milestone))
            .apply(i, Stage::new));
}
