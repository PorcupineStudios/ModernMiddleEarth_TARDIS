package com.porcupinestudios.ringbearer.api.provider;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * How many researchers a player has working for them, such as scholars in the player's colony. The TARDIS counts them
 * for its crew and runs research faster with more of them.
 */
@FunctionalInterface
public interface ResearcherProvider {
    int researchers(MinecraftServer server, UUID player);
}
