package com.porcupinestudios.ringbearer.harness.gametest;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.Harnessing;
import com.porcupinestudios.ringbearer.harness.content.HitchBlock;
import com.porcupinestudios.ringbearer.harness.content.HitchBlockEntity;
import com.porcupinestudios.ringbearer.harness.content.Reins;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A team on the reins: two harnessed horses hitched to one hitch, the leader in front and its mate behind and to the
 * side, driven by keys set as the network sets them ({@link Reins#set}). The leader goes, turns and stops on the keys;
 * the mate keeps its place beside and behind it all the way. On a strip of ground near the world origin (as the tow
 * runs are), with the hitch on the ground (no vehicle: nothing holds the team back).
 */
@GameTestHolder(HarnessMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamTests {
    private static final AtomicInteger LANES = new AtomicInteger();

    private TeamTests() {}

    private static BlockPos lane(ServerLevel level) {
        BlockPos o = new BlockPos(512 + LANES.getAndIncrement() * 64, 0, 2048);
        for (int cx = (o.getX() - 32) >> 4; cx <= (o.getX() + 32) >> 4; cx++) {
            for (int cz = (o.getZ() - 32) >> 4; cz <= (o.getZ() + 48) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
            }
        }
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 40; z++) {
                level.setBlock(o.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 1; y <= 4; y++) {
                    level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return o;
    }

    private static Horse horse(ServerLevel level, double x, double y, double z) {
        Horse h = EntityType.HORSE.create(level);
        h.moveTo(x, y, z, 0, 0);
        h.setYBodyRot(0);
        h.setYHeadRot(0);
        h.setTamed(true);
        h.setPersistenceRequired();
        level.addFreshEntity(h);
        Harnessing.putOn(h, null);
        return h;
    }

    /** Where the mate belongs: the leader's place difference (2 behind, 1.5 to the left), turned with the leader. */
    private static Vec3 place(Horse lead) {
        double r = Math.toRadians(lead.yBodyRot);
        Vec3 f = new Vec3(-Math.sin(r), 0, Math.cos(r));
        Vec3 right = new Vec3(-f.z, 0, f.x);
        return lead.position().add(f.scale(-2)).add(right.scale(-1.5));
    }

    private static double off(Horse mate, Horse lead) {
        Vec3 p = place(lead);
        return Math.hypot(mate.getX() - p.x, mate.getZ() - p.z);
    }

    @GameTest(template = "arena", timeoutTicks = 220)
    public static void teamFollowsItsLeaderOnTheReins(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = lane(level);
        BlockPos at = o.above();
        level.setBlock(at, HarnessMod.HITCH.get().defaultBlockState().setValue(HitchBlock.FACING, Direction.SOUTH), 3);
        HitchBlockEntity hitch = (HitchBlockEntity) level.getBlockEntity(at);
        Horse lead = horse(level, o.getX() + 0.5, o.getY() + 1, o.getZ() + 4.5);
        Horse mate = horse(level, o.getX() + 2.0, o.getY() + 1, o.getZ() + 2.5);
        h.assertTrue(hitch.hitch(lead) && hitch.hitch(mate), "the pair wouldn't hitch");
        long t0 = h.getTick();
        double[] worst = {0};
        double z0 = lead.getZ();
        // go 3 s; from 1.5 s to 2.5 s also turn right; then nothing (it stops)
        h.onEachTick(() -> {
            long t = h.getTick() - t0;
            Reins.set(level, at, t < 60 ? 1 : 0, t >= 30 && t < 50 ? -1 : 0, false);
            if (t > 15 && t < 110) {
                worst[0] = Math.max(worst[0], off(mate, lead));
            }
        });
        h.runAtTickTime(t0 + 29, () -> h.assertTrue(lead.getZ() - z0 > 2.5,
                "the leader didn't go on the reins: moved " + (lead.getZ() - z0)));
        h.runAtTickTime(t0 + 110, () -> {
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(lead.yBodyRot)) > 20,
                    "the leader didn't turn on the reins: heading " + lead.yBodyRot);
            Vec3 v = lead.getDeltaMovement();
            h.assertTrue(Math.hypot(v.x, v.z) < 0.03, "the leader didn't stop when the keys did");
            h.assertTrue(worst[0] < 1.5, String.format("the mate fell out of its place by up to %.2f blocks",
                    worst[0]));
            h.assertTrue(off(mate, lead) < 0.8, String.format("the mate ended %.2f blocks off its place",
                    off(mate, lead)));
            lead.discard();
            mate.discard();
            h.succeed();
        });
    }
}
