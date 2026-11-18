package com.porcupinestudios.ringbearer.api.provider;

import java.util.OptionalLong;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which moment a player is in: time travel answers with the game tick of the past (or future) the player is visiting,
 * and nothing while they are in the present. Systems that show the world's state (the LOTR map's borders, colonies)
 * show it as it was at that tick, rebuilt from the {@link com.porcupinestudios.ringbearer.api.history.HistoryLog}.
 */
@FunctionalInterface
public interface TimeProvider {
    OptionalLong timeOf(ServerPlayer player);
}
