package com.porcupinestudios.lotrport.colony;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Who may manage a colony. Every feature asks here (never compares founders itself), so the rules can grow into
 * roles and permissions without touching callers.
 */
public final class ColonyAccess {
    private ColonyAccess() {}

    /** Whether the player may give orders in the colony (command its soldiers, station units there). */
    public static boolean canManage(MinecraftServer server, UUID player, Colony colony) {
        return colony.founder().equals(player);
    }

    /** Colonies the player may manage. */
    public static List<Colony> managedBy(MinecraftServer server, UUID player) {
        return Colonies.get(server).all().stream().filter(c -> canManage(server, player, c)).toList();
    }
}
