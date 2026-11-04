package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.ringbearer.api.provider.Providers;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.Exteriors;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.compat.create.WinderBlock;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.repair.Repairs;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** TARDIS phase 2: the crash site, the interior, and short hops. */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WreckTests {
    private static final String PLATFORM = "platform";

    private WreckTests() {}

    /** A TARDIS standing on the test platform at (1,2,2) (the template's floor is at relative y 1), able to hop (navigation repaired, some charge). */
    static TardisRecord onPlatform(GameTestHelper helper, Optional<ServerPlayer> owner) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = TardisManager.get(server).create(server, "Test box", owner.map(ServerPlayer::getUUID));
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 2));
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

    /**
     * The game test server makes only the overworld (and doesn't load dimension files), so this builds the console
     * room 48 blocks above the test and checks it has its console, hull, doors and open hallway ends.
     * The dedicated-server world check (CI) builds a real interior.
     */
    @GameTest(template = PLATFORM)
    public static void theConsoleRoomTemplateLoads(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StructureTemplate room = server.getStructureManager()
                .get(TardisContent.tuning().interior().room()).orElse(null);
        helper.assertTrue(room != null, "no console room template");
        BlockPos origin = helper.absolutePos(new BlockPos(0, 48, 0));
        ServerLevel level = helper.getLevel();
        Interiors.build(level, origin, room);
        var t = TardisContent.tuning().interior();
        BlockPos console = origin.offset(t.console());
        helper.assertTrue(level.getBlockState(console).is(TardisBlocks.CONSOLE.get()), "no console in the room");
        helper.assertTrue(level.getBlockState(console.west()).is(TardisBlocks.CONSOLE_HULL.get())
                && level.getBlockState(console.south()).is(TardisBlocks.CONSOLE_HULL.get()), "no hull round the console");
        BlockPos door = origin.offset(TardisContent.tuning().door().interiorDoor());
        helper.assertTrue(level.getBlockState(door).is(TardisBlocks.INTERIOR_DOOR.get())
                && level.getBlockState(door.above()).is(TardisBlocks.INTERIOR_DOOR.get()), "no doors in the room");
        BlockPos spawn = origin.offset(t.spawn());
        helper.assertTrue(level.getBlockState(spawn).isAir() && level.getBlockState(spawn.above()).isAir()
                && !level.getBlockState(spawn.below()).isAir(), "spawn point is blocked or has no floor");
        for (var anchor : t.roomAnchors()) {
            for (BlockPos p : BlockPos.betweenClosed(origin.offset(anchor.min()), origin.offset(anchor.max()))) {
                helper.assertTrue(level.getBlockState(p).isAir(), "hallway end blocked at " + p.subtract(origin));
            }
        }
        clearInterior(level, origin);
        helper.succeed();
    }

    /** Clears a console room built at {@code origin} by a test, with the rooms near it. */
    public static void clearInterior(ServerLevel level, BlockPos origin) {
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-34, -10, -34), origin.offset(34, 8, 20))) {
            if (!level.getBlockState(p).isAir()) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @GameTest(template = PLATFORM)
    public static void aShortHopMovesTheBox(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = onPlatform(helper, Optional.empty());
        BlockPos from = t.location().get().pos();
        BlockPos target = helper.absolutePos(new BlockPos(11, 2, 2));
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
        helper.assertBlockPresent(TardisBlocks.EXTERIOR.get(), new BlockPos(11, 2, 2));
        helper.assertBlockPresent(TardisBlocks.EXTERIOR.get(), new BlockPos(11, 3, 2));
        helper.assertTrue(helper.getLevel().getBlockState(from).isAir(), "old box left behind");
        helper.assertTrue(t.charge() == before - Flight.baseCost(10), "charge " + t.charge());
        Exteriors.lift(helper.getLevel(), target);
        helper.succeed();
    }

    /**
     * Phase 2 done-when: a Create creative motor turns the winder next to the box; once wound up, the TARDIS hops.
     * Motor at (3,2,4) facing west drives the winder at (2,2,4), within reach of the box at (1,2,2).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 800)
    public static void windingWithCreateLetsTheWreckHop(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TardisRecord t = onPlatform(helper, Optional.empty());
        helper.assertTrue(Repairs.attempt(server, t, TardisRB.id("navigation_1"), Optional.empty()).done(),
                "navigation repair");
        helper.setBlock(new BlockPos(2, 2, 4), TardisBlocks.WINDER.get().defaultBlockState()
                .setValue(WinderBlock.AXIS, Direction.Axis.X));
        Block motor = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
        helper.setBlock(new BlockPos(3, 2, 4), motor.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST));
        BlockPos target = helper.absolutePos(new BlockPos(11, 2, 2));
        long cost = Flight.baseCost(Math.sqrt(t.location().get().pos().distSqr(target)));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(t.charge() >= cost, "charge " + t.charge() + " of " + cost))
                .thenExecute(() -> {
                    Flight.Outcome out = Flight.hop(server, t, target, Optional.empty());
                    if (!out.done()) {
                        helper.fail("hop failed: " + out.problem().get().getString());
                    }
                    helper.assertBlockPresent(TardisBlocks.EXTERIOR.get(), new BlockPos(11, 2, 2));
                    Exteriors.lift(helper.getLevel(), target);
                })
                .thenSucceed();
    }
}
