package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.progression.SkyLink;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;

/**
 * See-through planet edges, built on Immersive Portals' portals but made for planet-sized worlds (Aaron, 2026-11-04):
 * Immersive Portals' own world wrapping puts one portal along each whole edge, all the time. A planet's edge is
 * hundreds of thousands of blocks long, so here only the bit of edge near a player gets portals: one-way pieces
 * {@code seam_segment} blocks long, the full height of the world, for every player within {@code seam_view} of an
 * edge, taken down {@code seam_linger} ticks after nobody is near. Each side of a seam has its own pieces (one-way, so
 * pieces never overlap), so a player at a seam costs a handful of portals and nobody elsewhere costs any. Portals are
 * never saved: one loading from disk is dropped and made again when needed. Crossing a pole turns the view round
 * ({@link Planet#poleX}).
 */
public final class SeamPortals {
    public static final String TAG = "tardis_rb:seam";
    private static final int EAST = 0;
    private static final int WEST = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;

    private record Key(ResourceLocation planet, int edge, int segment) {}

    private static final class Live {
        final Portal portal;
        long wanted;

        Live(Portal portal, long wanted) {
            this.portal = portal;
            this.wanted = wanted;
        }
    }

    private static final Map<Key, Live> LIVE = new HashMap<>();

    private SeamPortals() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(SeamPortals::onTick);
        NeoForge.EVENT_BUS.addListener(SeamPortals::onJoin);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> LIVE.clear());
    }

    /** How many seam portals stand now (for tests and the debug command). */
    public static int count() {
        return LIVE.size();
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        update(server);
    }

    /** A spot that needs the edge near it see-through as if a player stood there (the world check uses it). */
    public record Probe(ResourceLocation planet, double x, double z) {}

    public static void update(MinecraftServer server) {
        update(server, java.util.List.of());
    }

    /** Puts up the pieces players (and probes) need now and takes down the ones nobody has needed for a while. */
    public static void update(MinecraftServer server, java.util.List<Probe> probes) {
        Tuning.SpaceTuning t = TardisContent.tuning().space();
        long now = server.getTickCount();
        Set<Key> wanted = new HashSet<>();
        Map<ResourceLocation, Planet> byId = new HashMap<>();
        if (t.seamView() > 0) {
            for (Planet p : Planets.all(server)) {
                ServerLevel world = server.getLevel(p.link().dimension());
                if (world == null || !p.link().seamPortals() || p.link().wrap() == SkyLink.Wrap.NONE) {
                    continue;
                }
                byId.put(p.id(), p);
                for (ServerPlayer player : world.players()) {
                    wanted(p, player.getX(), player.getZ(), t, wanted);
                }
                for (Probe probe : probes) {
                    if (probe.planet().equals(p.id())) {
                        wanted(p, probe.x(), probe.z(), t, wanted);
                    }
                }
            }
        }
        for (Key k : wanted) {
            Live live = LIVE.get(k);
            Planet p = byId.get(k.planet());
            ServerLevel world = server.getLevel(p.link().dimension());
            if (live != null && !live.portal.isRemoved()) {
                live.wanted = now;
                continue;
            }
            Portal portal = make(world, p, k, t);
            if (portal != null) {
                LIVE.put(k, new Live(portal, now));
            }
        }
        for (Iterator<Map.Entry<Key, Live>> it = LIVE.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Key, Live> e = it.next();
            if (wanted.contains(e.getKey()) || now - e.getValue().wanted < t.seamLinger()) {
                continue;
            }
            if (!e.getValue().portal.isRemoved()) {
                e.getValue().portal.discard();
            }
            it.remove();
        }
    }

    /** How many edge pieces a player at (x, z) needs (for tests). */
    public static int needed(Planet p, double x, double z, Tuning.SpaceTuning t) {
        Set<Key> out = new HashSet<>();
        wanted(p, x, z, t, out);
        return out.size();
    }

    /** The seam portals standing in a level (for tests). */
    public static java.util.List<Portal> portals(ServerLevel level) {
        java.util.List<Portal> out = new java.util.ArrayList<>();
        for (Live live : LIVE.values()) {
            if (live.portal.level() == level && !live.portal.isRemoved()) {
                out.add(live.portal);
            }
        }
        return out;
    }

    /** The pieces a player at (x, z) needs. */
    static void wanted(Planet p, double x, double z, Tuning.SpaceTuning t, Set<Key> out) {
        SkyLink.Bounds b = p.link().surface();
        int view = t.seamView();
        int s = t.seamSegment();
        if (b.maxX() - x < view) {
            segments(p, EAST, z - b.minZ(), b.maxZ() - b.minZ(), view, s, out);
        }
        if (x - b.minX() < view) {
            segments(p, WEST, z - b.minZ(), b.maxZ() - b.minZ(), view, s, out);
        }
        if (z - b.minZ() < view) {
            segments(p, NORTH, x - b.minX(), b.maxX() - b.minX(), view, s, out);
        }
        if (b.maxZ() - z < view) {
            segments(p, SOUTH, x - b.minX(), b.maxX() - b.minX(), view, s, out);
        }
    }

    private static void segments(Planet p, int edge, double along, int length, int view, int s, Set<Key> out) {
        int last = (length - 1) / s;
        int from = Math.max(0, (int) Math.floor((along - view) / s));
        int to = Math.min(last, (int) Math.floor((along + view) / s));
        for (int k = from; k <= to; k++) {
            out.add(new Key(p.id(), edge, k));
        }
    }

    /** One piece of edge: a one-way portal looking (and leading) round the globe. */
    private static Portal make(ServerLevel world, Planet p, Key k, Tuning.SpaceTuning t) {
        SkyLink.Bounds b = p.link().surface();
        int s = t.seamSegment();
        boolean alongZ = k.edge() == EAST || k.edge() == WEST;
        int start = (alongZ ? b.minZ() : b.minX()) + k.segment() * s;
        int end = Math.min(start + s, alongZ ? b.maxZ() : b.maxX());
        if (end <= start) {
            return null;
        }
        double mid = (start + end) / 2.0;
        double width = end - start;
        double height = world.getHeight();
        double y = world.getMinBuildHeight() + height / 2;
        Vec3 at;
        Vec3 dest;
        Direction facing;
        DQuaternion turn = DQuaternion.identity;
        boolean globe = p.link().wrap() == SkyLink.Wrap.GLOBE;
        switch (k.edge()) {
            case EAST -> {
                at = new Vec3(b.maxX(), y, mid);
                dest = new Vec3(b.minX(), y, mid);
                facing = Direction.WEST;
            }
            case WEST -> {
                at = new Vec3(b.minX(), y, mid);
                dest = new Vec3(b.maxX(), y, mid);
                facing = Direction.EAST;
            }
            case NORTH -> {
                at = new Vec3(mid, y, b.minZ());
                dest = globe ? new Vec3(p.poleX(mid), y, b.minZ()) : new Vec3(mid, y, b.maxZ());
                facing = Direction.SOUTH;
            }
            default -> {
                at = new Vec3(mid, y, b.maxZ());
                dest = globe ? new Vec3(p.poleX(mid), y, b.maxZ()) : new Vec3(mid, y, b.minZ());
                facing = Direction.NORTH;
            }
        }
        if (globe && !alongZ) {
            turn = DQuaternion.rotationByDegrees(new Vec3(0, 1, 0), 180);
        }
        Portal portal = Portal.ENTITY_TYPE.create(world);
        if (portal == null) {
            return null;
        }
        portal.setOriginPos(at);
        portal.setOrientationAndSize(Doors.axisW(facing), new Vec3(0, 1, 0), width, height);
        PortalAPI.setPortalTransformation(portal, world.dimension(), dest, turn, 1.0);
        portal.portalTag = TAG;
        PortalAPI.spawnServerEntity(portal);
        return portal;
    }

    /** Seam portals are never kept on disk; they come back when a player is near. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity() instanceof Portal p
                && TAG.equals(p.portalTag)) {
            event.setCanceled(true);
        }
    }
}
