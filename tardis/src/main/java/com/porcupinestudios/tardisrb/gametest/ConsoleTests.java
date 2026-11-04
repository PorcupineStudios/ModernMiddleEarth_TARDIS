package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.console.ConsoleControls;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.repair.Repairs;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The console model (Aaron, 2026-11-03): controls appear as the TARDIS is repaired, and the buttons, dial and lever
 * set a course and fly the hop.
 */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConsoleTests {
    private ConsoleTests() {}

    private static Optional<ConsoleBlockEntity.Shown> find(List<ConsoleBlockEntity.Shown> shown, String id) {
        return shown.stream().filter(c -> c.id().equals(TardisRB.id(id).toString())).findFirst();
    }

    @GameTest(template = "platform")
    public static void controlsAppearWithRepairsAndFlyAHop(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        helper.setBlock(new BlockPos(6, 2, 4), TardisBlocks.CONSOLE.get());
        ConsoleBlockEntity console = (ConsoleBlockEntity) helper.getLevel().getBlockEntity(
                helper.absolutePos(new BlockPos(6, 2, 4)));
        helper.assertTrue(console != null, "the console has no block entity");

        List<ConsoleBlockEntity.Shown> wreck = ConsoleControls.shown(server, t);
        helper.assertTrue(find(wreck, "monitor").isPresent(), "the wreck's console has no monitor");
        helper.assertTrue(find(wreck, "hop_lever").isEmpty(), "the hop lever is there before navigation is repaired");

        helper.assertTrue(Repairs.attempt(server, t, TardisRB.id("navigation_1"), Optional.empty()).done(),
                "navigation repair");
        List<ConsoleBlockEntity.Shown> shown = ConsoleControls.shown(server, t);
        for (String id : List.of("x_up", "step_dial", "hop_lever", "reset")) {
            helper.assertTrue(find(shown, id).isPresent(), id + " did not appear after the navigation repair");
        }
        helper.assertTrue(find(shown, "jump_lever").isEmpty(), "the jump lever is there before the core milestone");
        for (ConsoleBlockEntity.Shown c : shown) {
            helper.assertTrue(!c.part().isEmpty(), c.id() + " names no part of the console model");
        }

        // no screen: the monitor's readout says where the TARDIS is and the course
        List<String> readout = ConsoleControls.readout(server, t, console);
        BlockPos at = t.location().get().pos();
        helper.assertTrue(readout.contains(at.getX() + " " + at.getY() + " " + at.getZ()),
                "the monitor doesn't show where the TARDIS is: " + readout);

        ConsoleControls.use(server, t, console, find(shown, "step_dial").get(), Optional.empty());
        ConsoleControls.use(server, t, console, find(shown, "x_up").get(), Optional.empty());
        helper.assertTrue(console.dx() == 10 && console.dy() == 0 && console.dz() == 0,
                "course " + console.dx() + " " + console.dy() + " " + console.dz());
        helper.assertTrue(console.uses(find(shown, "x_up").get().id()) == 1, "the button press wasn't counted");
        helper.assertTrue(ConsoleControls.readout(server, t, console).contains("Course +10 +0 +0 x10"),
                "the monitor doesn't show the course: " + ConsoleControls.readout(server, t, console));

        t.addCharge(100);
        BlockPos target = t.location().get().pos().offset(10, 0, 0);
        String said = ConsoleControls.use(server, t, console, find(shown, "hop_lever").get(), Optional.empty())
                .getString();
        helper.assertTrue(t.location().get().pos().equals(target), "the hop lever didn't fly the course: " + said);
        helper.assertTrue(console.dx() == 0, "the course wasn't cleared after the hop");
        helper.succeed();
    }
}
