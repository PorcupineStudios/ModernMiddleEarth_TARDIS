package com.porcupinestudios.lotrport.colony;

import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Who may manage a colony. Every feature asks here (never compares founders itself), so the rules can grow into
 * roles and permissions without touching callers.
 */
public final class ColonyAccess {
    private ColonyAccess() {}

    /** Whether the player may command the colony's soldiers. */
    public static boolean canManage(MinecraftServer server, UUID player, Colony colony) {
        return Permissions.can(player, colony, Perm.COMMAND);
    }

    /** Whether the player may station their own units in the colony. */
    public static boolean canStation(MinecraftServer server, UUID player, Colony colony) {
        return Permissions.can(player, colony, Perm.STATION);
    }

    /** Colonies the player may manage. */
    public static List<Colony> managedBy(MinecraftServer server, UUID player) {
        return Colonies.get(server).all().stream().filter(c -> canManage(server, player, c)).toList();
    }
}
