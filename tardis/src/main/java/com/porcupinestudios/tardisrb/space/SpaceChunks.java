package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Which space chunks are loaded.
 * <p>
 * Players flying fast through space load no chunks (Aaron 2026-11-04: extreme speeds were laggy): faster than
 * {@code space.motion.chunk_skip_speed} blocks a tick they are left out of chunk loading, vanilla's
 * ({@code SpaceChunkMapMixin}) and Immersive Portals' ({@code compat/immportals}); the client fills the void round
 * itself ({@code client/SpaceVoidChunks}). Slower than half that speed they load again.
 * <p>
 * Sparse space (Aaron 2026-11-15: "99.99% of chunks are empty, only store where [things] are and only load in those
 * chunks", {@code space.chunks}): with {@code sparse} on, every player in space is left out of vanilla's loading, and
 * this loads only (1) the chunks within {@code near_radius} of each player who isn't flying fast, ticking, to build,
 * mine and fight in, and (2) the chunks {@link SpaceContent} says hold something within the server's view distance of
 * a player, ticking within its simulation distance. Everything else stays unloaded and unsaved: the client draws it
 * as sky. Tickets are worked out every {@code space.check_ticks}.
 */
public final class SpaceChunks {
    private static final Map<UUID, Vec3> LAST = new HashMap<>();
    private static final Set<UUID> SKIPPING = new HashSet<>();
    /** Near a player: ticket radius = near_radius + 2 (entity ticking to near_radius). */
    public static final TicketType<ChunkPos> NEAR = TicketType.create("tardis_rb_space_near",
            Comparator.comparingLong(ChunkPos::toLong));
    /** A chunk with things in it: radius 2 to tick, 0 just to load and send. */
    public static final TicketType<ChunkPos> CONTENT = TicketType.create("tardis_rb_space_content",
            Comparator.comparingLong(ChunkPos::toLong));
    /** Tickets held per space level: chunk -> (type << 8 | radius). */
    private static final Map<ResourceKey<Level>, Long2IntMap> HELD = new HashMap<>();
    private static int tick;

    private SpaceChunks() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(SpaceChunks::onTick);
        NeoForge.EVENT_BUS.addListener(SpaceChunks::onLogout);
        NeoForge.EVENT_BUS.addListener(SpaceChunks::onLevelSave);
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel l) {
                HELD.remove(l.dimension());
            }
        });
    }

    /** Whether sparse space is on for this level. */
    public static boolean sparse(Level level) {
        return level instanceof ServerLevel && TardisContent.tuning().space().chunks().sparse()
                && SpaceTravel.isSpace(level);
    }

    /** Whether vanilla chunk loading leaves this player out (server thread): fast, or anywhere in sparse space. */
    public static boolean skips(ServerPlayer player) {
        return SKIPPING.contains(player.getUUID()) || sparse(player.level());
    }

    private static void onTick(ServerTickEvent.Post event) {
        Tuning.SpaceTuning space = TardisContent.tuning().space();
        double skipSpeed = space.motion().chunkSkipSpeed();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            update(player, skipSpeed);
        }
        if (++tick % space.checkTicks() == 0) {
            loadSparse(event.getServer(), space.chunks());
        }
    }

    /**
     * One tick of a player's movement (the distance since the last tick: players move on the client). Returns whether
     * the player now flies fast enough to skip chunk loading.
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

    /** Whether a player flies too fast to load chunks round them (sparse space loads none near them then). */
    public static boolean fast(ServerPlayer player) {
        return SKIPPING.contains(player.getUUID());
    }

    /** Skip in space above {@code skipSpeed}, and keep skipping until below half of it (no flicker at the edge). */
    public static boolean decide(boolean was, boolean space, double speed, double skipSpeed) {
        return skipSpeed > 0 && space && (was ? speed >= skipSpeed / 2 : speed > skipSpeed);
    }

    /** Puts each sparse space level's tickets where its players need them now, and drops the rest. */
    static void loadSparse(MinecraftServer server, Tuning.Chunks tuning) {
        int view = server.getPlayerList().getViewDistance();
        int simulation = server.getPlayerList().getSimulationDistance();
        for (ServerLevel level : server.getAllLevels()) {
            Long2IntMap held = HELD.computeIfAbsent(level.dimension(), k -> new Long2IntOpenHashMap());
            Long2IntMap want = new Long2IntOpenHashMap();
            if (sparse(level)) {
                SpaceContent content = SpaceContent.get(level);
                content.updateStations(level, tuning.stationGap());
                for (ServerPlayer player : level.players()) {
                    ChunkPos at = player.chunkPosition();
                    if (!fast(player) && !player.isSpectator()) {
                        want.merge(at.toLong(), 1 << 8 | (tuning.nearRadius() + 2), SpaceChunks::stronger);
                    }
                    for (long c : content.near(at, view)) {
                        boolean ticking = Math.max(Math.abs(ChunkPos.getX(c) - at.x),
                                Math.abs(ChunkPos.getZ(c) - at.z)) <= simulation;
                        want.merge(c, 2 << 8 | (ticking ? 2 : 0), SpaceChunks::stronger);
                    }
                }
            }
            for (var it = held.long2IntEntrySet().iterator(); it.hasNext();) {
                var e = it.next();
                if (want.get(e.getLongKey()) != e.getIntValue() || !want.containsKey(e.getLongKey())) {
                    ticket(level, e.getLongKey(), e.getIntValue(), false);
                    it.remove();
                }
            }
            for (var e : want.long2IntEntrySet()) {
                if (!held.containsKey(e.getLongKey())) {
                    ticket(level, e.getLongKey(), e.getIntValue(), true);
                    held.put(e.getLongKey(), e.getIntValue());
                }
            }
        }
    }

    /**
     * The chunks a player in space needs loaded and sent (for Immersive Portals' loaders): none while flying fast;
     * in sparse space the chunks within near_radius + 2, and each chunk holding something within view distance.
     */
    public static void loaders(ServerPlayer player, java.util.function.BiConsumer<ChunkPos, Integer> loader) {
        if (fast(player) || !sparse(player.level())) {
            return;
        }
        ChunkPos at = player.chunkPosition();
        if (!player.isSpectator()) {
            loader.accept(at, TardisContent.tuning().space().chunks().nearRadius() + 2);
        }
        int view = player.server.getPlayerList().getViewDistance();
        for (long c : SpaceContent.get(player.serverLevel()).near(at, view)) {
            loader.accept(new ChunkPos(c), 0);
        }
    }

    /** Of two tickets for one chunk: a player's (near) one, else the larger radius. */
    public static int stronger(int a, int b) {
        if (a >> 8 != b >> 8) {
            return a >> 8 == 1 ? a : b;
        }
        return (a & 0xFF) >= (b & 0xFF) ? a : b;
    }

    private static void ticket(ServerLevel level, long chunk, int kind, boolean add) {
        ChunkPos pos = new ChunkPos(chunk);
        TicketType<ChunkPos> type = kind >> 8 == 1 ? NEAR : CONTENT;
        int radius = kind & 0xFF;
        if (add) {
            level.getChunkSource().addRegionTicket(type, pos, radius, pos);
        } else {
            level.getChunkSource().removeRegionTicket(type, pos, radius, pos);
        }
    }

    /** Loaded chunks this keeps in a level (for the world check). */
    public static int held(ServerLevel level) {
        Long2IntMap held = HELD.get(level.dimension());
        return held == null ? 0 : held.size();
    }

    /**
     * Minecraft saves a level's data before its chunks, and the chunks' saves change the index: save it again after
     * them, or the last changes before a shutdown would be lost.
     */
    private static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level && sparse(level)) {
            SpaceContent content = SpaceContent.get(level);
            if (content.isDirty()) {
                level.getDataStorage().save();
            }
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
        SKIPPING.remove(event.getEntity().getUUID());
    }
}
