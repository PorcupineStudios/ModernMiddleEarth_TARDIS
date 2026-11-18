package com.porcupinestudios.ringbearer.api.history;

import java.util.OptionalLong;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Posted on the game bus by time travel when a player arrives in another moment ({@code time}, a game tick) or comes
 * back to the present (empty). Systems that show the world's state to that player send it again for the new moment
 * (the LOTR map sends its borders as they were then).
 */
public final class TimeShiftEvent extends Event {
    private final ServerPlayer player;
    private final OptionalLong time;

    public TimeShiftEvent(ServerPlayer player, OptionalLong time) {
        this.player = player;
        this.time = time;
    }

    public ServerPlayer player() {
        return player;
    }

    /** The tick the player is in now; empty = the present. */
    public OptionalLong time() {
        return time;
    }
}
