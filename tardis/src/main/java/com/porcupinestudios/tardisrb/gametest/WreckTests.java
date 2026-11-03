package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.ringbearer.api.provider.Providers;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.Exteriors;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.repair.Repairs;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** TARDIS phase 2: the crash site, the interior, and short hops. */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WreckTests {
    private static final String PLATFORM = "platform";

    private WreckTests() {}

    /** A TARDIS standing on the test platform at (1,1,2), able to hop (navigation repaired, some charge). */
    static TardisRecord onPlatform(GameTestHelper helper, Optional<ServerPlayer> owner) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = TardisManager.get(server).create(server, "Test box", owner.map(ServerPlayer::getUUID));
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 2));
        Exteriors.place(helper.getLevel(), base, Direction.EAST, t.id());
        t.setLocation(Optional.of(GlobalPos.of(helper.getLevel().dimension(), base)));
        return t;
    }

    @GameTest(template = PLATFORM)
    public static void theWreckLandsAtTheStartSpawn(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisManager manager = TardisManager.get(server);
        helper.assertTrue(manager.crashSitePlaced(), "no crash site was made");
        TardisRecord wreck = manager.all().stream().filter(r -> r.name().equals("The Wreck")).findFirst()
                .orElse(null);
        helper.assertTrue(wreck != null, "no wreck record");
        helper.assertTrue(wreck.owner().isEmpty(), "the wreck already has an owner");
        GlobalPos at = wreck.location().orElse(null);
        helper.assertTrue(at != null && at.dimension().equals(Providers.startDimension(server)),
                "wreck not in the start dimension: " + at);
        ServerLevel level = server.getLevel(at.dimension());
        BlockPos spawn = level.getSharedSpawnPos();
        helper.assertTrue(Math.abs(at.pos().getX() - spawn.getX()) <= 32 && Math.abs(at.pos().getZ() - spawn.getZ()) <= 32,
                "wreck at " + at.pos() + ", spawn " + spawn);
        helper.assertTrue(ExteriorBlock.tardisAt(level, at.pos()).map(r -> r == wreck).orElse(false),
                "no police box for the wreck at " + at.pos() + ": " + level.getBlockState(at.pos()));
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void theInteriorHasAConsoleRoom(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = onPlatform(helper, Optional.empty());
        helper.assertTrue(Interiors.ensure(server, t), "interior not built");
        ServerLevel inside = Interiors.level(server).orElseThrow();
        BlockPos origin = Interiors.origin(t.plot());
        helper.assertTrue(inside.getBlockState(origin.offset(4, 1, 5)).is(TardisBlocks.CONSOLE.get()),
                "no console in the room: " + inside.getBlockState(origin.offset(4, 1, 5)));
        helper.assertTrue(inside.getBlockState(origin.offset(4, 1, 0)).is(TardisBlocks.INTERIOR_DOOR.get()),
                "no doors in the room");
        helper.assertTrue(Interiors.at(inside, origin.offset(4, 1, 5)).map(r -> r == t).orElse(false),
                "console doesn't know its TARDIS");
        helper.assertTrue(inside.getBlockState(Interiors.spawn(t)).isAir()
                && inside.getBlockState(Interiors.spawn(t).above()).isAir(), "spawn point is blocked");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void aShortHopMovesTheBox(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = onPlatform(helper, Optional.empty());
        BlockPos from = t.location().get().pos();
        BlockPos target = helper.absolutePos(new BlockPos(11, 1, 2));
        helper.assertFalse(Flight.hop(server, t, target, Optional.empty()).done(), "the wreck hopped unrepaired");
        helper.assertTrue(Repairs.attempt(server, t, TardisRB.id("navigation_1"), Optional.empty()).done(),
                "navigation repair");
        helper.assertFalse(Flight.hop(server, t, target, Optional.empty()).done(), "hopped with no charge");
        t.addCharge(100);
        long before = t.charge();
        Flight.Outcome far = Flight.hop(server, t, target.offset(500, 0, 0), Optional.empty());
        helper.assertFalse(far.done(), "hopped beyond its range");
        Flight.Outcome out = Flight.hop(server, t, target, Optional.empty());
        if (!out.done()) {
            helper.fail("hop failed: " + out.problem().get().getString());
        }
        helper.assertTrue(out.landed().get().pos().equals(target), "landed at " + out.landed().get().pos());
        helper.assertTrue(t.location().get().pos().equals(target), "record not moved");
        helper.assertBlockPresent(TardisBlocks.EXTERIOR.get(), new BlockPos(11, 1, 2));
        helper.assertBlockPresent(TardisBlocks.EXTERIOR.get(), new BlockPos(11, 2, 2));
        helper.assertTrue(helper.getLevel().getBlockState(from).isAir(), "old box left behind");
        helper.assertTrue(t.charge() == before - Flight.baseCost(10), "charge " + t.charge());
        Exteriors.lift(helper.getLevel(), target);
        helper.succeed();
    }
}
