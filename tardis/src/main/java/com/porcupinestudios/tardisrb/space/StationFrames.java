package com.porcupinestudios.tardisrb.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.tardisrb.gravity.gc.api.GravityChangerAPI;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Space stations orbit in real time while their blocks stay put (Aaron 2026-11-15: stations are built automatically
 * from what's built in orbit, orbit Kepler-style, and you see them move).
 * <p>
 * Each station over a planet is a <b>frame</b>: its blocks keep their coordinates in the orbit layer, while where they
 * are over the planet moves east at the orbit speed for the station's height ({@link Orbits#orbitSpeed}: the same
 * speed that keeps a free flyer up, so lower orbits are quicker). The frame's <b>drift</b> is how far (layer blocks,
 * east) the station has gone round since its blocks were placed. Inside a station's <b>zone</b> (its chunks plus
 * {@code space.stations.zone_margin} blocks) every spot on the planet is shifted by the drift: the globe below scrolls
 * past, and going down lands under where the station is now ({@link #surfaceX}). There, things co-orbit with the
 * station, so they float beside it instead of falling away, except within {@code deck_height} blocks over its blocks
 * (deck gravity, {@link #deck}).
 * <p>
 * Leaving a zone puts you where the station really is ({@code blocks + drift}) with its orbital speed added, so you
 * go on orbiting beside it; coming within {@code enter_radius} of where a station really is puts you in its frame
 * (minus drift and orbital speed). The zone is wider than the entry radius, so nobody flips back and forth.
 */
public final class StationFrames {
    /** One station's frame: its blocks' box (block x/z), centre, and orbit (true longitude at epoch, period). */
    public record Frame(int id, ResourceLocation planet, int minX, int minZ, int maxX, int maxZ, int centreX,
            int centreY, int centreZ, double lon, long period, long epoch) {
        public static final Codec<Frame> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Frame::id),
                ResourceLocation.CODEC.fieldOf("planet").forGetter(Frame::planet),
                Codec.INT.fieldOf("min_x").forGetter(Frame::minX), Codec.INT.fieldOf("min_z").forGetter(Frame::minZ),
                Codec.INT.fieldOf("max_x").forGetter(Frame::maxX), Codec.INT.fieldOf("max_z").forGetter(Frame::maxZ),
                Codec.INT.fieldOf("x").forGetter(Frame::centreX), Codec.INT.fieldOf("y").forGetter(Frame::centreY),
                Codec.INT.fieldOf("z").forGetter(Frame::centreZ), Codec.DOUBLE.fieldOf("lon").forGetter(Frame::lon),
                Codec.LONG.fieldOf("period").forGetter(Frame::period),
                Codec.LONG.fieldOf("epoch").forGetter(Frame::epoch))
                .apply(i, Frame::new));

        /** Layer blocks east the station has gone since its blocks were placed, at game time {@code time}. */
        public double drift(Planet p, double time) {
            double lonNow = period <= 0 ? lon : lon + 2 * Math.PI * (time - epoch) / period;
            double placed = p.lon(p.surfaceX(centreX));
            double turn = Math.IEEEremainder(lonNow - placed, 2 * Math.PI); // -pi..pi of a turn
            return turn / (2 * Math.PI) * p.width() / p.link().orbit().scale();
        }

        /** Whether a layer x/z is in the zone: within {@code margin} blocks of the station's chunks. */
        public boolean holds(double x, double z, double margin) {
            return x >= minX - margin && x <= maxX + 1 + margin && z >= minZ - margin && z <= maxZ + 1 + margin;
        }

        /** Distance (x/z) from a layer point to where the station's box really is at {@code time}. */
        public double distanceToTrue(Planet p, long time, double x, double z) {
            double d = drift(p, time);
            double dx = Math.max(0, Math.max(minX + d - x, x - (maxX + 1 + d)));
            double dz = Math.max(0, Math.max(minZ - z, z - (maxZ + 1)));
            return Math.hypot(dx, dz);
        }
    }

    /** The orbit period (ticks per turn) for a station at layer height {@code y}: once round at the orbit speed. */
    public static long period(Planet p, double y, Tuning.SpaceTuning t) {
        double speed = Orbits.orbitSpeed(p, y, t);
        if (speed <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(p.width() / p.link().orbit().scale() / speed));
    }

    /** Frames the client knows, per space dimension (set from the server's {@code SkyNet.Frames}). */
    private static final Map<ResourceKey<Level>, List<Frame>> CLIENT = new ConcurrentHashMap<>();
    /** Persistent tag: the station frame an entity is in (kept over restarts, so a reload doesn't throw it out). */
    private static final String IN_FRAME = "tardis_rb:station_frame";

    private StationFrames() {}

    public static void setClient(ResourceKey<Level> dimension, List<Frame> frames) {
        CLIENT.put(dimension, List.copyOf(frames));
    }

    /** A level's station frames: the server's from its stations, the client's as sent. */
    public static List<Frame> of(Level level) {
        if (level.isClientSide()) {
            return CLIENT.getOrDefault(level.dimension(), List.of());
        }
        if (!(level instanceof ServerLevel server) || !SpaceChunks.sparse(level)) {
            return List.of();
        }
        return frames(server);
    }

    /** The server's frames: its stations over a planet. */
    static List<Frame> frames(ServerLevel level) {
        List<Frame> out = new ArrayList<>();
        for (SpaceContent.Station s : SpaceContent.get(level).stations()) {
            if (s.orbit().planet().isEmpty()) {
                continue;
            }
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (long c : s.chunks()) {
                int cx = net.minecraft.world.level.ChunkPos.getX(c);
                int cz = net.minecraft.world.level.ChunkPos.getZ(c);
                minX = Math.min(minX, cx << 4);
                minZ = Math.min(minZ, cz << 4);
                maxX = Math.max(maxX, (cx << 4) + 15);
                maxZ = Math.max(maxZ, (cz << 4) + 15);
            }
            out.add(new Frame(s.id(), s.orbit().planet().get(), minX, minZ, maxX, maxZ, s.centreX(), s.centreY(),
                    s.centreZ(), s.orbit().lon(), s.orbit().period(), s.orbit().epoch()));
        }
        return out;
    }

    /** The frames each space level's players were last sent (server). */
    private static final Map<ResourceKey<Level>, List<Frame>> SENT = new java.util.HashMap<>();

    /** Sends a level's players its frames when they changed. */
    static void sync(ServerLevel level) {
        List<Frame> now = frames(level);
        if (!now.equals(SENT.get(level.dimension()))) {
            SENT.put(level.dimension(), now);
            for (var player : level.players()) {
                send(player);
            }
        }
    }

    /** Sends a player the frames of the level it's in (on login, changing level, or when they change). */
    public static void send(net.minecraft.server.level.ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.porcupinestudios.tardisrb.net.SkyNet.Frames(player.level().dimension().location(),
                        of(player.level())));
    }

    /** The station whose zone a layer point is in (the nearest centre if zones meet). */
    public static Optional<Frame> zone(Level level, double x, double z) {
        return zone(level, x, z, level.isClientSide() ? clientTuning.get().stations().zoneMargin()
                : TardisContent.tuning().space().stations().zoneMargin());
    }

    /** The client's space tuning (as the server sent it with the planets). */
    public static java.util.function.Supplier<Tuning.SpaceTuning> clientTuning = () -> Tuning.SpaceTuning.DEFAULT;

    static Optional<Frame> zone(Level level, double x, double z, double margin) {
        Frame best = null;
        double bestD = Double.MAX_VALUE;
        for (Frame f : of(level)) {
            if (f.holds(x, z, margin)) {
                double d = Math.hypot(x - f.centreX(), z - f.centreZ());
                if (d < bestD) {
                    best = f;
                    bestD = d;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<Planet> planet(Level level, Frame f) {
        MinecraftServer server = level.getServer();
        if (server != null) {
            return Planets.all(server).stream().filter(p -> p.id().equals(f.planet())).findFirst();
        }
        return clientPlanet.apply(f.planet());
    }

    /** The client's planet lookup (set by the client: it knows the planets from {@code SkyNet.Planets}). */
    public static java.util.function.Function<ResourceLocation, Optional<Planet>> clientPlanet = id -> Optional.empty();

    /** The surface x below a layer point: the planet's own mapping, shifted by the drift of a station zone there. */
    public static double surfaceX(Level level, Planet p, double x, double z) {
        return surfaceX(level, p, x, z, level.getGameTime());
    }

    /** As {@link #surfaceX}, at a time between ticks (the client's globe moves smoothly). */
    public static double surfaceX(Level level, Planet p, double x, double z, double time) {
        return p.surfaceX(x + zone(level, x, z).filter(f -> f.planet().equals(p.id())).map(f -> f.drift(p, time))
                .orElse(0.0));
    }

    /** The surface z below a layer point (stations orbit east, so north/south doesn't drift). */
    public static double surfaceZ(Level level, Planet p, double x, double z) {
        return p.surfaceZ(z);
    }

    /**
     * Deck gravity: in a station's zone the planet pulls only within {@code deck_height} blocks over a solid block
     * (so you walk its decks); elsewhere there you co-orbit and float. Outside zones the pull is unchanged.
     */
    public static boolean pulled(Entity e, Tuning.SpaceTuning t) {
        if (zone(e.level(), e.getX(), e.getZ(), t.stations().zoneMargin()).isEmpty()) {
            return true;
        }
        return deck(e.level(), e.position(), t.stations().deckHeight());
    }

    static boolean deck(Level level, Vec3 at, double height) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= Math.ceil(height); dy++) {
            m.set(at.x, at.y - 0.01 - dy, at.z);
            if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** What an entity's next check does: stay, move out of a zone to the station's true place, or into a frame. */
    public enum Move { STAY, OUT, IN }

    /**
     * The rule, pure: in a zone, stay (in its frame from now on, also if it flew in from outside); just out of the
     * zone it was in, go out; otherwise near enough a station's true place, come in.
     */
    public static Move decide(boolean inZone, boolean wasInZone, boolean nearTrue) {
        if (inZone) {
            return Move.STAY;
        }
        if (wasInZone) {
            return Move.OUT;
        }
        return nearTrue ? Move.IN : Move.STAY;
    }

    /** One check of every entity in a space level's station zones (server, every {@code space.check_ticks}). */
    static void check(ServerLevel level) {
        List<Frame> frames = frames(level);
        if (frames.isEmpty()) {
            return;
        }
        Tuning.SpaceTuning t = TardisContent.tuning().space();
        long time = level.getGameTime();
        List<Entity> all = new ArrayList<>();
        level.getAllEntities().forEach(all::add);
        for (Entity e : all) {
            if (!e.isAlive() || e.isPassenger()) {
                continue;
            }
            Optional<Frame> in = zone(level, e.getX(), e.getZ());
            var data = e.getPersistentData();
            Integer was = data.contains(IN_FRAME) ? data.getInt(IN_FRAME) : null;
            Frame wasFrame = was == null ? null
                    : frames.stream().filter(f -> f.id() == was).findFirst().orElse(null);
            Frame near = null;
            for (Frame f : frames) {
                Planet p = planet(level, f).orElse(null);
                if (p != null && f.distanceToTrue(p, time, e.getX(), e.getZ()) < t.stations().enterRadius()) {
                    near = f;
                    break;
                }
            }
            switch (decide(in.isPresent(), wasFrame != null, near != null)) {
                case STAY -> {
                    if (in.isPresent()) {
                        data.putInt(IN_FRAME, in.get().id());
                    } else if (was != null) {
                        data.remove(IN_FRAME); // its station is gone
                    }
                }
                case OUT -> {
                    data.remove(IN_FRAME);
                    shift(level, e, wasFrame, t, time, 1);
                }
                case IN -> {
                    shift(level, e, near, t, time, -1);
                }
            }
        }
    }

    /** Moves an entity by the frame's drift (+1 out to the true place, -1 into the frame) and its orbital speed. */
    private static void shift(ServerLevel level, Entity e, Frame f, Tuning.SpaceTuning t, long time, int sign) {
        Planet p = planet(level, f).orElse(null);
        if (p == null) {
            return;
        }
        double drift = f.drift(p, time);
        double speed = Orbits.orbitSpeed(p, e.getY(), t);
        Vec3 velocity = GravityChangerAPI.getWorldVelocity(e).add(sign * speed, 0, 0);
        Vec3 to = e.position().add(sign * drift, 0, 0);
        level.getChunk(BlockPos.containing(to));
        Entity moved = SpaceTravel.move(e, level, to, 0);
        GravityChangerAPI.setWorldVelocity(moved, velocity);
        moved.hurtMarked = true;
    }
}
