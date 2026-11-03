package com.porcupinestudios.tardisrb.gametest;

import com.mojang.authlib.GameProfile;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.PartBlock;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.progression.Cost;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import com.porcupinestudios.tardisrb.repair.Parts;
import com.porcupinestudios.tardisrb.repair.Repairs;
import com.porcupinestudios.tardisrb.repair.WorldDamage;
import com.porcupinestudios.tardisrb.research.Researching;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** TARDIS phase 3: parts fixed by hand, timed research, the box's look, damage from explosions. */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RepairLoopTests {
    private static final String PLATFORM = "platform";
    private static final ResourceLocation NAVIGATION = TardisRB.id("navigation");
    private static final ResourceLocation HULL = TardisRB.id("hull");

    private RepairLoopTests() {}

    static ServerPlayer crew(GameTestHelper helper, String id) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(
                id.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "tardis_" + id));
        p.getInventory().clearContent();
        p.getAbilities().instabuild = false;
        return p;
    }

    static void give(ServerPlayer player, Cost cost) {
        for (Cost.ItemCost c : cost.items()) {
            ItemStack stack = c.ingredient().getItems()[0].copy();
            stack.setCount(c.count());
            player.getInventory().add(stack);
        }
    }

    static int look(GameTestHelper helper, TardisRecord t) {
        return helper.getLevel().getBlockState(t.location().get().pos()).getValue(ExteriorBlock.LOOK);
    }

    /**
     * Phase 3 done-when: fixing one part (navigation, by hand at its part block) and researching one node
     * (hop calibration, which takes time) raise the jump range, and the box's look changes.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 700)
    public static void fixingAPartAndResearchingANodeRaiseRangeAndChangeTheLook(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = crew(helper, "loop");
        TardisRecord t = WreckTests.onPlatform(helper, Optional.of(player));
        ResourceLocation repair = TardisRB.id("navigation_1");
        ResourceLocation node = TardisRB.id("hop_calibration");
        BlockPos part = helper.absolutePos(new BlockPos(6, 2, 2));
        Parts.place(helper.getLevel(), part, t, repair);
        helper.assertTrue(Unlocks.stat(t, "range") == 0, "the wreck already has range");
        helper.assertTrue(look(helper, t) == 0, "the wreck doesn't look wrecked");

        helper.assertFalse(Parts.use(helper.getLevel(), part, player).getString().contains("Fixed"),
                "fixed with empty hands");
        give(player, TardisContent.get().cost(TardisContent.repair(repair).orElseThrow().cost()));
        String fixed = Parts.use(helper.getLevel(), part, player).getString();
        helper.assertTrue(helper.getLevel().getBlockState(part).getValue(PartBlock.FIXED), "part still broken: " + fixed);
        double afterPart = Unlocks.stat(t, "range");
        helper.assertTrue(afterPart > 0, "fixing the part gave no range");
        helper.assertTrue(look(helper, t) > 0, "the box looks the same after the repair");

        give(player, TardisContent.get().cost(TardisContent.research(node).orElseThrow().cost()));
        Repairs.Outcome started = Researching.start(server, t, node, Optional.of(player));
        if (!started.done()) {
            helper.fail("research didn't start: " + started.problem().get().getString());
        }
        helper.assertTrue(Researching.state(server, t, node) == Researching.State.RUNNING, "research not running");
        helper.assertTrue(Unlocks.stat(t, "range") == afterPart, "range rose before the research finished");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(t.research().contains(node), "research not finished"))
                .thenExecute(() -> helper.assertTrue(Unlocks.stat(t, "range") > afterPart,
                        "research gave no range: " + Unlocks.stat(t, "range")))
                .thenSucceed();
    }

    @GameTest(template = PLATFORM)
    public static void anExplosionByTheBoxLowersTheHull(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        helper.assertTrue(Repairs.attempt(server, t, TardisRB.id("hull_1"), Optional.empty()).done(), "hull repair");
        helper.assertTrue(Unlocks.level(t, HULL) == 1, "hull not repaired");
        int patched = look(helper, t);
        helper.assertTrue(patched >= 2, "a patched hull doesn't change the look: " + patched);
        WorldDamage.explosion(helper.getLevel(), t.location().get().pos().getCenter().add(2, 0, 0));
        helper.assertTrue(Unlocks.level(t, HULL) == 0, "the explosion did no damage");
        helper.assertTrue(t.repairedLevel(HULL) == 1 && t.repairs().contains(TardisRB.id("hull_1")),
                "damage forgot the repair");
        helper.assertTrue(look(helper, t) < patched, "the box still looks patched");
        WorldDamage.explosion(helper.getLevel(), t.location().get().pos().getCenter().add(40, 0, 0));
        helper.assertTrue(Unlocks.level(t, NAVIGATION) == 0, "far explosion");
        helper.succeed();
    }

    /** Stage 4: colonist researchers (through the shared API) make running research finish sooner. */
    @GameTest(template = PLATFORM)
    public static void researchersSpeedUpResearch(GameTestHelper helper) {
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        long now = helper.getLevel().getServer().overworld().getGameTime();
        ResourceLocation node = TardisRB.id("hop_calibration");
        t.startResearch(new TardisRecord.ResearchJob(node, now + 200));
        helper.assertTrue(Researching.bonusTicks(0, 100) == 0, "no researchers still helped");
        long bonus = Researching.bonusTicks(2, 100);
        double speed = TardisContent.tuning().research().speedPerResearcher();
        helper.assertTrue(bonus == Math.round(100 * 2 * speed), "bonus " + bonus);
        helper.assertTrue(Researching.bonusTicks(99, 100) == Researching.bonusTicks(
                TardisContent.tuning().research().maxResearchers(), 100), "the researcher cap didn't hold");
        t.hurryResearch(bonus);
        helper.assertTrue(t.researching().get().finishAt() == now + 200 - bonus,
                "finish at " + t.researching().get().finishAt());
        helper.succeed();
    }
}
