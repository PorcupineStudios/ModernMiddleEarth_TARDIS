package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.flight.FreeFlight;
import com.porcupinestudios.tardisrb.progression.Health;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.repair.Repairs;
import com.porcupinestudios.tardisrb.vortex.Vortex;
import com.porcupinestudios.tardisrb.vortex.Wormholes;
import com.porcupinestudios.tardisrb.progression.WormholeExit;
import com.porcupinestudios.tardisrb.entity.TardisEntities;
import com.porcupinestudios.tardisrb.entity.Wormhole;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Flying through the time vortex (Aaron 2026-11-09): setting off lifts the box into its lane in the vortex dimension;
 * a lightning arc across the tunnel it touches is a hit (costing charge and health), and stays; the end of the tunnel lands it near the target.
 */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VortexTests {
    private VortexTests() {}

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void departHitDebrisAndArrive(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel vortex = server.getLevel(Vortex.DIMENSION);
        helper.assertTrue(vortex != null, "the time vortex dimension isn't loaded");
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        BlockPos base = t.location().get().pos();
        helper.assertTrue(Repairs.attempt(server, t, TardisRB.id("navigation_1"), Optional.empty()).done(),
                "navigation repair");
        t.addCharge(1000);
        BlockPos target = new BlockPos(base.getX() + 10, helper.getLevel().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX() + 10, base.getZ()), base.getZ());

        // nobody is in the vortex to load the lane: the trip has to keep the box's own chunks loaded
        Flight.Outcome left = Vortex.depart(server, t, Optional.empty(), target, Optional.empty(), Optional.empty());
        noWormholes(t);
        helper.assertTrue(left.done(), "it didn't set off: " + left.problem().map(c -> c.getString()).orElse(""));
        helper.assertTrue(Vortex.inVortex(t), "no trip recorded");
        helper.assertTrue(!(helper.getLevel().getBlockState(base).getBlock() instanceof ExteriorBlock),
                "the exterior block is still there");
        helper.startSequence().thenIdle(100).thenExecute(() -> helper.assertTrue(FreeFlight.body(server, t)
                .filter(b -> vortex.isPositionEntityTicking(b.blockPosition())).isPresent(),
                "the box in the vortex isn't kept loaded: " + FreeFlight.body(server, t).map(b -> b.position() + " " + b.level().dimension()) + " at " + t.location() + " trip " + t.moduleData(Vortex.MODULE) + " plot " + t.plot()))
                .thenExecute(() -> {
            TardisBody body = FreeFlight.body(server, t).orElse(null);
            helper.assertTrue(body != null && body.level() == vortex && body.inLane(), "the box isn't in its vortex lane");

            // the doors open in flight; landing has to take the doorway out of the vortex with the box
            helper.assertTrue(Doors.open(server, t), "the doors didn't open in the vortex");
            Entity doorway = vortex.getEntity(t.moduleData(Doors.MODULE).getUUID("outside"));
            helper.assertTrue(doorway instanceof Portal, "no doorway portal in the vortex");
            long charge = t.charge();
            // a lightning arc straight across its path, level with its middle
            var trip = t.moduleData(Vortex.MODULE);
            double z = body.getZ() + 3;
            double offset = body.getY() + 1 - Vortex.LANE_Y - Vortex.bend(z).y;
            var arc = Vortex.placeArc(vortex, trip, z, 0, offset);
            t.setModuleData(Vortex.MODULE, trip);
            double axisX = trip.getDouble("lane_x") + Vortex.bend(z).x;
            helper.assertTrue(arc != null && Math.abs(arc.getX() + arc.half().x - axisX) >= TardisContent.tuning().vortex().radius()
                    && Math.abs(arc.getX() - arc.half().x - axisX) >= TardisContent.tuning().vortex().radius(),
                    "the arc doesn't reach the tunnel wall at both ends");
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(Vortex.hits(t) >= 1, "flying into the lightning arc wasn't a hit");
                helper.assertTrue(!arc.isRemoved(), "the arc went when it was hit: it should stay put");
                helper.assertTrue(t.charge() < charge, "the hit cost no charge");
                helper.assertTrue(Health.get(t) < Health.max(t)
                        && Vortex.inaccuracy(t) > 0, "the hit didn't hurt it or put it off course: health "
                        + Health.get(t) + ", inaccuracy " + Vortex.inaccuracy(t));
                helper.assertTrue(Vortex.progress(t) > 0, "the autopilot didn't carry it along");

                double off = Vortex.inaccuracy(t) + 8 + 2; // its inaccuracy, then the landing search's radius
                double health = Health.get(t);
                Flight.Outcome landed = Vortex.arrive(server, t);
                helper.assertTrue(landed.done(), "it didn't land: " + landed.problem().map(c -> c.getString()).orElse(""));
                helper.assertTrue(!Vortex.inVortex(t) && !FreeFlight.isLoose(t) && body.isRemoved(),
                        "the trip didn't end");
                helper.assertTrue(doorway.isRemoved(), "the doorway portal was left in the vortex");
                BlockPos at = t.location().get().pos();
                helper.assertTrue(t.location().get().dimension().equals(helper.getLevel().dimension())
                        && Math.abs(at.getX() - target.getX()) <= off && Math.abs(at.getZ() - target.getZ()) <= off,
                        "it landed at " + t.location().get() + ", not within " + off + " of " + target + " (health " + health + ")");
                helper.assertTrue(helper.getLevel().getBlockState(at).getBlock() instanceof ExteriorBlock,
                        "no exterior block where it landed");
                // the damage lasts: still drained after landing and at the start of the next trip
                helper.assertTrue(Health.get(t) == health, "landing healed it: " + Health.get(t) + ", was " + health);
                helper.assertTrue(Vortex.depart(server, t, Optional.empty(), base, Optional.empty(), Optional.empty())
                        .done() && Health.get(t) == health, "the next trip didn't start from the same health");
                noWormholes(t);
                Vortex.arrive(server, t);
                helper.succeed();
            });
        });
    }

    /**
     * Out of health it crash lands (Aaron 2026-11-09): it falls out of the tunnel and comes down, thrusters off, near
     * the point of its course it had reached (not the target), leaving a crater, and lies there as a loose box.
     */
    @GameTest(template = "platform", timeoutTicks = 600, skyAccess = true) // no barrier roof for it to land on
    public static void outOfHealthItCrashLandsPartWay(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel vortex = server.getLevel(Vortex.DIMENSION);
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        BlockPos base = t.location().get().pos();
        helper.assertTrue(Repairs.attempt(server, t, TardisRB.id("navigation_1"), Optional.empty()).done(),
                "navigation repair");
        t.addCharge(1000);
        BlockPos target = new BlockPos(base.getX() + 4, base.getY(), base.getZ());
        helper.assertTrue(Vortex.depart(server, t, Optional.empty(), target, Optional.empty(), Optional.empty()).done(),
                "it didn't set off");
        noWormholes(t);
        double[] crashAt = new double[1];
        helper.startSequence().thenIdle(60).thenExecute(() -> {
            Health.set(t, 0); // worn out mid-trip (as by /tardis health set 0): it goes down at once
        }).thenWaitUntil(() -> {
            helper.assertTrue(Vortex.crashing(t), "it didn't start to crash: " + t.moduleData(Vortex.MODULE) + " body " + FreeFlight.body(server, t).map(b -> b.position()));
            crashAt[0] = t.moduleData(Vortex.MODULE).getDouble("crash");
            // the landing is random within the inaccuracy: take it away so the box comes down on this test's platform
            var trip = t.moduleData(Vortex.MODULE);
            trip.putDouble("inaccuracy", 0);
            t.setModuleData(Vortex.MODULE, trip);
        }).thenWaitUntil(() -> helper.assertTrue(!Vortex.inVortex(t), "still falling out of the vortex"))
                .thenExecute(() -> FreeFlight.body(server, t).ifPresent(b -> {
                    helper.assertTrue(b.crashing() && !b.thrusters() && b.getY() > base.getY() + 10,
                            "it didn't appear falling high over the spot, thrusters off: " + b.position());
                    b.setDeltaMovement(0, b.getDeltaMovement().y, 0); // straight down, onto the platform
                }))
                .thenWaitUntil(() -> helper.assertTrue(FreeFlight.body(server, t).filter(b -> b.impactAt != null)
                        .isPresent(), "it hasn't hit the ground yet"))
                .thenExecute(() -> {
            TardisBody body = FreeFlight.body(server, t).orElseThrow();
            helper.assertTrue(body.level() == helper.getLevel() && !body.thrusters() && !body.crashing(),
                    "it isn't lying in the world it left as a loose box");
            double along = base.getX() + 0.5 + 4 * crashAt[0];
            helper.assertTrue(crashAt[0] > 0.05 && crashAt[0] < 0.95, "it didn't crash part way: " + crashAt[0]);
            helper.assertTrue(Math.abs(body.getX() - along) < 1.5 && Math.abs(body.getZ() - base.getZ() - 0.5) < 1.5,
                    "it came down at " + body.position() + ", not on its course at x " + along);
            helper.assertTrue(helper.getLevel().getBlockState(body.impactAt).isAir(),
                    "no crater where it hit: " + helper.getLevel().getBlockState(body.impactAt));
            helper.assertTrue(Health.get(t) == TardisContent.tuning().vortex().damage().afterCrash(),
                    "after the crash it should limp on with after_crash health, not " + Health.get(t));
            helper.succeed();
        });
    }

    /**
     * The TARDIS's health lasts (Aaron 2026-11-09): repairing the hull raises the most it can have and fills it up;
     * /tardis health damage, repair and set change it.
     */
    @GameTest(template = "platform")
    public static void hullRepairsAndTheHealthCommand(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "tardis_health_test")); // new each run: the test world keeps old TARDISes
        TardisRecord t = WreckTests.onPlatform(helper, Optional.of(player));
        t.addCharge(1000);
        double was = Health.max(t);
        Health.set(t, 5);
        var fixed = Repairs.attempt(server, t, TardisRB.id("hull_1"), Optional.empty());
        helper.assertTrue(fixed.done(), "hull repair: " + fixed.problem().map(c -> c.getString()).orElse(""));
        helper.assertTrue(Health.max(t) > was && Health.get(t) == Health.max(t),
                "the hull repair didn't raise and fill its health: " + Health.get(t) + "/" + Health.max(t));
        var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        run(helper, source, "tardis health damage 7");
        helper.assertTrue(Health.get(t) == Health.max(t) - 7, "damage 7 left " + Health.get(t));
        run(helper, source, "tardis health repair 3");
        helper.assertTrue(Health.get(t) == Health.max(t) - 4, "repair 3 left " + Health.get(t));
        run(helper, source, "tardis health set 2");
        helper.assertTrue(Health.get(t) == 2, "set 2 left " + Health.get(t));
        run(helper, source, "tardis health repair");
        helper.assertTrue(Health.get(t) == Health.max(t), "repair didn't fill it: " + Health.get(t));
        helper.succeed();
    }

    private static void run(GameTestHelper helper, net.minecraft.commands.CommandSourceStack source, String command) {
        try {
            source.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (Exception e) {
            helper.fail("/" + command + " failed: " + e.getMessage());
        }
    }

    /**
     * The course line runs from where it left to the target, across dimensions in the target's coordinates (the
     * Nether is 8 to 1); before halfway a point on it is in the dimension it left, after it in the target's.
     */
    @GameTest(template = "platform")
    public static void theCourseCrossesDimensions(GameTestHelper helper) {
        var hop = Vortex.course(new net.minecraft.world.phys.Vec3(0, 64, 0), 1,
                new net.minecraft.world.phys.Vec3(100, 80, 0), 1, 0.4);
        helper.assertTrue(Math.abs(hop.pos().x - 40) < 1.0E-9 && Math.abs(hop.pos().y - 70.4) < 1.0E-9,
                "40% of a hop is at " + hop.pos());
        var early = Vortex.course(new net.minecraft.world.phys.Vec3(800, 64, 0), 1,
                new net.minecraft.world.phys.Vec3(200, 64, 0), 8, 0.25);
        helper.assertTrue(!early.inTarget() && Math.abs(early.pos().x - 1000) < 1.0E-9,
                "a quarter of the way to the Nether should be back home at x 1000: " + early);
        var late = Vortex.course(new net.minecraft.world.phys.Vec3(800, 64, 0), 1,
                new net.minecraft.world.phys.Vec3(200, 64, 0), 8, 0.75);
        helper.assertTrue(late.inTarget() && Math.abs(late.pos().x - 175) < 1.0E-9,
                "three quarters of the way should be in the Nether at x 175: " + late);
        helper.succeed();
    }

    /** The tube is solid: a point past the wall goes back onto it along the same line from the axis; inside stays. */
    @GameTest(template = "platform")
    public static void theTubeWallPushesBack(GameTestHelper helper) {
        double lane = Vortex.laneX(3 * Vortex.LANE_SPACING + 40);
        helper.assertTrue(lane == 3 * Vortex.LANE_SPACING + 0.5, "wrong lane axis " + lane);
        // the axis winds: measure from where it is at z 70
        var bend = Vortex.bend(70);
        double ax = lane + bend.x;
        double ay = Vortex.LANE_Y + bend.y;
        helper.assertTrue(Vortex.keepInside(ax + 3, ay - 4, 70, 10) == null, "a point inside was moved");
        var back = Vortex.keepInside(ax + 12, ay - 16, 70, 10);
        helper.assertTrue(back != null && Math.abs(back.x - (ax + 6)) < 1.0E-9 && Math.abs(back.y - (ay - 8)) < 1.0E-9
                && back.z == 70, "pushed back to " + back + ", not onto the wall");
        helper.succeed();
    }

    /** The tunnel twists and turns, but gently enough to fly: straight at the start, bends everywhere else. */
    @GameTest(template = "platform")
    public static void theTunnelBendsGently(GameTestHelper helper) {
        helper.assertTrue(Vortex.bend(0).equals(net.minecraft.world.phys.Vec3.ZERO), "the start isn't straight");
        double most = 0;
        double steepest = 0;
        for (int z = 0; z < 1200; z++) {
            var a = Vortex.bend(z);
            var b = Vortex.bend(z + 1);
            most = Math.max(most, Math.hypot(a.x, a.y));
            steepest = Math.max(steepest, Math.hypot(b.x - a.x, b.y - a.y));
        }
        helper.assertTrue(most > 6, "the tunnel hardly bends: " + most);
        helper.assertTrue(steepest < 0.5, "a bend is too sharp to fly: " + steepest + " sideways per block");
        helper.succeed();
    }

    /** Stops wormholes turning up on a trip (for tests about something else). */
    static void noWormholes(TardisRecord t) {
        var trip = t.moduleData(Vortex.MODULE);
        trip.putInt("next_wormhole", Integer.MAX_VALUE);
        t.setModuleData(Vortex.MODULE, trip);
    }

    /** Raises a subsystem to {@code level} through its repairs, past the start cap (as the restored-core milestone). */
    private static void upgrade(TardisRecord t, String subsystem, int level) {
        t.passMilestone(TardisRB.id("restored_core"));
        for (int i = 1; i <= level; i++) {
            var id = TardisRB.id(subsystem + "_" + i);
            t.applyRepair(id, TardisContent.repair(id).orElseThrow());
        }
    }

    /**
     * Wormholes vary (Aaron 2026-11-10): sizes stay within the tuned range and never get too small for the box, bigger
     * ones pull harder, and a face turns at most 90 degrees, never away from the oncoming box nor toward the nearer
     * tunnel wall.
     */
    @GameTest(template = "platform")
    public static void wormholesVaryInSizeAndTurnInward(GameTestHelper helper) {
        var shape = TardisContent.tuning().vortex().wormholes().shape();
        double min = Math.max(Wormholes.smallest(), shape.minRadius());
        helper.assertTrue(Wormholes.smallest() > Math.hypot(1, 2) / 2, "the smallest wormhole doesn't fit the box");
        RandomSource random = RandomSource.create(42);
        double least = Double.MAX_VALUE;
        double most = 0;
        for (int i = 0; i < 2000; i++) {
            double size = Wormholes.size(random);
            helper.assertTrue(size >= min - 1.0E-9 && size <= Math.max(min, shape.maxRadius()) + 1.0E-9,
                    "size " + size + " outside " + min + ".." + shape.maxRadius());
            least = Math.min(least, size);
            most = Math.max(most, size);
            for (double off : new double[] {-9, -3, -1, -0.5, 0, 0.5, 1, 3, 9}) {
                double yaw = Wormholes.turn(off, shape.maxYaw(), random);
                double pitch = Wormholes.turn(off, shape.maxPitch(), random);
                helper.assertTrue(Math.abs(yaw) <= shape.maxYaw() && Math.abs(pitch) <= shape.maxPitch(),
                        "turned too far: " + yaw + ", " + pitch);
                Vec3 n = Wormhole.facing(yaw, pitch);
                helper.assertTrue(n.z <= 1.0E-9, "it faces away from the box: " + n);
                // off to + x (or y) it never faces + x (or y): toward its nearer wall
                helper.assertTrue(Math.abs(off) < 1 || (off * n.x <= 1.0E-9 && off * Math.sin(Math.toRadians(pitch))
                        <= 1.0E-9), "at " + off + " it faces its wall: " + n);
            }
        }
        helper.assertTrue(most - least > (shape.maxRadius() - min) * 0.8, "sizes don't vary: " + least + ".." + most);
        helper.assertTrue(Wormholes.strength(least) < Wormholes.strength(most), "bigger ones don't pull harder");
        helper.succeed();
    }

    /** Wormhole exits are picked by weight from data, with the "from" weights for adjacent dimensions. */
    @GameTest(template = "platform")
    public static void wormholeExitsArePickedByWeight(GameTestHelper helper) {
        helper.assertTrue(TardisContent.get().wormholes().size() >= 3, "the wormhole files didn't load: "
                + TardisContent.get().wormholes().keySet());
        var ow = Level.OVERWORLD;
        var nether = Level.NETHER;
        var end = Level.END;
        var exits = List.of(new WormholeExit(ow, 10, Map.of()), new WormholeExit(nether, 3, Map.of(nether.location(), 8.0)),
                new WormholeExit(end, 1, Map.of()));
        var random = RandomSource.create(42);
        int n = 20000;
        int[] fromOverworld = new int[3];
        int[] fromNether = new int[3];
        for (int i = 0; i < n; i++) {
            var a = Wormholes.pick(exits, ow.location(), ow.location(), d -> true, random).orElseThrow();
            fromOverworld[a == ow ? 0 : a == nether ? 1 : 2]++;
            var b = Wormholes.pick(exits, nether.location(), ow.location(), d -> true, random).orElseThrow();
            fromNether[b == ow ? 0 : b == nether ? 1 : 2]++;
            helper.assertTrue(Wormholes.pick(exits, ow.location(), ow.location(), d -> d != end, random)
                    .filter(d -> d == end).isEmpty(), "picked a dimension that isn't allowed");
        }
        near(helper, fromOverworld[0] / (double) n, 10 / 14.0, "overworld from the overworld");
        near(helper, fromOverworld[1] / (double) n, 3 / 14.0, "nether from the overworld");
        near(helper, fromOverworld[2] / (double) n, 1 / 14.0, "end from the overworld");
        near(helper, fromNether[1] / (double) n, 8 / 19.0, "nether from the nether (its from weight)");
        helper.assertTrue(Wormholes.pick(exits, ow.location(), ow.location(), d -> false, random).isEmpty(),
                "picked something with nothing allowed");
        helper.succeed();
    }

    private static void near(GameTestHelper helper, double got, double want, String what) {
        helper.assertTrue(Math.abs(got - want) < 0.02, what + ": " + got + ", expected about " + want);
    }

    /** The stabilizers weaken the wormholes' pull: level 3 leaves a fifth of it. */
    @GameTest(template = "platform")
    public static void stabilizersWeakenTheWormholePull(GameTestHelper helper) {
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        double base = Wormholes.pull(t);
        helper.assertTrue(base == TardisContent.tuning().vortex().wormholes().pull(), "level 0 pull " + base);
        upgrade(t, "stabilizers", 1);
        double one = Wormholes.pull(t);
        upgrade(t, "stabilizers", 3);
        double three = Wormholes.pull(t);
        helper.assertTrue(one < base && three < one && Math.abs(three - base * 0.2) < 1.0E-9,
                "the pull didn't drop with the stabilizers: " + base + ", " + one + ", " + three);
        // and a wormhole uses it: pulling toward its middle, stronger nearer, nothing out of range
        Wormhole hole = new Wormhole(TardisEntities.WORMHOLE.get(), helper.getLevel());
        hole.setPos(0, 0, 0);
        hole.setup(4, base, 28);
        Vec3 near = hole.pullOn(new Vec3(3, 0, -4));
        Vec3 far = hole.pullOn(new Vec3(3, 0, -20));
        helper.assertTrue(near.x < 0 && far.x < 0 && near.x < far.x
                && hole.pullOn(new Vec3(3, 0, -40)).equals(Vec3.ZERO),
                "the pull isn't toward the middle and stronger nearer: " + near + " " + far);
        helper.succeed();
    }

    /** Flying into a wormhole sucks the box in and throws it out in the wormhole's dimension, ending the trip. */
    @GameTest(template = "platform", timeoutTicks = 600)
    public static void aWormholeSucksTheBoxIntoItsDimension(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel vortex = server.getLevel(Vortex.DIMENSION);
        var dim = server.getLevel(Level.END) != null ? Level.END : Level.OVERWORLD;
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        BlockPos base = t.location().get().pos();
        helper.assertTrue(Repairs.attempt(server, t, TardisRB.id("navigation_1"), Optional.empty()).done(),
                "navigation repair");
        t.addCharge(1000);
        helper.assertTrue(Vortex.depart(server, t, Optional.empty(), base.east(20), Optional.empty(), Optional.empty())
                .done(), "it didn't set off");
        noWormholes(t);
        double laneX = t.moduleData(Vortex.MODULE).getDouble("lane_x");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(FreeFlight.body(server, t).isPresent(),
                "no box in the vortex")).thenExecute(() -> {
            TardisBody body = FreeFlight.body(server, t).orElseThrow();
            var trip = t.moduleData(Vortex.MODULE);
            // off to the side and turned 60 degrees toward the middle: the box flies into its face
            Wormhole hole = Wormholes.place(server, vortex, t, trip, body.getZ() + 20, 4, 0, dim, 5, -60, 0);
            t.setModuleData(Vortex.MODULE, trip);
            helper.assertTrue(hole != null && !hole.open(), "the wormhole wasn't placed, or is see-through at scanner 0");
        }).thenWaitUntil(() -> helper.assertTrue(!Vortex.inVortex(t), "still in the vortex")).thenExecute(() -> {
            helper.assertTrue(t.location().get().dimension().equals(dim), "it came out in "
                    + t.location().get().dimension().location() + ", not " + dim.location());
        }).thenWaitUntil(() -> {
            // the spot it was thrown out over loads a tick later
            TardisBody out = FreeFlight.body(server, t).orElse(null);
            helper.assertTrue(out != null && out.level().dimension().equals(dim) && !out.thrusters(),
                    "no loose box thrown out in " + dim.location() + " at " + t.location() + " plot " + t.plot() + " flight " + t.moduleData(FreeFlight.MODULE));
            // it comes out falling and still going forward, then hits the ground
            helper.assertTrue(out.crashing() && out.getDeltaMovement().z > 0.05, "it didn't come out moving: "
                    + out.getDeltaMovement());
            helper.assertTrue(vortex.getEntitiesOfClass(Wormhole.class, new AABB(laneX - 64, 0, -10, laneX + 64, 256, 600))
                    .isEmpty(), "a wormhole was left behind in the vortex");
        }).thenWaitUntil(() -> helper.assertTrue(FreeFlight.body(server, t).filter(b -> b.impactAt != null).isPresent(),
                "it hasn't fallen to the ground")).thenExecute(() -> {
            helper.succeed();
        });
    }

    /** With the scanner at level 2 a wormhole is see-through: an Immersive Portals portal fills it, gone after. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void theScannerMakesWormholesSeeThrough(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel vortex = server.getLevel(Vortex.DIMENSION);
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        BlockPos base = t.location().get().pos();
        helper.assertTrue(Repairs.attempt(server, t, TardisRB.id("navigation_1"), Optional.empty()).done(),
                "navigation repair");
        t.addCharge(1000);
        upgrade(t, "scanner", 2);
        helper.assertTrue(Vortex.depart(server, t, Optional.empty(), base.east(20), Optional.empty(), Optional.empty())
                .done(), "it didn't set off");
        noWormholes(t);
        // wait for the box to catch up with the trip: the autopilot moves progress on while the box is being made,
        // and until the next step pulls the box forward a wormhole 10 ahead of the box can be behind progress (not
        // "ahead") and 10 ahead of progress outside the chunks loaded round the box (no portal). CI failed both ways.
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(FreeFlight.body(server, t)
                .filter(b -> b.getZ() >= t.moduleData(Vortex.MODULE).getDouble("progress")).isPresent(),
                "no box in the vortex level with its trip")).thenExecute(() -> {
            TardisBody body = FreeFlight.body(server, t).orElseThrow();
            var trip = t.moduleData(Vortex.MODULE);
            // off to the side, so the autopilot flies past it
            Wormhole hole = Wormholes.place(server, vortex, t, trip, body.getZ() + 10, 8, 0, Level.OVERWORLD);
            t.setModuleData(Vortex.MODULE, trip);
            // ours by id (not just the first in the list)
            var h = new net.minecraft.nbt.CompoundTag();
            for (Tag x : trip.getList("wormholes", Tag.TAG_COMPOUND)) {
                if (hole != null && ((net.minecraft.nbt.CompoundTag) x).hasUUID("id")
                        && ((net.minecraft.nbt.CompoundTag) x).getUUID("id").equals(hole.getUUID())) {
                    h = (net.minecraft.nbt.CompoundTag) x;
                }
            }
            Entity found = h.hasUUID("portal") ? vortex.getEntity(h.getUUID("portal")) : null;
            helper.assertTrue(hole != null && hole.open() && found instanceof Portal p && !p.isTeleportable()
                    && Wormholes.PORTAL_TAG.equals(p.portalTag), "no see-through portal in the wormhole (hole "
                    + (hole == null ? "null" : "open " + hole.open() + " at " + hole.position()) + ", portal id "
                    + h.hasUUID("portal") + ", entity " + found + ", sight " + com.porcupinestudios.tardisrb.progression
                    .Unlocks.stat(t, "wormhole_sight") + ", body at " + body.position() + ", progress "
                    + trip.getDouble("progress") + ", holes " + trip.getList("wormholes", Tag.TAG_COMPOUND).size()
                    + ")");
            helper.assertTrue(Wormholes.next(t).isPresent(), "the scanner doesn't see the wormhole ahead");
            Entity portal = vortex.getEntity(h.getUUID("portal"));
            Vortex.arrive(server, t);
            helper.assertTrue(portal.isRemoved() && hole.isRemoved(), "the wormhole or its portal outlived the trip");
            helper.succeed();
        });
    }
}
