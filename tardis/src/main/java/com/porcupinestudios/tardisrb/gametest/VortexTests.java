package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.flight.FreeFlight;
import com.porcupinestudios.tardisrb.progression.Health;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.repair.Repairs;
import com.porcupinestudios.tardisrb.vortex.Vortex;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Flying through the time vortex (Aaron 2026-11-09): setting off lifts the box into its lane in the vortex dimension;
 * debris it touches shatters and counts as a hit (costing charge); the end of the tunnel lands it near the target.
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
        helper.assertTrue(left.done(), "it didn't set off: " + left.problem().map(c -> c.getString()).orElse(""));
        helper.assertTrue(Vortex.inVortex(t), "no trip recorded");
        helper.assertTrue(!(helper.getLevel().getBlockState(base).getBlock() instanceof ExteriorBlock),
                "the exterior block is still there");
        helper.startSequence().thenIdle(100).thenExecute(() -> helper.assertTrue(FreeFlight.body(server, t)
                .filter(b -> vortex.isPositionEntityTicking(b.blockPosition())).isPresent(),
                "the box in the vortex isn't kept loaded"))
                .thenExecute(() -> {
            TardisBody body = FreeFlight.body(server, t).orElse(null);
            helper.assertTrue(body != null && body.level() == vortex && body.inLane(), "the box isn't in its vortex lane");

            // the doors open in flight; landing has to take the doorway out of the vortex with the box
            helper.assertTrue(Doors.open(server, t), "the doors didn't open in the vortex");
            Entity doorway = vortex.getEntity(t.moduleData(Doors.MODULE).getUUID("outside"));
            helper.assertTrue(doorway instanceof Portal, "no doorway portal in the vortex");
            long charge = t.charge();
            BlockPos ahead = body.blockPosition().south();
            vortex.setBlock(ahead, TardisBlocks.VORTEX_DEBRIS.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(Vortex.hits(t) >= 1, "flying into debris wasn't a hit");
                helper.assertTrue(!vortex.getBlockState(ahead).is(TardisBlocks.VORTEX_DEBRIS.get()),
                        "the debris it hit didn't shatter");
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
}
