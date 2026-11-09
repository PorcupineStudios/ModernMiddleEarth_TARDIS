package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.core.TardisRecord;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Flying through the time vortex (Aaron 2026-11-09): setting off lifts the box into its lane in the vortex dimension;
 * debris it touches shatters and counts as a hit (costing charge); the end of the tunnel lands it near the target.
 */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VortexTests {
    private VortexTests() {}

    @GameTest(template = "platform", timeoutTicks = 200)
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

        // no pilot rides along to load the lane: keep its start loaded and wait until it is, then go
        ChunkPos chunk = new ChunkPos(BlockPos.containing((t.plot() + 1) * (double) Vortex.LANE_SPACING, Vortex.LANE_Y, 0));
        vortex.setChunkForced(chunk.x, chunk.z, true);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(vortex.areEntitiesLoaded(chunk.toLong()),
                "the vortex lane didn't load")).thenExecute(() -> {
            Flight.Outcome left = Vortex.depart(server, t, Optional.empty(), target, Optional.empty(), Optional.empty());
            helper.assertTrue(left.done(), "it didn't set off: " + left.problem().map(c -> c.getString()).orElse(""));
            helper.assertTrue(Vortex.inVortex(t), "no trip recorded");
            helper.assertTrue(!(helper.getLevel().getBlockState(base).getBlock() instanceof ExteriorBlock),
                    "the exterior block is still there");
            TardisBody body = FreeFlight.body(server, t).orElse(null);
            helper.assertTrue(body != null && body.level() == vortex && body.inLane(), "the box isn't in its vortex lane");

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
                vortex.setChunkForced(chunk.x, chunk.z, false);
                helper.assertTrue(landed.done(), "it didn't land: " + landed.problem().map(c -> c.getString()).orElse(""));
                helper.assertTrue(!Vortex.inVortex(t) && !FreeFlight.isLoose(t) && body.isRemoved(),
                        "the trip didn't end");
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
}
