package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.ai.AiData;
import com.porcupinestudios.lotrport.ai.AiReplay;
import com.porcupinestudios.lotrport.ai.AiRulers;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 10 done-when: two AI rulers found colonies and go to war, a colony changes hands, and the replay matches. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AiRulerTests {
    private static final ResourceLocation WEST = LotrPort.id("test_ai_west");
    private static final ResourceLocation EAST = LotrPort.id("test_ai_east");
    private static final long SEED = 20261103L;

    private AiRulerTests() {}

    @GameTest(template = "empty")
    public static void aiRulersDataLoads(GameTestHelper helper) {
        helper.assertTrue(AiData.loadProblems().isEmpty(), "ai data problems: " + AiData.loadProblems());
        helper.assertTrue(AiData.rulers().size() >= 2, "rulers: " + AiData.rulers().keySet());
        helper.succeed();
    }

    /** Aaron, 2026-11-03: no AI ruler starts before the first player colony, so the AI gets no head start. */
    @GameTest(template = "empty")
    public static void aiRulersWaitForTheFirstPlayerColony(GameTestHelper helper) {
        AiData.Settings s = AiData.Settings.DEFAULT;
        helper.assertTrue(s.waitForPlayer(), "the AI does not wait for the player by default");
        helper.assertTrue(AiRulers.waits(s, -1, false), "the AI started before any player colony");
        helper.assertTrue(!AiRulers.waits(s, -1, true), "the AI did not start after a player colony was founded");
        helper.assertTrue(!AiRulers.waits(s, 5, false), "the AI stopped when every player colony was lost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoAiRulersFoundColoniesGoToWarAndOneTakesAColony(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<ResourceLocation, AiData.RulerDef> defs = Map.of(
                WEST, new AiData.RulerDef("Test West", LotrPort.id("gondor"), LotrPort.id("minas_tirith"), 1.0, 1.0,
                        2, List.of("West One", "West Two"), 0xFFFFFF),
                EAST, new AiData.RulerDef("Test East", LotrPort.id("mordor"), LotrPort.id("minas_morgul"), 1.0, 1.0,
                        2, List.of("East One", "East Two"), 0x000000));
        AiRulers ai = AiRulers.get(server);
        Colonies colonies = Colonies.get(server);
        UUID west = AiRulers.uuid(WEST);
        UUID east = AiRulers.uuid(EAST);
        int logStart = HistoryLog.get(server).size();

        boolean foundedBoth = false;
        boolean wentToWar = false;
        boolean changedHands = false;
        long day = 1;
        for (; day <= 200 && !(foundedBoth && wentToWar && changedHands); day++) {
            ai.step(server, defs, day, SEED);
            foundedBoth |= !colonies.ownedBy(west).isEmpty() && !colonies.ownedBy(east).isEmpty();
            wentToWar |= ai.atWar(WEST, EAST);
            changedHands |= HistoryLog.get(server).entries().stream().skip(logStart)
                    .anyMatch(e -> e.type().equals(Colonies.TRANSFER_HISTORY));
        }
        helper.assertTrue(foundedBoth, "the rulers did not both found colonies");
        helper.assertTrue(wentToWar, "the rulers never went to war");
        helper.assertTrue(changedHands, "no colony changed hands in " + (day - 1) + " days");

        List<HistoryEntry> log = HistoryLog.get(server).entries();
        AiReplay.Result replay = AiReplay.replay(log);
        for (UUID ruler : List.of(west, east)) {
            for (Colony c : colonies.ownedBy(ruler)) {
                helper.assertTrue(ruler.equals(replay.owners().get(c.id())), "replay says " + c.name() + " belongs to "
                        + replay.owners().get(c.id()) + ", live owner " + ruler);
            }
        }
        replay.owners().forEach((colony, owner) -> {
            if (owner.equals(west) || owner.equals(east)) {
                helper.assertTrue(colonies.get(colony).map(c -> c.founder().equals(owner)).orElse(false),
                        "replay owner of " + colony + " doesn't match the live world");
            }
        });
        boolean replayWar = replay.wars().contains(AiReplay.pair(WEST.toString(), EAST.toString()));
        helper.assertTrue(replayWar == ai.atWar(WEST, EAST), "replayed war " + replayWar + ", live "
                + ai.atWar(WEST, EAST));
        helper.succeed();
    }
}
