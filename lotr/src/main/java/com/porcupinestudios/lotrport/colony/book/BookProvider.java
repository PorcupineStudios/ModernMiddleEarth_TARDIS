package com.porcupinestudios.lotrport.colony.book;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Builds one kind of book and answers its clicks. Every permission check happens here, on the server. */
public interface BookProvider {
    /** The book for this player, context and tab; empty when they may not see it. */
    Optional<Book> build(ServerPlayer player, String context, String tab);

    /**
     * A click: {@code action} with its {@code arg} and number ({@code num}: a stepper's new value). Returns what to
     * say, and optionally another book to open instead (kind, context, tab).
     */
    Result act(ServerPlayer player, String context, String tab, String action, String arg, int num);

    record Result(String message, Optional<Target> go) {
        public static final Result NONE = new Result("", Optional.empty());

        public static Result say(String m) {
            return new Result(m, Optional.empty());
        }

        public static Result say(net.minecraft.network.chat.Component m) {
            return new Result(m.getString(), Optional.empty());
        }

        public static Result go(String kind, String context, String tab) {
            return new Result("", Optional.of(new Target(kind, context, tab)));
        }
    }

    record Target(String kind, String context, String tab) {}
}
