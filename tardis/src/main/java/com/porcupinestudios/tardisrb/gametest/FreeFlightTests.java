package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.flight.FreeFlight;
import com.porcupinestudios.tardisrb.flight.Piloting;
import com.porcupinestudios.tardisrb.repair.Repairs;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The exterior as an entity (docs/free-flight-plan.md slices 2 and 3): phase turns the block into a loose box with
 * the doors shut; with the thrusters on it hovers, with them off it falls to the ground; phase again sets it down as
 * the block, with the record's location on it.
 */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FreeFlightTests {
    private FreeFlightTests() {}

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void phaseTurnsTheBoxLooseHoversFallsAndSetsDown(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        BlockPos base = t.location().get().pos();
        helper.assertTrue(Repairs.attempt(server, t, TardisRB.id("navigation_1"), Optional.empty()).done(),
                "navigation repair");

        String said = FreeFlight.phase(server, t, Optional.empty()).getString();
        helper.assertTrue(FreeFlight.isLoose(t), "the box didn't come loose: " + said);
        helper.assertTrue(!(helper.getLevel().getBlockState(base).getBlock() instanceof ExteriorBlock),
                "the exterior block is still there");
        helper.assertTrue(!Doors.open(server, t), "the doors opened while the box is loose");
        TardisBody body = FreeFlight.body(server, t).orElse(null);
        helper.assertTrue(body != null, "no loose body");

        // thrusters on: lifted three blocks, it stays up
        FreeFlight.setThrusters(server, t, true, Optional.empty());
        body.setPos(body.getX(), body.getY() + 3, body.getZ());
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(body.getY() > base.getY() + 2.5, "it fell with the thrusters on: y " + body.getY());
            FreeFlight.setThrusters(server, t, false, Optional.empty());
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(body.onGround() && Math.abs(body.getY() - base.getY()) < 0.01,
                        "it didn't fall to the ground with the thrusters off: y " + body.getY());
                helper.assertTrue(t.location().equals(Optional.of(GlobalPos.of(helper.getLevel().dimension(),
                        body.blockPosition()))), "the record doesn't follow the box: " + t.location());
                String back = FreeFlight.phase(server, t, Optional.empty()).getString();
                helper.assertTrue(!FreeFlight.isLoose(t) && body.isRemoved(), "it didn't set down: " + back);
                helper.assertTrue(helper.getLevel().getBlockState(base).getBlock() instanceof ExteriorBlock,
                        "the block isn't back at " + base);
                helper.assertTrue(t.location().get().pos().equals(base), "the record says " + t.location());
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void theFlightYokeSeatsThePilotAndShiftPutsThemBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = WreckTests.onPlatform(helper, Optional.empty());
        helper.assertTrue(Repairs.attempt(server, t, TardisRB.id("navigation_1"), Optional.empty()).done(),
                "navigation repair");
        ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(new BlockPos(5, 2, 4));
        pilot.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        Vec3 start = pilot.position();

        helper.assertTrue(Piloting.start(server, t, pilot).getString().contains("loose"),
                "the yoke worked on a landed box");
        FreeFlight.phase(server, t, Optional.empty());
        helper.assertTrue(Piloting.start(server, t, pilot).getString().contains("thrusters"),
                "the yoke worked with the thrusters off");
        FreeFlight.setThrusters(server, t, true, Optional.empty());
        TardisBody body = FreeFlight.body(server, t).orElseThrow();
        String said = Piloting.start(server, t, pilot).getString();
        helper.assertTrue(Piloting.isPilot(pilot), "not flying: " + said);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(pilot.getVehicle() == body, "the pilot isn't in the box");
            helper.assertTrue(pilot.isInvisible(), "the pilot can be seen");
            pilot.stopRiding(); // Shift
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(!Piloting.isPilot(pilot) && !pilot.isPassenger() && !pilot.isInvisible(),
                        "getting off didn't end piloting");
                helper.assertTrue(pilot.position().distanceTo(start) < 0.5,
                        "the pilot wasn't put back at the console: " + pilot.position());
                helper.assertTrue(FreeFlight.isLoose(t) && body.thrusters(), "the box should go on hovering");
                helper.succeed();
            });
        });
    }
}
