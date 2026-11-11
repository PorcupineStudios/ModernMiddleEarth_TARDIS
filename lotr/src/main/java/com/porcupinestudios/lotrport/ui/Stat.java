package com.porcupinestudios.lotrport.ui;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * One line of a stats page: a label and a value, maybe a bar (0-1), maybe arrows to change it, maybe a drop-down to
 * pick another value.
 *
 * @param bar fill of a bar under the value, 0-1; negative for none
 */
public record Stat(Component label, String value, Tone tone, double bar, Optional<Stepper> stepper,
        Optional<Picker> picker) {
    public Stat(Component label, String value, Tone tone, double bar, Optional<Stepper> stepper) {
        this(label, value, tone, bar, stepper, Optional.empty());
    }

    public static Stat of(Component label, String value) {
        return new Stat(label, value, Tone.NORMAL, -1, Optional.empty());
    }

    public static Stat of(Component label, String value, Tone tone) {
        return new Stat(label, value, tone, -1, Optional.empty());
    }

    public Stat withBar(double fill) {
        return new Stat(label, value, tone, Math.max(0, Math.min(1, fill)), stepper, picker);
    }

    public Stat withStepper(Stepper s) {
        return new Stat(label, value, tone, bar, Optional.of(s), picker);
    }

    public Stat withPicker(Picker p) {
        return new Stat(label, value, tone, bar, stepper, Optional.of(p));
    }

    /** Arrows that send {@code action} with the new value; the server clamps and checks again. */
    public record Stepper(String action, int value, int min, int max, int step) {}

    /**
     * A drop-down of choices: picking one sends {@code action} with the choice's id as the row. The server checks it
     * again (the choice may be gone by then).
     *
     * @param current the id of the choice in force (ticked in the list)
     */
    public record Picker(String action, String current, List<Choice> choices) {
        public Picker {
            choices = List.copyOf(choices);
        }
    }

    /** One choice of a {@link Picker}; a disabled one is greyed with {@code why} as its tooltip. */
    public record Choice(String id, Component label, boolean enabled, Optional<Component> why) {
        public static Choice of(String id, Component label) {
            return new Choice(id, label, true, Optional.empty());
        }
    }
}
