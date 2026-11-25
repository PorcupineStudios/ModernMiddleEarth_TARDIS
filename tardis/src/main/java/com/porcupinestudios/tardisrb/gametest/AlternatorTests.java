package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.compat.create.AlternatorBlockEntity;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Artron Alternator (2026-11-19): set over the police box, it finds the box under it and its arms reach it. */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AlternatorTests {
    private AlternatorTests() {}

    @GameTest(template = "platform")
    public static void theAlternatorFindsTheBoxUnderIt(GameTestHelper helper) {
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        BlockPos box = t.location().get().pos(); // the box's lower half
        BlockPos at = box.above(3); // one block of air over the top half
        helper.getLevel().setBlockAndUpdate(at, TardisBlocks.ALTERNATOR.get().defaultBlockState());
        long before = t.charge();
        helper.runAfterDelay(5, () -> {
            if (!(helper.getLevel().getBlockEntity(at) instanceof AlternatorBlockEntity be)) {
                helper.fail("no alternator block entity");
                return;
            }
            helper.assertTrue(be.drop() == 2, "the alternator sees the box " + be.drop() + " blocks down");
            helper.assertTrue(t.charge() == before, "it charged without turning");
            helper.succeed();
        });
    }

    /** A cogwheel beside it, turned by a creative motor, meshes with its gear: it turns and fills the TARDIS. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void anAdjacentCogwheelTurnsItAndItCharges(GameTestHelper helper) {
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        t.useCharge(t.charge()); // empty, so there is room to fill
        BlockPos at = t.location().get().pos().above(3);
        BlockPos cog = at.east();
        var level = helper.getLevel();
        level.setBlockAndUpdate(at, TardisBlocks.ALTERNATOR.get().defaultBlockState());
        level.setBlockAndUpdate(cog, com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState());
        level.setBlockAndUpdate(cog.below(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING,
                        net.minecraft.core.Direction.UP));
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockEntity(at) instanceof AlternatorBlockEntity be && be.getSpeed() != 0,
                    "the alternator's gear isn't turning");
            helper.assertTrue(t.charge() > 0, "it turns but doesn't charge the TARDIS");
        });
    }
}
