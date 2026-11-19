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
}
