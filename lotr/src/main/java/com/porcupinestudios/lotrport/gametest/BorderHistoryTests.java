package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.border.BorderHistory;
import com.porcupinestudios.lotrport.border.BorderNet;
import com.porcupinestudios.lotrport.border.Borders;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.ringbearer.api.history.HistoryRewindEvent;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Borders through time (time travel): map changes are layer deltas in the history log, the map of a past tick is
 * replayed from them, a rewound world puts its borders back, and the map payload for the past carries that tick.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BorderHistoryTests {
    private static final ResourceLocation HOBBIT = LotrPort.id("hobbit");
    private static final ResourceLocation MORDOR = LotrPort.id("mordor");

    private BorderHistoryTests() {}

    private static int shirePixel(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        for (int i = 0; i < map.biomes().size(); i++) {
            if (map.biomes().get(i).id().equals("lotr_port:shire")) {
                List<int[]> pixels = map.pixelCentres(i, 2000);
                int[] p = pixels.get(helper.getLevel().getRandom().nextInt(pixels.size()));
                return Borders.pixelOf(p[0], p[1]);
            }
        }
        throw new IllegalStateException("no Shire on the map");
    }

    @GameTest(template = "empty", batch = "border_history")
    public static void theMapOfAPastTickIsReplayedAndRewound(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Borders borders = Borders.get(server);
        int pixel = shirePixel(helper);
        long now = server.overworld().getGameTime();
        helper.assertTrue(borders.owner(pixel).equals(Optional.of(HOBBIT)), "the Shire pixel isn't the hobbits'");
        borders.take(server, pixel, MORDOR, "test");
        try {
            helper.assertTrue(BorderHistory.at(server, now).owner(pixel).equals(Optional.of(MORDOR)),
                    "the replay of now misses the change");
            helper.assertTrue(BorderHistory.at(server, now - 1).owner(pixel).equals(Optional.of(HOBBIT)),
                    "the replay of the tick before shows the change");
            helper.assertTrue(BorderHistory.at(server, now).changes().equals(borders.changes()),
                    "replaying now doesn't give the live borders");

            BorderNet.Snapshot past = BorderNet.snapshot(server, OptionalLong.of(now - 1));
            helper.assertTrue(past.time() == now - 1, "the past map doesn't say its tick");
            helper.assertTrue(!past.pixels().contains(pixel), "the past map shows a later conquest");
            BorderNet.Snapshot present = BorderNet.snapshot(server, OptionalLong.empty());
            helper.assertTrue(present.time() == -1 && present.pixels().contains(pixel), "the present map is wrong");

            NeoForge.EVENT_BUS.post(new HistoryRewindEvent(server, now - 1));
            helper.assertTrue(borders.owner(pixel).equals(Optional.of(HOBBIT)), "a rewind didn't roll the map back");
            helper.assertTrue(BorderHistory.at(server, server.overworld().getGameTime()).owner(pixel)
                    .equals(Optional.of(HOBBIT)), "after a rewind the replay still shows the undone change");
        } finally {
            borders.take(server, pixel, HOBBIT, "test");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "border_history")
    public static void pastColoniesComeFromTheirHistory(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long now = server.overworld().getGameTime();
        int far = 300_000 + helper.getLevel().getRandom().nextInt(300_000);
        Colony colony = Colonies.get(server).foundAi(server, UUID.randomUUID(), UUID.randomUUID(), "History Test Hold",
                Borders.dimension(server), new BlockPos(far, 64, -far), MORDOR, 0).orElseThrow();
        try {
            helper.assertTrue(BorderHistory.coloniesAt(server, now).stream().anyMatch(c -> c.id().equals(colony.id())),
                    "a colony founded now is missing from now's colonies");
            helper.assertTrue(BorderHistory.coloniesAt(server, now - 1).stream()
                    .noneMatch(c -> c.id().equals(colony.id())), "a colony shows before it was founded");
        } finally {
            Colonies.get(server).disband(server, colony, "test");
        }
        helper.assertTrue(BorderHistory.coloniesAt(server, now).stream().noneMatch(c -> c.id().equals(colony.id())),
                "a disbanded colony still stands");
        helper.succeed();
    }
}
