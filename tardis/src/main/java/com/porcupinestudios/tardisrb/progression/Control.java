package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.List;
import java.util.Optional;

/**
 * One control on the console ({@code tardis/control}): what it looks like ({@code kind}: button, lever, dial,
 * monitor), what it does ({@code action}, see {@code console/ConsoleControls}), where it sits ({@code panel} 0-5 around
 * the console, {@code u} across the panel and {@code v} from its inner to its outer edge, both 0-1), and the
 * {@code requirements} that make it appear. A control is only on the console once they hold (Aaron: controls appear as
 * they are repaired).
 */
public record Control(String kind, String action, int panel, float u, float v, Optional<Condition> requirements) {
    public static final List<String> KINDS = List.of("button", "lever", "dial", "monitor");
    public static final Codec<Control> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(Control::kind),
            Codec.STRING.fieldOf("action").forGetter(Control::action),
            Codec.intRange(0, 5).fieldOf("panel").forGetter(Control::panel),
            Codec.floatRange(0, 1).optionalFieldOf("u", 0.5f).forGetter(Control::u),
            Codec.floatRange(0, 1).optionalFieldOf("v", 0.5f).forGetter(Control::v),
            Conditions.CODEC.optionalFieldOf("requirements").forGetter(Control::requirements))
            .apply(i, Control::new));
}
