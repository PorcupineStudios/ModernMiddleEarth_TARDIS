package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.List;

/**
 * How the police box looks ({@code tardis/look/exterior.json}): the highest {@code look} (0-3, a model and texture
 * set Aaron paints) whose requirements hold; 0 when none do. Requirements are ordinary conditions, so a look can follow
 * the stage, a subsystem's level, a milestone or research.
 */
public record Look(List<Rule> rules) {
    public static final Look NONE = new Look(List.of());
    public static final int MAX = 3;

    public record Rule(int look, Condition requirements) {
        static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, MAX).fieldOf("look").forGetter(Rule::look),
                Conditions.CODEC.fieldOf("requirements").forGetter(Rule::requirements))
                .apply(i, Rule::new));
    }

    public static final Codec<Look> CODEC = RecordCodecBuilder.create(i -> i.group(
            Rule.CODEC.listOf().fieldOf("rules").forGetter(Look::rules))
            .apply(i, Look::new));
}
