package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.flight.FreeFlight;
import com.porcupinestudios.tardisrb.repair.Repairs;
import com.porcupinestudios.tardisrb.vortex.Vortex;
import java.util.Optional;
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
                helper.assertTrue(Vortex.progress(t) > 0, "the autopilot didn't carry it along");

                Flight.Outcome landed = Vortex.arrive(server, t);
                helper.assertTrue(landed.done(), "it didn't land: " + landed.problem().map(c -> c.getString()).orElse(""));
                helper.assertTrue(!Vortex.inVortex(t) && !FreeFlight.isLoose(t) && body.isRemoved(),
                        "the trip didn't end");
                helper.assertTrue(doorway.isRemoved(), "the doorway portal was left in the vortex");
                BlockPos at = t.location().get().pos();
                helper.assertTrue(t.location().get().dimension().equals(helper.getLevel().dimension())
                        && Math.abs(at.getX() - target.getX()) <= 16 && Math.abs(at.getZ() - target.getZ()) <= 16,
                        "it landed at " + t.location().get() + ", not near " + target);
                helper.assertTrue(helper.getLevel().getBlockState(at).getBlock() instanceof ExteriorBlock,
                        "no exterior block where it landed");
                helper.succeed();
            });
        });
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
