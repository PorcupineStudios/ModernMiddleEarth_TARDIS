package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One control on the console ({@code tardis/control}, written by {@code tools/console_layout.py}). Aaron, 2026-11-08:
 * the console is a plain blank slate and the small control models stand on its six themed sides.
 * <ul>
 * <li>{@code model}: a control model in {@code assets/tardis_rb/console/controls/<model>.json}, or {@code monitor}
 * (the console's own monitor, which turns round the rotor).</li>
 * <li>{@code panel} 0-5 (0 faces the door, each next one 60 degrees on), {@code u} blocks right of the panel's middle,
 * {@code v} blocks up the slope from its outer edge, {@code turn} degrees round the panel's normal, {@code scale}.</li>
 * <li>{@code actions}: what clicking each clickable part of the model does (see {@code console/ConsoleControls};
 * {@code none} only moves it).</li>
 * <li>{@code shows}: for gauges, the value each needle shows ({@code charge}, {@code rooms}).</li>
 * <li>{@code requirements}: the control is only on the console once they hold (controls appear as repaired).</li>
 * </ul>
 */
public record Control(String model, int panel, float u, float v, float turn, float scale,
        Map<String, String> actions, List<String> shows, Optional<Condition> requirements) {
    public static final String MONITOR = "monitor";
    public static final Codec<Control> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("model").forGetter(Control::model),
            Codec.intRange(0, 5).optionalFieldOf("panel", 0).forGetter(Control::panel),
            Codec.FLOAT.optionalFieldOf("u", 0F).forGetter(Control::u),
            Codec.FLOAT.optionalFieldOf("v", 0.3F).forGetter(Control::v),
            Codec.FLOAT.optionalFieldOf("turn", 0F).forGetter(Control::turn),
            Codec.FLOAT.optionalFieldOf("scale", 1F).forGetter(Control::scale),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("actions", Map.of())
                    .forGetter(Control::actions),
            Codec.STRING.listOf().optionalFieldOf("shows", List.of()).forGetter(Control::shows),
            Conditions.CODEC.optionalFieldOf("requirements").forGetter(Control::requirements))
            .apply(i, Control::new));
}
