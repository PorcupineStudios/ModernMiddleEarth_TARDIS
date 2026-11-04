package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.List;
import java.util.Optional;

/**
 * One control on the console ({@code tardis/control}): how it moves ({@code kind}), what it does ({@code action}, see
 * {@code console/ConsoleControls}; {@code none} for decoration), which part of the console model it is ({@code part},
 * a control in {@code assets/tardis_rb/console/capaldi.json}, which also holds its click box), its {@code panel} 0-5
 * (0 faces the door), and the {@code requirements} that make it appear. A control is only on the console once they
 * hold (Aaron: controls appear as they are repaired).
 */
public record Control(String kind, String action, String part, int panel, Optional<Condition> requirements) {
    public static final List<String> KINDS = List.of("button", "buttons", "lever", "dial", "monitor", "screen",
            "slider", "switch", "wheel", "keyboard", "phone");
    public static final Codec<Control> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(Control::kind),
            Codec.STRING.fieldOf("action").forGetter(Control::action),
            Codec.STRING.optionalFieldOf("part", "").forGetter(Control::part),
            Codec.intRange(0, 5).optionalFieldOf("panel", 0).forGetter(Control::panel),
            Conditions.CODEC.optionalFieldOf("requirements").forGetter(Control::requirements))
            .apply(i, Control::new));
}
