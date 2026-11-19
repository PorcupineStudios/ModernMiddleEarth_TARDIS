package com.porcupinestudios.ringbearer.api.history;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/**
 * Posted on the game bus when the world itself is rolled back to {@code toTime} (a game tick): every system that keeps
 * its state as deltas in the {@link HistoryLog} puts its state back as it was at that tick (the LOTR borders replay
 * their layer deltas). Whoever rewinds decides what happens to the log after {@code toTime}; listeners must not depend
 * on it being kept or cut.
 */
public final class HistoryRewindEvent extends Event {
    private final MinecraftServer server;
    private final long toTime;

    public HistoryRewindEvent(MinecraftServer server, long toTime) {
        this.server = server;
        this.toTime = toTime;
    }

    public MinecraftServer server() {
        return server;
    }

    public long toTime() {
        return toTime;
    }
}
