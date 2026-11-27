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
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The console (Aaron, 2026-11-03; controls on themed sides 2026-11-08): controls appear as the TARDIS is repaired,
 * the X levers set a coordinate (1.7.10 style), memory slots save and load it, the handbrake holds the TARDIS, the
 * dematerialisation lever flies there, and the monitor's readout changes with the side it stands over.
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
        helper.assertTrue(find(wreck, "demat_lever").isEmpty(),
                "the dematerialisation lever is there before navigation is repaired");

        helper.assertTrue(FlightReady.navigation(server, t),
                "navigation repair");
        List<ConsoleBlockEntity.Shown> shown = ConsoleControls.shown(server, t);
        for (String id : List.of("x_lever_1", "x_wheel_1", "z_lever_5", "y_lever_4", "demat_lever", "handbrake",
                "memory_bank", "dimension_dial", "thrusters", "flight_yoke", "land_switch",
                "relative_switch")) {
            helper.assertTrue(find(shown, id).isPresent(), id + " did not appear after the flight upgrades");
        }
        // only flight controls unlock for now (Aaron 2026-11-19): time and rooms come later
        for (String id : List.of("time_drums", "time_lever", "time_unit_dial", "room_deleter", "schema_chooser")) {
            helper.assertTrue(find(shown, id).isEmpty(), id + " is on the console");
        }
        for (ConsoleBlockEntity.Shown c : shown) {
            helper.assertTrue(!c.model().isEmpty(), c.id() + " names no control model");
        }

        // no screen: the monitor's readout (over the Z side until turned) says where the TARDIS is
        BlockPos from = t.location().get().pos();
        helper.assertTrue(ConsoleControls.readout(server, t, console).contains("Now Z\t" + from.getZ()),
                "the monitor doesn't show where the TARDIS is: " + ConsoleControls.readout(server, t, console));

        // X: the x6 lever once and the x1 lever four times = +10, relative to where the TARDIS stands
        ConsoleBlockEntity.Shown six = find(shown, "x_lever_3").get();
        ConsoleBlockEntity.Shown one = find(shown, "x_lever_4").get();
        ConsoleControls.use(server, t, console, six, "lever", Optional.empty());
        for (int i = 0; i < 4; i++) {
            ConsoleControls.use(server, t, console, one, "lever", Optional.empty());
        }
        helper.assertTrue(ConsoleControls.axisValue(console, 'x') == 10,
                "the X levers give " + ConsoleControls.axisValue(console, 'x'));
        helper.assertTrue(console.state("x3") == 4 && console.uses(ConsoleLayout.key(one.id(), "lever")) == 4,
                "the lever's setting or clicks weren't counted");

        // each side has its own readout: turned to the door side (+z), the monitor shows the X coordinates
        console.setMonitorAngle(90);
        List<String> xSide = ConsoleControls.readout(server, t, console);
        helper.assertTrue(xSide.contains("Target X\t" + (from.getX() + 10)), "the monitor over side 0 shows " + xSide);

        // (the time controls aren't on the console for now: Aaron 2026-11-19, time comes later)
        console.setMonitorAngle(Float.NaN);

        // memory: save, change, load
        ConsoleBlockEntity.Shown save = find(shown, "save_switch").get();
        ConsoleBlockEntity.Shown bank = find(shown, "memory_bank").get();
        ConsoleControls.use(server, t, console, save, "rod", Optional.empty());
        ConsoleControls.use(server, t, console, bank, "slot_3", Optional.empty());
        ConsoleControls.use(server, t, console, save, "rod", Optional.empty());
        ConsoleControls.use(server, t, console, one, "lever", Optional.empty());
        ConsoleControls.use(server, t, console, bank, "slot_3", Optional.empty());
        helper.assertTrue(ConsoleControls.axisValue(console, 'x') == 10, "memory slot 4 didn't load X +10");

        t.addCharge(100);
        BlockPos target = new BlockPos(from.getX() + 10, helper.getLevel().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, from.getX() + 10, from.getZ()), from.getZ());
        ConsoleBlockEntity.Shown demat = find(shown, "demat_lever").get();
        ConsoleControls.use(server, t, console, find(shown, "handbrake").get(), "arm", Optional.empty());
        ConsoleControls.use(server, t, console, demat, "arm", Optional.empty());
        helper.assertTrue(t.location().get().pos().equals(from), "the TARDIS took off with the handbrake on");
        ConsoleControls.use(server, t, console, find(shown, "handbrake").get(), "arm", Optional.empty());
        String said = ConsoleControls.use(server, t, console, demat, "arm", Optional.empty()).getString();
        helper.assertTrue(t.location().get().pos().equals(target),
                "the dematerialisation lever didn't fly to " + target + ": " + said);

        // fast return: armed, the next pull of the lever goes back to where it took off from
        t.addCharge(100);
        ConsoleControls.use(server, t, console, find(shown, "fast_return").get(), "button", Optional.empty());
        helper.assertTrue(console.state("fast_return") == 1, "the fast return button didn't arm");
        said = ConsoleControls.use(server, t, console, demat, "arm", Optional.empty()).getString();
        helper.assertTrue(t.location().get().pos().equals(from), "fast return didn't go back to " + from + ": " + said);
        helper.assertTrue(console.state("fast_return") == 0, "fast return stayed armed after going back");
        // (the schema chooser isn't on the console for now, asserted above: rooms come later, Aaron 2026-11-19)
        helper.succeed();
    }
}
