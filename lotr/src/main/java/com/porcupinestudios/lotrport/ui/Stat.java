package com.porcupinestudios.lotrport.ui;

import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * One line of a stats page: a label and a value, maybe a bar (0-1) and maybe arrows to change it.
 *
 * @param bar fill of a bar under the value, 0-1; negative for none
 */
public record Stat(Component label, String value, Tone tone, double bar, Optional<Stepper> stepper) {
    public static Stat of(Component label, String value) {
        return new Stat(label, value, Tone.NORMAL, -1, Optional.empty());
    }

    public static Stat of(Component label, String value, Tone tone) {
        return new Stat(label, value, tone, -1, Optional.empty());
    }

    public Stat withBar(double fill) {
        return new Stat(label, value, tone, Math.max(0, Math.min(1, fill)), stepper);
    }

    public Stat withStepper(Stepper s) {
        return new Stat(label, value, tone, bar, Optional.of(s));
    }

    /** Arrows that send {@code action} with the new value; the server clamps and checks again. */
    public record Stepper(String action, int value, int min, int max, int step) {}
}
