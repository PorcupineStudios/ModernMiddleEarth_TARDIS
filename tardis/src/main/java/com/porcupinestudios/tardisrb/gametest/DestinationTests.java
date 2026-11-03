package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.flight.Destinations;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** TARDIS phase 5: destinations opened by milestones. */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DestinationTests {
    private static final ResourceLocation OVERWORLD = TardisRB.id("overworld");
    private static final ResourceLocation NETHER = TardisRB.id("the_nether");
    private static final ResourceLocation END = TardisRB.id("the_end");

    private DestinationTests() {}

    /**
     * Phase 5 done-when: one milestone opens the Overworld while later dimensions stay locked. Asked as for a world
     * that starts elsewhere (the start dimension is always open; here it is set to the interior dimension), then the
     * opened jump is flown for real.
     */
    @GameTest(template = "platform")
    public static void oneMilestoneOpensTheOverworldWhileLaterDimensionsStayLocked(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        ResourceKey<Level> start = Interiors.DIMENSION;
        for (ResourceLocation d : new ResourceLocation[] {OVERWORLD, NETHER, END}) {
            helper.assertTrue(Destinations.closed(server, t, d, Optional.empty(), start).isPresent(),
                    d + " is open before any milestone");
        }

        t.passMilestone(TardisRB.id("restored_core"));
        helper.assertTrue(Destinations.closed(server, t, OVERWORLD, Optional.empty(), start).isEmpty(),
                "the Overworld is still closed after the milestone");
        helper.assertTrue(Destinations.closed(server, t, NETHER, Optional.empty(), start).isPresent(),
                "the Nether opened with the first milestone");
        helper.assertTrue(Destinations.closed(server, t, END, Optional.empty(), start).isPresent(),
                "the End opened with the first milestone");

        for (String id : new String[] {"power_1", "power_2"}) {
            t.applyRepair(TardisRB.id(id), TardisContent.repair(TardisRB.id(id)).orElseThrow());
        }
        t.addCharge(1000);
        long before = t.charge();
        long cost = TardisContent.destination(OVERWORLD).orElseThrow().charge();
        BlockPos target = helper.absolutePos(new BlockPos(11, 2, 2));
        Flight.Outcome out = Flight.jump(server, t, OVERWORLD, Optional.of(target), Optional.empty());
        helper.assertTrue(out.done(), "jump: " + out.problem().map(c -> c.getString()).orElse(""));
        helper.assertTrue(out.landed().get().pos().equals(target), "landed at " + out.landed().get().pos()
                + ", aimed at " + target);
        helper.assertTrue(t.charge() == before - cost, "charge " + t.charge() + ", expected " + (before - cost));
        helper.assertFalse(Flight.jump(server, t, NETHER, Optional.empty(), Optional.empty()).done(),
                "jumped to the Nether");
        helper.succeed();
    }
}
