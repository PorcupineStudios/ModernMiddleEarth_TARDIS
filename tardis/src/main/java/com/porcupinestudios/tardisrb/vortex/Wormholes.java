package com.porcupinestudios.tardisrb.vortex;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.entity.TardisEntities;
import com.porcupinestudios.tardisrb.entity.Wormhole;
import com.porcupinestudios.tardisrb.flight.Destinations;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.flight.FreeFlight;
import com.porcupinestudios.tardisrb.progression.Destination;
import com.porcupinestudios.tardisrb.progression.Health;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import com.porcupinestudios.tardisrb.progression.WormholeExit;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Mesh2D;

/**
 * Wormholes in the time vortex (Aaron 2026-11-09: "openings in the time vortex ... wormholes that are opaque portals by
 * default but if you upgrade the scanner system they become immersive portals", with "a vacuum effect that pulls the
 * TARDIS" weakened by upgrading the stabilizers, leading to random places in dimensions picked by weight). Placed
 * along the lane ahead of the box like the lightning arcs ({@link #placeAhead}); each is a {@link Wormhole} entity, and the trip
 * keeps a list {@code wormholes} of them: entity id, z, exit dimension and aim, and the see-through portal's id.
 * Crossing one's disc ({@link #check}) sucks the box in; it spins ({@link #suck}) and is thrown out tumbling over the
 * aim in that dimension ({@link #exit}), ending the trip. Numbers: tuning {@code vortex.wormholes}.
 */
public final class Wormholes {
    public static final String PORTAL_TAG = "tardis_rb:wormhole";

    private Wormholes() {}

    private static Tuning.WormholeTuning tuning() {
        return TardisContent.tuning().vortex().wormholes();
    }

    /** How hard this TARDIS's wormholes pull: the tuned pull, less its stabilizers' {@code pull_resist}. */
    public static double pull(TardisRecord tardis) {
        return tuning().pull() * Math.max(0, 1 - Unlocks.stat(tardis, "pull_resist"));
    }

    /**
     * Picks an exit dimension by weight ({@link WormholeExit#weight}) for a trip that left {@code left} aiming at
     * {@code aim}, among the {@code allowed} ones; empty if none has any weight.
     */
    public static Optional<ResourceKey<Level>> pick(Collection<WormholeExit> exits, ResourceLocation left,
            ResourceLocation aim, Predicate<ResourceKey<Level>> allowed, RandomSource random) {
        double total = 0;
        for (WormholeExit e : exits) {
            total += allowed.test(e.dimension()) ? e.weight(left, aim) : 0;
        }
        if (total <= 0) {
            return Optional.empty();
        }
        double roll = random.nextDouble() * total;
        for (WormholeExit e : exits) {
            double w = allowed.test(e.dimension()) ? e.weight(left, aim) : 0;
            if (w > 0 && (roll -= w) < 0) {
                return Optional.of(e.dimension());
            }
        }
        return Optional.empty();
    }

    /**
     * Dimensions a wormhole may lead to for this TARDIS: ones in this world, and with {@code respect_locks} not one
     * whose every destination file is still locked or switched off for it (one with no destination file is fine).
     */
    static Predicate<ResourceKey<Level>> allowed(MinecraftServer server, TardisRecord tardis) {
        boolean locks = tuning().respectLocks();
        return dim -> {
            if (server.getLevel(dim) == null) {
                return false;
            }
            if (!locks) {
                return true;
            }
            boolean any = false;
            for (Map.Entry<ResourceLocation, Destination> e
                    : TardisContent.get().destinations().entrySet()) {
                if (e.getValue().dimension().equals(dim)) {
                    if (Destinations.closed(server, tardis, e.getKey(), Optional.empty()).isEmpty()) {
                        return true;
                    }
                    any = true;
                }
            }
            return !any;
        };
    }

    /** The dimension a trip is aiming at: the jump's destination, or where it left for a hop. */
    private static ResourceKey<Level> aimDimension(CompoundTag trip) {
        GlobalPos from = Vortex.from(trip);
        return trip.contains("destination")
                ? TardisContent.destination(ResourceLocation.parse(trip.getString("destination")))
                        .map(d -> d.dimension()).orElse(from.dimension())
                : from.dimension();
    }

    /** Places wormholes up to 96 blocks ahead of the box: every {@code gap} blocks after {@code first}, by chance. */
    static void placeAhead(MinecraftServer server, ServerLevel vortex, TardisRecord tardis, CompoundTag trip,
            double progress, int length) {
        Tuning.WormholeTuning w = tuning();
        int next = trip.contains("next_wormhole") ? trip.getInt("next_wormhole") : w.first();
        RandomSource random = RandomSource.create();
        while (next < progress + 96 && next < length - 24) {
            final int at = next;
            if (random.nextDouble() < w.chance()) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(random.nextDouble())
                        * Math.max(0, TardisContent.tuning().vortex().radius() - w.radius() * 0.5);
                pick(TardisContent.get().wormholes().values(), Vortex.from(trip).dimension().location(),
                        aimDimension(trip).location(), allowed(server, tardis), random)
                        .ifPresent(dim -> place(server, vortex, tardis, trip, at, Math.cos(a) * r,
                                Math.sin(a) * r, dim));
            }
            next += Math.max(16, w.gap());
        }
        trip.putInt("next_wormhole", next);
    }

    /**
     * Puts a wormhole into {@code dim} at {@code z} along the trip's lane, ({@code ox}, {@code oy}) off the tunnel's
     * axis, and adds it to the trip. Its exit is a random spot up to {@code spread} blocks from where the course has
     * reached at {@code z}; with the scanner at {@code see_through_level} a portal shows that spot.
     */
    public static Wormhole place(MinecraftServer server, ServerLevel vortex, TardisRecord tardis, CompoundTag trip,
            double z, double ox, double oy, ResourceKey<Level> dim) {
        Tuning.WormholeTuning w = tuning();
        Vec3 b = Vortex.bend(z);
        Vec3 centre = new Vec3(trip.getDouble("lane_x") + b.x + ox, Vortex.LANE_Y + b.y + oy, z);
        Wormhole hole = TardisEntities.WORMHOLE.get().create(vortex);
        if (hole == null) {
            return null;
        }
        hole.moveTo(centre.x, centre.y, centre.z, 0, 0);
        hole.setup(w.radius(), pull(tardis), w.range());
        hole.setOwner(tardis.id());
        ServerLevel exitLevel = server.getLevel(dim);
        BlockPos aim = aim(server, trip, z, exitLevel, RandomSource.create());
        CompoundTag h = new CompoundTag();
        h.putUUID("id", hole.getUUID());
        h.putDouble("x", centre.x);
        h.putDouble("y", centre.y);
        h.putDouble("z", z);
        h.putString("dimension", dim.location().toString());
        h.putLong("aim", aim.asLong());
        if (Unlocks.stat(tardis, "wormhole_sight") >= w.seeThroughLevel()) {
            Portal portal = Portal.ENTITY_TYPE.create(vortex);
            if (portal != null) {
                BlockPos ground = Vortex.ground(exitLevel, aim);
                portal.setOriginPos(centre);
                // width x up = (0, 0, -1): its seen side faces back down the tunnel, at the box coming along it
                portal.setOrientationAndSize(new Vec3(-1, 0, 0), new Vec3(0, 1, 0), w.radius() * 1.8, w.radius() * 1.8);
                portal.setPortalShape(new SpecialFlatPortalShape(circle(32)));
                PortalAPI.setPortalTransformation(portal, dim, Vec3.atBottomCenterOf(ground).add(0, 6, 0),
                        DQuaternion.identity, 1.0);
                portal.setTeleportable(false);
                portal.portalTag = PORTAL_TAG;
                portal.disableDefaultAnimation();
                PortalAPI.spawnServerEntity(portal);
                h.putUUID("portal", portal.getUUID());
                hole.setOpen(true);
            }
        }
        vortex.addFreshEntity(hole);
        ListTag list = trip.getList("wormholes", Tag.TAG_COMPOUND);
        list.add(h);
        trip.put("wormholes", list);
        return hole;
    }

    /** A round portal: a fan of triangles in the portal's own -1..1 square. */
    private static Mesh2D circle(int n) {
        Mesh2D mesh = new Mesh2D();
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n;
            double a1 = (i + 1) * Math.PI * 2 / n;
            mesh.addTriangle(0, 0, Math.cos(a0), Math.sin(a0), Math.cos(a1), Math.sin(a1));
        }
        return mesh;
    }

    /**
     * Where a wormhole at {@code z} lets out in {@code level}: the point the course line has reached there, in that
     * dimension's coordinates, pushed a random way up to {@code spread} blocks, kept inside the world border.
     */
    private static BlockPos aim(MinecraftServer server, CompoundTag trip, double z, ServerLevel level,
            RandomSource random) {
        GlobalPos from = Vortex.from(trip);
        ServerLevel fromLevel = Optional.ofNullable(server.getLevel(from.dimension())).orElse(server.overworld());
        ServerLevel toLevel = Optional.ofNullable(server.getLevel(aimDimension(trip))).orElse(fromLevel);
        double f = z / Math.max(1, trip.getInt("length"));
        Vortex.Course c = Vortex.course(Vec3.atBottomCenterOf(from.pos()), fromLevel.dimensionType().coordinateScale(),
                Vec3.atBottomCenterOf(BlockPos.of(trip.getLong("target"))), toLevel.dimensionType().coordinateScale(), f);
        double k = (c.inTarget() ? toLevel : fromLevel).dimensionType().coordinateScale()
                / level.dimensionType().coordinateScale();
        BlockPos p = Vortex.offCourse(BlockPos.containing(c.pos().x * k, c.pos().y, c.pos().z * k), tuning().spread(),
                random);
        return level.getWorldBorder().clampToBounds(p.getX(), p.getY(), p.getZ());
    }

    /** The next wormhole ahead of {@code progress} on the trip, if any. */
    public static Optional<CompoundTag> next(TardisRecord tardis) {
        CompoundTag trip = tardis.moduleData(Vortex.MODULE);
        CompoundTag best = null;
        for (Tag t : trip.getList("wormholes", Tag.TAG_COMPOUND)) {
            CompoundTag h = (CompoundTag) t;
            if (h.getDouble("z") > trip.getDouble("progress")
                    && (best == null || h.getDouble("z") < best.getDouble("z"))) {
                best = h;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The box against the trip's wormholes, each tick: coming up to one's disc (within 1.5 x its radius in front of it,
     * and within its radius of its middle) catches it (true); ones well behind it go.
     */
    static boolean check(MinecraftServer server, ServerLevel vortex, TardisRecord tardis, CompoundTag trip,
            TardisBody body) {
        ListTag list = trip.getList("wormholes", Tag.TAG_COMPOUND);
        Vec3 c = body.position().add(0, body.getBbHeight() / 2, 0);
        for (int i = list.size() - 1; i >= 0; i--) {
            CompoundTag h = list.getCompound(i);
            double z = h.getDouble("z");
            if (!h.getBoolean("passed")) {
                if (c.z > z) {
                    h.putBoolean("passed", true);
                } else if (z - c.z <= tuning().radius() * 1.5
                        && Math.hypot(c.x - h.getDouble("x"), c.y - h.getDouble("y")) <= tuning().radius()) {
                    h.putBoolean("passed", true);
                    trip.put("wormholes", list);
                    capture(trip, body, h);
                    return true;
                }
            }
            if (c.z > z + 12) {
                remove(vortex, h);
                list.remove(i);
            }
        }
        trip.put("wormholes", list);
        return false;
    }

    private static void capture(CompoundTag trip, TardisBody body, CompoundTag h) {
        trip.put("suck", h.copy());
        trip.putInt("suck_ticks", 0);
        if (body.getFirstPassenger() instanceof ServerPlayer p) {
            // the pilot rides it through, the controls dead (TardisBody.getControllingPassenger)
            p.sendSystemMessage(Component.translatableWithFallback("tardis_rb.vortex.wormhole",
                    "The TARDIS is dragged into a wormhole!"));
        }
        body.setThrusters(false);
        body.setCrashing(true); // controls dead: it falls and keeps going, tumbling and crackling
        body.shock(body.position().subtract(h.getDouble("x"), h.getDouble("y"), h.getDouble("z")), 1);
        body.level().playSound(null, body.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.NEUTRAL, 2, 0.6F);
    }

    static boolean sucking(CompoundTag trip) {
        return trip.contains("suck");
    }

    /**
     * Being sucked in (Aaron 2026-11-10: it has to fall through, controls off, gravity on, still going forward): the box
     * falls and carries on along the tunnel ({@code TardisBody.vortexPhysics} while crashing) and the wormhole drags it
     * toward its middle; once it is all the way into the disc (or after {@code suck_ticks}) it comes out the far side.
     */
    static void suck(MinecraftServer server, TardisRecord tardis, CompoundTag trip, Optional<TardisBody> body) {
        CompoundTag h = trip.getCompound("suck");
        int ticks = trip.getInt("suck_ticks") + 1;
        trip.putInt("suck_ticks", ticks);
        tardis.setModuleData(Vortex.MODULE, trip);
        boolean through = body.map(b -> {
            Vec3 c = b.position().add(0, b.getBbHeight() / 2, 0);
            Vec3 v = b.getDeltaMovement();
            b.setDeltaMovement(v.x * 0.85 + (h.getDouble("x") - c.x) * 0.08,
                    v.y * 0.85 + (h.getDouble("y") - c.y) * 0.08, v.z);
            b.setYRot(b.getYRot() + 6);
            return c.z - 0.5 >= h.getDouble("z"); // its back is past the disc
        }).orElse(true);
        if (through || ticks >= tuning().suckTicks()) {
            exit(server, tardis);
        }
    }

    /**
     * Out the far side: the trip ends and the box comes out of a rift over the wormhole's aim in its dimension, still
     * falling and going forward as it went in, thrusters off, its pilot still riding ({@code Vortex.dropInto}); it
     * loses {@code exit_damage} health (never below 1) and lands with a crater of {@code exit_crater}. Says what
     * happened.
     */
    public static Flight.Outcome exit(MinecraftServer server, TardisRecord tardis) {
        CompoundTag trip = tardis.moduleData(Vortex.MODULE);
        if (!trip.contains("suck")) {
            return Flight.Outcome.fail("tardis_rb.vortex.not_in", "The TARDIS isn't in a wormhole");
        }
        CompoundTag h = trip.getCompound("suck");
        ResourceLocation id = ResourceLocation.tryParse(h.getString("dimension"));
        ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            level = Optional.ofNullable(server.getLevel(Vortex.from(trip).dimension())).orElse(server.overworld());
        }
        Direction facing = FreeFlight.facing(server, tardis);
        Optional<TardisBody> was = FreeFlight.body(server, tardis);
        Optional<ServerPlayer> pilot = was.map(b -> b.getFirstPassenger() instanceof ServerPlayer p ? p : null);
        Vec3 v = was.map(TardisBody::getDeltaMovement).orElse(new Vec3(0, -0.1, 0.4));
        Vortex.clearTrip(server, tardis, trip);
        Health.set(tardis, Math.max(Math.min(1, Health.get(tardis)), Health.get(tardis) - tuning().exitDamage()));
        // along the tunnel (+z) in, so out of the rift the same way, as fast and falling as fast
        Vortex.dropInto(server, tardis, level, BlockPos.of(h.getLong("aim")),
                new Vec3(0, Math.min(-0.05, v.y), Math.max(0.3, v.z)), tuning().exitCrater(), facing, pilot);
        return new Flight.Outcome(Optional.empty(), Optional.empty());
    }

    /** Removes a wormhole and its portal. */
    static void remove(ServerLevel vortex, CompoundTag h) {
        for (String key : new String[] {"id", "portal"}) {
            if (h.hasUUID(key)) {
                Entity e = vortex.getEntity(h.getUUID(key));
                if (e != null) {
                    e.discard();
                }
            }
        }
    }

    static void removeAll(ServerLevel vortex, CompoundTag trip) {
        for (Tag t : trip.getList("wormholes", Tag.TAG_COMPOUND)) {
            remove(vortex, (CompoundTag) t);
        }
        if (trip.contains("suck")) {
            remove(vortex, trip.getCompound("suck"));
        }
    }
}
