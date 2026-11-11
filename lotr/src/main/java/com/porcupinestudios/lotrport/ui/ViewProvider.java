package com.porcupinestudios.lotrport.ui;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds one kind of view and carries out its actions. Register it in {@link Views}. Both methods run on the server
 * thread for a player who may have sent anything: check that they may see and do it every time.
 */
public interface ViewProvider {
    /**
     * The view for this player, or empty if they may not see it (they get {@link #denied}).
     *
     * @param context what it is about (a colony id...), as the client sent it: parse it defensively
     * @param tab the tab asked for; unknown or empty means the first tab
     */
    Optional<View> build(ServerPlayer player, String context, String tab);

    /** What the player is told when {@link #build} gives nothing. */
    default Component denied() {
        return Component.translatableWithFallback("ui.lotr_port.denied", "You can't see that");
    }

    /**
     * Carries out an action. The default does nothing.
     *
     * @param row the selected row's id for a row action or a row click, else empty
     * @param value a stepper's new value, else 0
     */
    default Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
        return Result.NONE;
    }

    /**
     * What an action did: a line for the player, and the view to show next (another view or tab), else the same view
     * is rebuilt and refreshed.
     */
    record Result(Optional<Component> message, Optional<View.Target> next) {
        public static final Result NONE = new Result(Optional.empty(), Optional.empty());

        public static Result say(Component message) {
            return new Result(Optional.of(message), Optional.empty());
        }

        public static Result go(View.Target next) {
            return new Result(Optional.empty(), Optional.of(next));
        }
    }
}
