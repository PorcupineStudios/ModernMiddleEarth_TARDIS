package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.progression.TardisContent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Players flying fast through space load no chunks (Aaron 2026-11-04: extreme speeds were laggy). Space levels are
 * empty void, yet at 40 blocks a tick Minecraft would still make, light, save and send dozens of empty chunks a tick,
 * and the client froze whenever the player outran them. Faster than {@code space.motion.chunk_skip_speed} blocks a
 * tick a player is left out of chunk loading, vanilla's ({@code SpaceChunkMapMixin}) and Immersive Portals'
 * ({@code compat/immportals}), the way a spectator who may not generate chunks is; the client fills the void around
 * itself ({@code client/SpaceVoidChunks}). Slower than half that speed, loading starts again.
 */
public final class SpaceChunks {
    private static final Map<UUID, Vec3> LAST = new HashMap<>();
    private static final Set<UUID> SKIPPING = new HashSet<>();

    private SpaceChunks() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(SpaceChunks::onTick);
        NeoForge.EVENT_BUS.addListener(SpaceChunks::onLogout);
    }

    /** Whether chunk loading leaves this player out (server thread). */
    public static boolean skips(ServerPlayer player) {
        return SKIPPING.contains(player.getUUID());
    }

    private static void onTick(ServerTickEvent.Post event) {
        double skipSpeed = TardisContent.tuning().space().motion().chunkSkipSpeed();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            update(player, skipSpeed);
        }
    }

    /**
     * One tick of a player's movement (the distance since the last tick: players move on the client). Returns whether
     * the player now skips chunk loading.
     */
    public static boolean update(ServerPlayer player, double skipSpeed) {
        UUID id = player.getUUID();
        Vec3 now = player.position();
        Vec3 last = LAST.put(id, now);
        double speed = last == null ? 0 : now.distanceTo(last);
        boolean was = SKIPPING.contains(id);
        boolean skip = decide(was, SpaceTravel.isSpace(player.level()), speed, skipSpeed);
        if (skip != was) {
            if (skip) {
                SKIPPING.add(id);
            } else {
                SKIPPING.remove(id);
            }
            // vanilla re-checks skipPlayer when the player moves; Immersive Portals re-reads its loaders each update
            player.serverLevel().getChunkSource().move(player);
        }
        return skip;
    }

    /** Skip in space above {@code skipSpeed}, and keep skipping until below half of it (no flicker at the edge). */
    public static boolean decide(boolean was, boolean space, double speed, double skipSpeed) {
        return skipSpeed > 0 && space && (was ? speed >= skipSpeed / 2 : speed > skipSpeed);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
        SKIPPING.remove(event.getEntity().getUUID());
    }
}
