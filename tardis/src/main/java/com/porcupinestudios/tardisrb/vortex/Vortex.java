package com.porcupinestudios.tardisrb.vortex;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.Exteriors;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.entity.TardisEntities;
import com.porcupinestudios.tardisrb.entity.VortexArc;
import com.porcupinestudios.tardisrb.entity.Wormhole;
import com.porcupinestudios.tardisrb.flight.Destinations;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.flight.FreeFlight;
import com.porcupinestudios.tardisrb.flight.Piloting;
import com.porcupinestudios.tardisrb.look.Looks;
import com.porcupinestudios.tardisrb.progression.Destination;
import com.porcupinestudios.tardisrb.progression.Health;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Flying through the time vortex (Aaron 2026-11-09: instead of the 1.7.10 button-pressing minigame, dematerialising
 * flies the box through a time vortex dimension, dodging obstacles, "to" the destination). When a crew member pulls the
 * dematerialisation lever ({@code ConsoleControls.takeOff}) the box leaves its spot and appears as a loose body
 * ({@link TardisBody}) at the start of its own lane in {@code tardis_rb:time_vortex}: a tunnel along +z round
 * ({@code lane x}, {@link #LANE_Y}), drawn by {@code client/VortexRenderer}. The puller is seated to fly it
 * ({@link Piloting#seat}); it drifts forward by itself, and the pilot steers round lightning arcs
 * ({@link VortexArc}) placed across the tunnel ahead of it. Each hit costs charge and pushes the landing off target. At the tunnel's end ({@code length}
 * blocks) it materialises at the destination through the usual hop or jump ({@link Flight}), which pays for the
 * trip; if that fails it lands back where it left. With nobody flying, the box keeps drifting (autopilot), and if
 * its chunk isn't loaded the trip advances on its own.
 *
 * <p>The trip is kept in the record's module data {@value #MODULE}: where it left ({@code from_*}), what it aims at
 * ({@code destination} for a jump, empty for a hop; {@code target}; {@code facing}), the lane, length, progress,
 * hits, where the next obstacle goes and the obstacles placed (to clear them).
 */
public final class Vortex {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            TardisRB.id("time_vortex"));
    public static final String MODULE = "tardis_rb:vortex";
    /** The tunnel's axis height. */
    public static final double LANE_Y = 128;
    /** Lanes are this far apart (one per interior plot), so trips never meet. */
    public static final int LANE_SPACING = 4096;

    private Vortex() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Vortex::onTick);
        NeoForge.EVENT_BUS.addListener(Vortex::onEntityTick);
    }

    /** The axis x of the lane nearest {@code x} (lanes are {@link #LANE_SPACING} apart, at n x spacing + 0.5). */
    public static double laneX(double x) {
        return Math.round((x - 0.5) / LANE_SPACING) * (double) LANE_SPACING + 0.5;
    }

    /**
     * The tunnel twists and turns (Aaron 2026-11-09): how far its axis is from the straight lane line (lane x,
     * {@link #LANE_Y}) at {@code z}, as (x, y, 0). Two slow waves each way, gentle enough (about 0.35 blocks sideways
     * per block forward at {@code bend} 1) for a pilot to follow; straight before z 0, easing in over the first 40.
     */
    public static Vec3 bend(double z) {
        double s = TardisContent.tuning().vortex().bend() * Math.max(0, Math.min(1, z / 40));
        if (s == 0) {
            return Vec3.ZERO;
        }
        return new Vec3(s * (8 * Math.sin(z / 45) + 3 * Math.sin(z / 19 + 1.3)),
                s * (6 * Math.sin(z / 37 + 0.7) + 2.5 * Math.sin(z / 16 + 2.1)), 0);
    }

    /**
     * The tube is solid (Aaron 2026-11-09): where a point at ({@code x}, {@code y}, {@code z}) more than {@code limit}
     * from its lane's (bent) axis is pushed back to, or null if it is inside.
     */
    public static Vec3 keepInside(double x, double y, double z, double limit) {
        Vec3 b = bend(z);
        double ax = laneX(x) + b.x;
        double ay = LANE_Y + b.y;
        double ox = x - ax;
        double oy = y - ay;
        double r = Math.sqrt(ox * ox + oy * oy);
        return r <= limit ? null : new Vec3(ax + ox / r * limit, ay + oy / r * limit, z);
    }

    /**
     * Anything loose in the vortex (a crew member stepping out of the doors, dropped items, mobs) stays inside the
     * tube: it is pushed back off the wall, loses its speed into the wall, and can stand on the tube's bottom. Runs on
     * both sides, so a player's own client keeps them in too. The box itself bounces ({@code TardisBody}).
     */
    private static void onEntityTick(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (e instanceof TardisBody || e instanceof Portal || e.isPassenger() || e.level().dimension() != DIMENSION) {
            return;
        }
        double half = e.getBbHeight() / 2;
        Vec3 back = keepInside(e.getX(), e.getY() + half, e.getZ(),
                TardisContent.tuning().vortex().radius() + 1 - Math.max(half, e.getBbWidth() / 2));
        if (back == null) {
            return;
        }
        Vec3 out = new Vec3(e.getX() - back.x, e.getY() + half - back.y, 0).normalize();
        e.setPos(back.x, back.y - half, back.z);
        Vec3 v = e.getDeltaMovement();
        double into = v.dot(out);
        if (into > 0) {
            e.setDeltaMovement(v.subtract(out.scale(into)));
        }
        if (out.y < -0.5) { // on the tube's bottom
            e.setOnGround(true);
            e.resetFallDistance();
        }
    }

    public static boolean inVortex(TardisRecord tardis) {
        return tardis.moduleData(MODULE).contains("length");
    }

    /** How far through the tunnel it is, 0..1. */
    public static double progress(TardisRecord tardis) {
        CompoundTag t = tardis.moduleData(MODULE);
        return t.contains("length") ? Math.min(1, t.getDouble("progress") / Math.max(1, t.getInt("length"))) : 0;
    }

    public static int hits(TardisRecord tardis) {
        return tardis.moduleData(MODULE).getInt("hits");
    }

    /** How far off its course line the box will land, in blocks: grows with every bit of damage on the trip. */
    public static double inaccuracy(TardisRecord tardis) {
        return tardis.moduleData(MODULE).getDouble("inaccuracy");
    }

    /** True while it is crash landing: falling out of the tunnel, before it reaches the world. */
    public static boolean crashing(TardisRecord tardis) {
        return tardis.moduleData(MODULE).contains("crash");
    }

    /**
     * Sets off through the vortex for {@code target} (a hop in this dimension when {@code destination} is empty, else
     * a jump there), landing with the doors facing {@code facing}. Checks first what the landing will check (unlocks,
     * range, charge), so a doomed trip doesn't start. Says what happened; {@code done} in the result means it left.
     */
    public static Flight.Outcome depart(MinecraftServer server, TardisRecord tardis, Optional<ResourceLocation> destination,
            BlockPos target, Optional<Direction> facing, Optional<ServerPlayer> pilot) {
        Tuning.VortexTuning t = TardisContent.tuning().vortex();
        ServerLevel vortex = server.getLevel(DIMENSION);
        if (tardis.location().isEmpty()) {
            return Flight.Outcome.fail("tardis_rb.hop.nowhere", "The TARDIS isn't standing anywhere");
        }
        if (vortex == null) {
            return Flight.Outcome.fail("tardis_rb.vortex.missing", "The time vortex isn't in this world");
        }
        if (inVortex(tardis)) {
            return Flight.Outcome.fail("tardis_rb.vortex.already", "The TARDIS is already in the vortex");
        }
        GlobalPos from = tardis.location().get();
        double distance;
        if (destination.isEmpty()) {
            Unlocks.Result hop = Unlocks.check(server, tardis, Flight.SHORT_HOP, pilot);
            if (!hop.allowed()) {
                return new Flight.Outcome(Optional.of(hop.reason()), Optional.empty());
            }
            distance = Math.sqrt(from.pos().distSqr(target));
            double range = Unlocks.stat(tardis, "range");
            if (distance > range) {
                return Flight.Outcome.fail("tardis_rb.hop.range", "Too far: %s blocks, the TARDIS can hop %s",
                        (long) Math.ceil(distance), (long) range);
            }
            long cost = Flight.baseCost(distance);
            if (tardis.charge() < cost) {
                return Flight.Outcome.fail("tardis_rb.hop.charge", "Needs %s charge (the TARDIS has %s)", cost,
                        tardis.charge());
            }
        } else {
            Optional<Component> closed = Destinations.closed(server, tardis, destination.get(), pilot);
            if (closed.isPresent()) {
                return new Flight.Outcome(closed, Optional.empty());
            }
            Destination d = TardisContent.destination(destination.get()).orElseThrow();
            if (tardis.charge() < d.charge()) {
                return Flight.Outcome.fail("tardis_rb.hop.charge", "Needs %s charge (the TARDIS has %s)", d.charge(),
                        tardis.charge());
            }
            distance = -1;
        }
        int length = (int) Math.min(t.maxLength(), distance < 0 ? t.jumpLength()
                : t.minLength() + distance * t.lengthPerBlock());
        // leave: doors shut, the block (or the loose body) goes
        Doors.close(server, tardis);
        ServerLevel fromLevel = server.getLevel(from.dimension());
        Direction was = FreeFlight.isLoose(tardis) ? FreeFlight.facing(server, tardis)
                : fromLevel == null ? Direction.NORTH : Exteriors.lift(fromLevel, from.pos());
        FreeFlight.dock(server, tardis);
        double laneX = (tardis.plot() + 1) * (double) LANE_SPACING + 0.5;
        TardisBody body = TardisEntities.BODY.get().create(vortex);
        if (body == null) {
            if (fromLevel != null) {
                Exteriors.place(fromLevel, from.pos(), was, tardis.id());
            }
            return Flight.Outcome.fail("tardis_rb.vortex.failed", "The TARDIS shudders and stays put");
        }
        vortex.getChunk(BlockPos.containing(laneX, LANE_Y, 0)); // load the start of the lane
        body.moveTo(laneX, LANE_Y, 0.5, Direction.SOUTH.toYRot(), 0);
        body.setTardis(tardis.id());
        body.setThrusters(true);
        body.setLook(Looks.exterior(server, tardis));
        body.setLane((float) laneX, (float) LANE_Y);
        vortex.addFreshEntity(body);
        CompoundTag trip = new CompoundTag();
        trip.putString("from_dimension", from.dimension().location().toString());
        trip.putLong("from", from.pos().asLong());
        trip.putInt("from_facing", was.get2DDataValue());
        destination.ifPresent(d -> trip.putString("destination", d.toString()));
        trip.putLong("target", target.asLong());
        trip.putInt("facing", facing.map(Direction::get2DDataValue).orElse(-1));
        trip.putDouble("lane_x", laneX);
        trip.putInt("length", length);
        trip.putDouble("progress", 0);
        trip.putInt("hits", 0);
        trip.putDouble("inaccuracy", 0);
        trip.putInt("next_obstacle", 24);
        trip.put("obstacles", new ListTag());
        tardis.setModuleData(MODULE, trip);
        CompoundTag flight = tardis.moduleData(FreeFlight.MODULE);
        flight.putUUID("body", body.getUUID());
        tardis.setModuleData(FreeFlight.MODULE, flight);
        tardis.setLocation(Optional.of(GlobalPos.of(DIMENSION, body.blockPosition())));
        // the lane has no player yet: keep the box's chunk loaded and seat the pilot once the box can be found
        keepLoaded(vortex, body.position());
        pilot.ifPresent(p -> {
            trip.putUUID("pilot", p.getUUID());
            tardis.setModuleData(MODULE, trip);
        });
        return new Flight.Outcome(Optional.empty(), Optional.empty());
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel vortex = server.getLevel(DIMENSION);
        if (vortex == null) {
            return;
        }
        for (TardisRecord tardis : TardisManager.get(server).all()) {
            if (inVortex(tardis)) {
                step(server, vortex, tardis);
            }
        }
    }

    private static final TicketType<ChunkPos> TICKET = TicketType.create("tardis_rb_vortex",
            Comparator.comparingLong(ChunkPos::toLong), 40);
    private static final TicketType<ChunkPos> CRASH_TICKET = TicketType.create("tardis_rb_crash",
            Comparator.comparingLong(ChunkPos::toLong), 400);

    /** Keeps the chunks round {@code at} entity-ticking for a couple of seconds (re-added each tick of a trip). */
    private static void keepLoaded(ServerLevel vortex, Vec3 at) {
        ChunkPos c = new ChunkPos(BlockPos.containing(at));
        vortex.getChunkSource().addRegionTicket(TICKET, c, 3, c); // 3: the chunks round it too, so the next one is ready before it gets there
    }

    /** One tick of a trip: progress, obstacles ahead, hits, arrival. */
    static void step(MinecraftServer server, ServerLevel vortex, TardisRecord tardis) {
        Tuning.VortexTuning t = TardisContent.tuning().vortex();
        CompoundTag trip = tardis.moduleData(MODULE);
        Optional<TardisBody> body = FreeFlight.body(server, tardis);
        keepLoaded(vortex, body.map(TardisBody::position)
                .orElse(new Vec3(trip.getDouble("lane_x"), LANE_Y, trip.getDouble("progress"))));
        if (body.isPresent() && trip.hasUUID("pilot")) {
            ServerPlayer pilot = server.getPlayerList().getPlayer(trip.getUUID("pilot"));
            trip.remove("pilot");
            if (pilot != null) {
                pilot.sendSystemMessage(Piloting.seat(server, tardis, pilot));
            }
        }
        if (Wormholes.sucking(trip)) {
            Wormholes.suck(server, tardis, trip, body);
            return;
        }
        if (trip.contains("crash")) {
            crashFall(server, tardis, trip, body);
            return;
        }
        double progress;
        if (body.isPresent() && body.get().getControllingPassenger() != null) {
            progress = Math.max(trip.getDouble("progress"), body.get().getZ());
        } else {
            // nobody flying (or not loaded): autopilot carries it on down the middle of the tunnel
            progress = trip.getDouble("progress") + t.speed();
            body.ifPresent(b -> b.setPos(b.getX(), b.getY(), Math.max(b.getZ(), progress)));
        }
        trip.putDouble("progress", progress);
        int length = trip.getInt("length");
        if (body.isPresent()) {
            placeAhead(vortex, trip, t, progress, length, tardis);
            Wormholes.placeAhead(server, vortex, tardis, trip, progress, length);
            if (hitArc(vortex, body.get(), trip, t)) {
                tardis.useCharge(Math.min(tardis.charge(), t.hitCharge()));
                trip.putInt("hits", trip.getInt("hits") + 1);
                vortex.playSound(null, body.get().blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                        SoundSource.NEUTRAL, 1.2F, 1.1F);
                if (body.get().getControllingPassenger() instanceof ServerPlayer p) {
                    p.displayClientMessage(Component.translatableWithFallback("tardis_rb.vortex.hit",
                            "The TARDIS flies into a lightning arc! (health %s)",
                            (int) Math.ceil(Math.max(0, Health.get(tardis) - t.damage().arc()))), true);
                }
                damage(server, tardis, trip, body.get(), t.damage().arc());
            }
            // the charged wall: a bump (coming onto it, not resting on it) shocks the box and hurts it
            Vec3 axis = bend(body.get().getZ());
            double r = Math.hypot(body.get().getX() - trip.getDouble("lane_x") - axis.x, body.get().getY() - LANE_Y - axis.y);
            boolean atWall = r >= t.radius() - 0.15;
            if (atWall && !trip.getBoolean("at_wall") && !trip.contains("crash")) {
                body.get().shock(new Vec3(trip.getDouble("lane_x") + axis.x - body.get().getX(),
                        LANE_Y + axis.y - body.get().getY(), 0), 1);
                damage(server, tardis, trip, body.get(), t.damage().wall());
            }
            trip.putBoolean("at_wall", atWall);
            if (Health.get(tardis) <= 0) {
                damage(server, tardis, trip, body.get(), 0); // set to 0 some other way (/tardis health): it goes down now
            }
            if (!trip.contains("crash") && Wormholes.check(server, vortex, tardis, trip, body.get())) {
                tardis.setModuleData(MODULE, trip);
                return;
            }
            if (trip.contains("crash")) {
                tardis.setModuleData(MODULE, trip);
                return;
            }
        }
        tardis.setModuleData(MODULE, trip);
        if (progress >= length) {
            arrive(server, tardis);
        }
    }

    /** Takes {@code amount} off the trip's health and adds to its inaccuracy; at 0 health it starts to crash. */
    private static void damage(MinecraftServer server, TardisRecord tardis, CompoundTag trip, TardisBody body,
            double amount) {
        double health = Health.add(tardis, -amount);
        trip.putDouble("inaccuracy", trip.getDouble("inaccuracy")
                + amount * TardisContent.tuning().vortex().damage().driftPerDamage());
        if (health > 0 || trip.contains("crash")) {
            return;
        }
        // where along its course it was when it broke down: that is where it comes down
        trip.putDouble("crash", Math.min(1, trip.getDouble("progress") / Math.max(1, trip.getInt("length"))));
        trip.putInt("crash_ticks", 0);
        if (body.getFirstPassenger() instanceof ServerPlayer p) {
            // the pilot rides it down, the controls dead (TardisBody.getControllingPassenger)
            p.sendSystemMessage(Component.translatableWithFallback("tardis_rb.vortex.crash",
                    "The TARDIS is breaking up! It falls out of the vortex..."));
        }
        body.setThrusters(false);
        body.setCrashing(true);
        body.setDeltaMovement(0, -0.1, 0);
    }

    /** Falling out of the tunnel through the clouds; once well under the tube (or after 3 s) it drops into the world. */
    private static void crashFall(MinecraftServer server, TardisRecord tardis, CompoundTag trip,
            Optional<TardisBody> body) {
        int ticks = trip.getInt("crash_ticks") + 1;
        trip.putInt("crash_ticks", ticks);
        tardis.setModuleData(MODULE, trip);
        if (ticks > 60 || body.map(b -> b.getY() < LANE_Y + bend(b.getZ()).y - 22).orElse(true)) {
            crashOut(server, tardis);
        }
    }

    /**
     * The crash (Aaron 2026-11-09): the box leaves the vortex and falls out of a rift, thrusters dead, over the point
     * its course line had reached, pushed off the line by the trip's inaccuracy; under halfway through a jump it falls
     * back into the dimension it left. It hits the ground with a crater ({@code TardisBody.impact}) and lies there as a
     * loose box. Says what happened.
     */
    public static Flight.Outcome crashOut(MinecraftServer server, TardisRecord tardis) {
        Tuning.VortexDamage d = TardisContent.tuning().vortex().damage();
        CompoundTag trip = tardis.moduleData(MODULE);
        if (!trip.contains("length")) {
            return Flight.Outcome.fail("tardis_rb.vortex.not_in", "The TARDIS isn't in the vortex");
        }
        double f = trip.contains("crash") ? trip.getDouble("crash") : progress(tardis);
        GlobalPos from = from(trip);
        ServerLevel fromLevel = Optional.ofNullable(server.getLevel(from.dimension())).orElse(server.overworld());
        ServerLevel toLevel = trip.contains("destination")
                ? TardisContent.destination(ResourceLocation.parse(trip.getString("destination")))
                        .map(dest -> server.getLevel(dest.dimension())).orElse(fromLevel)
                : fromLevel;
        if (toLevel == null) {
            toLevel = fromLevel;
        }
        Course c = course(Vec3.atBottomCenterOf(from.pos()), fromLevel.dimensionType().coordinateScale(),
                Vec3.atBottomCenterOf(BlockPos.of(trip.getLong("target"))), toLevel.dimensionType().coordinateScale(), f);
        ServerLevel level = c.inTarget() ? toLevel : fromLevel;
        BlockPos aim = offCourse(BlockPos.containing(c.pos()), trip.getDouble("inaccuracy"), RandomSource.create());
        Direction facing = FreeFlight.facing(server, tardis);
        Optional<TardisBody> was = FreeFlight.body(server, tardis);
        Optional<ServerPlayer> pilot = was.map(b -> b.getFirstPassenger() instanceof ServerPlayer p ? p : null);
        double fall = was.map(b -> Math.min(-0.1, b.getDeltaMovement().y)).orElse(-0.3);
        Vec3 across = new Vec3(c.dir().x, 0, c.dir().z);
        across = across.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : across.normalize();
        clearTrip(server, tardis, trip);
        Health.set(tardis, d.afterCrash()); // it limps on: enough to fly, not to take a hit
        dropInto(server, tardis, level, aim, across.scale(d.crashSpeed()).add(0, fall, 0), d.crater(), facing, pilot);
        return new Flight.Outcome(Optional.empty(), Optional.empty());
    }

    /**
     * The box carries on out of a rift in {@code level} over {@code aim} (Aaron 2026-11-10: it has to be seen to fall,
     * not just appear): the rift opens as high over the ground as there is room for, up to {@code crash_height} (of a
     * few spots round the aim, the one with the most room), and the box comes out of it with {@code velocity}
     * (forward momentum carried through), thrusters off and tumbling, under gravity, the controls dead. Its
     * {@code pilot} goes with it, riding; when it hits the ground ({@code TardisBody.impact}, with a crater of power
     * {@code crater}) they go back to the console. Gives the body, or null if it had to be set down as a block.
     */
    static TardisBody dropInto(MinecraftServer server, TardisRecord tardis, ServerLevel level, BlockPos aim,
            Vec3 velocity, double crater, Direction facing, Optional<ServerPlayer> pilot) {
        Tuning.VortexDamage d = TardisContent.tuning().vortex().damage();
        RandomSource random = RandomSource.create();
        BlockPos ground = ground(level, aim);
        int room = room(level, ground, d.crashHeight());
        for (int k = 0; k < 8 && room < d.crashHeight(); k++) {
            BlockPos g = ground(level, aim.offset(random.nextInt(49) - 24, 0, random.nextInt(49) - 24));
            int r = room(level, g, d.crashHeight());
            if (r > room) {
                ground = g;
                room = r;
            }
        }
        BlockPos start = ground.above(1 + room);
        // nobody may be near: keep the spot loaded while it falls
        level.getChunkSource().addRegionTicket(CRASH_TICKET, new ChunkPos(start), 3, new ChunkPos(start));
        TardisBody body = TardisEntities.BODY.get().create(level);
        if (body == null) {
            Exteriors.place(level, ground.above(), facing, tardis.id());
            tardis.setLocation(Optional.of(GlobalPos.of(level.dimension(), ground.above())));
            pilot.ifPresent(Piloting::stop);
            return null;
        }
        body.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, facing.toYRot(), 0);
        body.setTardis(tardis.id());
        body.setThrusters(false);
        body.setLook(Looks.exterior(server, tardis));
        body.setCrashing(true);
        body.crater = crater;
        body.setDeltaMovement(velocity);
        level.addFreshEntity(body);
        // the rift it comes out of, facing the way it is going
        Wormhole rift = TardisEntities.WORMHOLE.get().create(level);
        if (rift != null) {
            float yaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
            rift.moveTo(body.getX(), body.getY() + body.getBbHeight() / 2, body.getZ(), yaw, 0);
            rift.setup(1.8, 0, 0);
            rift.setLife(60);
            level.addFreshEntity(rift);
        }
        CompoundTag flight = tardis.moduleData(FreeFlight.MODULE);
        flight.putUUID("body", body.getUUID());
        tardis.setModuleData(FreeFlight.MODULE, flight);
        tardis.setLocation(Optional.of(GlobalPos.of(level.dimension(), body.blockPosition())));
        pilot.ifPresent(p -> Piloting.carry(p, body));
        level.playSound(null, body.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 4, 0.8F);
        return body;
    }

    /** How many blocks over {@code ground} the box (and the rift round it) has room to start, up to {@code most}. */
    private static int room(ServerLevel level, BlockPos ground, int most) {
        BlockPos p = ground.above();
        int n = 0;
        while (n < most && p.getY() < level.getMaxBuildHeight() - 4 && level.getBlockState(p.above(3)).isAir()) {
            p = p.above();
            n++;
        }
        return n;
    }

    /** The point {@code f} (0..1) along a course, which dimension it is in, and the way the course runs. */
    public record Course(boolean inTarget, Vec3 pos, Vec3 dir) {}

    /**
     * The course line from {@code from} (in a dimension of coordinate scale {@code fromScale}: 8 for the Nether) to
     * {@code to} (scale {@code toScale}), at {@code f}: worked out in the target dimension's coordinates; before
     * halfway the point is given back in the starting dimension's own coordinates ({@code inTarget} false).
     */
    public static Course course(Vec3 from, double fromScale, Vec3 to, double toScale, double f) {
        double k = fromScale / toScale;
        Vec3 start = new Vec3(from.x * k, from.y, from.z * k);
        Vec3 at = start.lerp(to, Math.max(0, Math.min(1, f)));
        Vec3 dir = to.subtract(start);
        return f >= 0.5 ? new Course(true, at, dir) : new Course(false, new Vec3(at.x / k, at.y, at.z / k), dir);
    }

    /** {@code p} pushed a random way up to {@code inaccuracy} blocks across. */
    static BlockPos offCourse(BlockPos p, double inaccuracy, RandomSource random) {
        if (inaccuracy <= 0) {
            return p;
        }
        double a = random.nextDouble() * Math.PI * 2;
        double r = inaccuracy * Math.sqrt(random.nextDouble());
        return p.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r));
    }

    /** The ground at {@code aim}: the top block, or under a roof (the Nether) the first floor below the aim. */
    static BlockPos ground(ServerLevel level, BlockPos aim) {
        BlockPos g = column(level, aim);
        if (g.getY() <= level.getMinBuildHeight()) {
            g = column(level, BlockPos.ZERO.atY(aim.getY())); // nothing under it (the End's void): the middle of the world
        }
        return g;
    }

    private static BlockPos column(ServerLevel level, BlockPos aim) {
        if (!level.dimensionType().hasCeiling()) {
            return new BlockPos(aim.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, aim.getX(),
                    aim.getZ()) - 1, aim.getZ());
        }
        // under a roof: the first floor (air over a block) below the aim's height, else below the roof
        int min = level.getMinBuildHeight();
        int top = level.getLogicalHeight() - 2;
        BlockPos.MutableBlockPos p = aim.mutable();
        for (int from : new int[] {Math.max(min + 1, Math.min(aim.getY(), top)), top}) {
            for (int y = from; y > min; y--) {
                if (level.getBlockState(p.setY(y)).isAir() && !level.getBlockState(p.setY(y - 1)).isAir()) {
                    return p.immutable();
                }
            }
        }
        return p.setY(min).immutable();
    }

    static GlobalPos from(CompoundTag trip) {
        ResourceLocation dim = ResourceLocation.tryParse(trip.getString("from_dimension"));
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim == null ? Level.OVERWORLD.location() : dim),
                BlockPos.of(trip.getLong("from")));
    }

    /** Ends the trip: the pilot gets off and goes back to the console, the body and the obstacles go. */
    static void clearTrip(MinecraftServer server, TardisRecord tardis, CompoundTag trip) {
        FreeFlight.dock(server, tardis);
        ServerLevel vortex = server.getLevel(DIMENSION);
        if (vortex != null) {
            Wormholes.removeAll(vortex, trip);
            for (Tag tag : trip.getList("obstacles", Tag.TAG_COMPOUND)) {
                removeArc(vortex, (CompoundTag) tag);
            }
        }
        tardis.setModuleData(MODULE, new CompoundTag());
    }

    /**
     * Places lightning arcs up to 96 blocks ahead of the box (Aaron 2026-11-10: "unmoving lightning bolts" across the
     * tunnel to dodge): every {@code obstacle_gap} / 2 .. 1.5 x gap blocks, 1 to {@code arcs_per_section} lines
     * across that cross-section, each at a random angle and a random way off the middle (up to 3/4 of the radius).
     */
    private static void placeAhead(ServerLevel vortex, CompoundTag trip, Tuning.VortexTuning t, double progress,
            int length, TardisRecord tardis) {
        int next = trip.getInt("next_obstacle");
        RandomSource random = RandomSource.create(tardis.id().getLeastSignificantBits() ^ next);
        while (next < progress + 96 && next < length - 16) {
            int n = 1 + random.nextInt(Math.max(1, t.arcsPerSection()));
            for (int i = 0; i < n; i++) {
                placeArc(vortex, trip, next, random.nextDouble() * Math.PI,
                        (random.nextDouble() * 2 - 1) * t.radius() * 0.75);
            }
            next += Math.max(6, t.obstacleGap() / 2 + random.nextInt(Math.max(1, t.obstacleGap())));
        }
        trip.putInt("next_obstacle", next);
    }

    /**
     * Puts a lightning arc straight across the tunnel at {@code z}: a line at {@code angle} (radians from the x axis,
     * in the tunnel's cross-section) passing {@code offset} blocks from its (bent) axis, running into the cloud wall at
     * both ends so it can't be slipped round. Adds it to the trip's {@code obstacles}; gives the entity drawing it.
     */
    public static VortexArc placeArc(ServerLevel vortex, CompoundTag trip, double z, double angle, double offset) {
        Tuning.VortexTuning t = TardisContent.tuning().vortex();
        double r = t.radius() + 1.5;
        double h = Math.sqrt(Math.max(1, r * r - offset * offset));
        Vec3 b = bend(z);
        Vec3 dir = new Vec3(Math.cos(angle), Math.sin(angle), 0);
        Vec3 mid = new Vec3(trip.getDouble("lane_x") + b.x - dir.y * offset, LANE_Y + b.y + dir.x * offset, z);
        VortexArc arc = TardisEntities.ARC.get().create(vortex);
        CompoundTag tag = new CompoundTag();
        if (arc != null) {
            arc.moveTo(mid.x, mid.y, mid.z, 0, 0);
            arc.setup(new Vector3f((float) (dir.x * h), (float) (dir.y * h), 0), (float) t.arcThickness());
            vortex.addFreshEntity(arc);
            tag.putUUID("id", arc.getUUID());
        }
        tag.putDouble("z", z);
        tag.putDouble("ax", mid.x - dir.x * h);
        tag.putDouble("ay", mid.y - dir.y * h);
        tag.putDouble("bx", mid.x + dir.x * h);
        tag.putDouble("by", mid.y + dir.y * h);
        ListTag list = trip.getList("obstacles", Tag.TAG_COMPOUND);
        list.add(tag);
        trip.put("obstacles", list);
        return arc;
    }

    private static void removeArc(ServerLevel vortex, CompoundTag arc) {
        if (arc.hasUUID("id") && vortex.getEntity(arc.getUUID("id")) instanceof VortexArc e) {
            e.discard();
        }
    }

    /**
     * True if the box touches a lightning arc it hasn't been hit by yet: within {@code arc_thickness} of its line. The
     * arc stays (it never moves); each one can hurt the box once. Arcs well behind the box go.
     */
    private static boolean hitArc(ServerLevel vortex, TardisBody body, CompoundTag trip, Tuning.VortexTuning t) {
        AABB box = body.getBoundingBox().inflate(t.arcThickness());
        Vec3 c = box.getCenter();
        ListTag list = trip.getList("obstacles", Tag.TAG_COMPOUND);
        boolean hit = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            CompoundTag arc = list.getCompound(i);
            double z = arc.getDouble("z");
            if (z < c.z - 12) {
                removeArc(vortex, arc);
                list.remove(i);
                continue;
            }
            if (hit || arc.getBoolean("hit") || z < box.minZ || z > box.maxZ) {
                continue;
            }
            Vec3 a = new Vec3(arc.getDouble("ax"), arc.getDouble("ay"), z);
            Vec3 b = new Vec3(arc.getDouble("bx"), arc.getDouble("by"), z);
            int n = (int) Math.ceil(a.distanceTo(b) / 0.25);
            for (int k = 0; k <= n; k++) {
                Vec3 p = a.lerp(b, k / (double) n);
                if (box.contains(p)) {
                    arc.putBoolean("hit", true);
                    body.shock(c.subtract(p), 1);
                    hit = true;
                    break;
                }
            }
        }
        trip.put("obstacles", list);
        return hit;
    }

    /**
     * The end of the tunnel: the pilot goes back inside, the body and the obstacles go, and the TARDIS materialises at
     * the end of its course (pushed off it by the trip's inaccuracy), or back where it left if it can't land there.
     */
    public static Flight.Outcome arrive(MinecraftServer server, TardisRecord tardis) {
        CompoundTag trip = tardis.moduleData(MODULE);
        if (!trip.contains("length")) {
            return Flight.Outcome.fail("tardis_rb.vortex.not_in", "The TARDIS isn't in the vortex");
        }
        clearTrip(server, tardis, trip);
        GlobalPos from = from(trip);
        tardis.setLocation(Optional.of(from));
        BlockPos target = offCourse(BlockPos.of(trip.getLong("target")), trip.getDouble("inaccuracy"),
                RandomSource.create());
        Direction facing = Direction.from2DDataValue(trip.getInt("facing") < 0 ? trip.getInt("from_facing")
                : trip.getInt("facing"));
        Flight.Outcome out = trip.contains("destination")
                ? Flight.jump(server, tardis, ResourceLocation.parse(trip.getString("destination")),
                        Optional.of(target), Optional.of(facing), Optional.empty())
                : Flight.hop(server, tardis, target, Optional.of(facing), Optional.empty());
        if (!out.done()) {
            ServerLevel back = server.getLevel(from.dimension());
            if (back != null && !(back.getBlockState(from.pos()).getBlock() instanceof ExteriorBlock)) {
                Exteriors.place(back, from.pos(), Direction.from2DDataValue(trip.getInt("from_facing")), tardis.id());
            }
        }
        return out;
    }
}
