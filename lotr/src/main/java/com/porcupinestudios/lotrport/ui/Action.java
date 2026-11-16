package com.porcupinestudios.lotrport.ui;

import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * A button: the client sends its id back ({@code UiNet.Act}) and the server decides. A disabled action is shown greyed
 * with {@code why} as its tooltip, so players see what they could do and why they can't.
 *
 * @param confirm ask "are you sure?" first
 */
public record Action(String id, Component label, boolean enabled, Optional<Component> why, boolean confirm) {
    public static Action of(String id, Component label) {
        return new Action(id, label, true, Optional.empty(), false);
    }

    public static Action disabled(String id, Component label, Component why) {
        return new Action(id, label, false, Optional.of(why), false);
    }

    public Action confirmed() {
        return new Action(id, label, enabled, why, true);
    }
}
