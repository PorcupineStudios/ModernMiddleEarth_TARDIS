package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.console.ConsoleControls;
import com.porcupinestudios.tardisrb.console.ConsoleLayout;
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
 * The console (Aaron, 2026-11-03; controls on themed sides 2026-11-08): controls appear as the TARDIS is repaired,
 * the wheels set a course, the handbrake holds the TARDIS, the take-off lever flies the hop, and the monitor's readout
 * changes with the side it stands over.
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
        helper.assertTrue(find(wreck, "takeoff_lever").isEmpty(),
                "the take-off lever is there before navigation is repaired");

        helper.assertTrue(Repairs.attempt(server, t, TardisRB.id("navigation_1"), Optional.empty()).done(),
                "navigation repair");
        List<ConsoleBlockEntity.Shown> shown = ConsoleControls.shown(server, t);
        for (String id : List.of("coordinate_wheels", "valve_wheel", "takeoff_lever", "coordinate_lever",
                "handbrake")) {
            helper.assertTrue(find(shown, id).isPresent(), id + " did not appear after the navigation repair");
        }
        helper.assertTrue(find(shown, "demat_lever").isEmpty(),
                "the dematerialisation lever is there before the core milestone");
        for (ConsoleBlockEntity.Shown c : shown) {
            helper.assertTrue(!c.model().isEmpty(), c.id() + " names no control model");
        }

        // no screen: the monitor's readout (over the navigation side until turned) says where the TARDIS is
        List<String> readout = ConsoleControls.readout(server, t, console);
        BlockPos at = t.location().get().pos();
        helper.assertTrue(readout.contains(at.getX() + " " + at.getY() + " " + at.getZ()),
                "the monitor doesn't show where the TARDIS is: " + readout);

        ConsoleBlockEntity.Shown wheels = find(shown, "coordinate_wheels").get();
        ConsoleControls.use(server, t, console, find(shown, "valve_wheel").get(), "wheel", Optional.empty());
        ConsoleControls.use(server, t, console, wheels, "wheel_x", Optional.empty());
        helper.assertTrue(console.dx() == 10 && console.dy() == 0 && console.dz() == 0,
                "course " + console.dx() + " " + console.dy() + " " + console.dz());
        helper.assertTrue(console.uses(ConsoleLayout.key(wheels.id(), "wheel_x")) == 1,
                "the wheel's turn wasn't counted");
        helper.assertTrue(ConsoleControls.readout(server, t, console).contains("Course +10 +0 +0 x10"),
                "the monitor doesn't show the course: " + ConsoleControls.readout(server, t, console));

        // each side has its own readout: turned to the door side (+z), the monitor shows flight
        console.setMonitorAngle(90);
        List<String> flight = ConsoleControls.readout(server, t, console);
        helper.assertTrue(flight.get(0).startsWith("FLIGHT"), "the monitor over side 0 shows " + flight);
        console.setMonitorAngle(Float.NaN);

        t.addCharge(100);
        BlockPos from = t.location().get().pos();
        BlockPos target = from.offset(10, 0, 0);
        ConsoleBlockEntity.Shown takeoff = find(shown, "takeoff_lever").get();
        ConsoleControls.use(server, t, console, find(shown, "handbrake").get(), "arm", Optional.empty());
        ConsoleControls.use(server, t, console, takeoff, "lever", Optional.empty());
        helper.assertTrue(t.location().get().pos().equals(from), "the TARDIS hopped with the handbrake on");
        ConsoleControls.use(server, t, console, find(shown, "handbrake").get(), "arm", Optional.empty());
        String said = ConsoleControls.use(server, t, console, takeoff, "lever", Optional.empty()).getString();
        helper.assertTrue(t.location().get().pos().equals(target), "the take-off lever didn't fly the course: " + said);
        helper.assertTrue(console.dx() == 0, "the course wasn't cleared after the hop");
        helper.succeed();
    }
}
