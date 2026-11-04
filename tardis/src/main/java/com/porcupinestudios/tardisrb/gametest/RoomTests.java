package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.interior.Rooms;
import com.porcupinestudios.tardisrb.progression.Repair;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** TARDIS phase 4: rooms added behind the console room, limited by the room cap. */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoomTests {
    private static final ResourceLocation LIBRARY = TardisRB.id("library");
    private static final ResourceLocation WORKSHOP = TardisRB.id("workshop");

    private RoomTests() {}

    /**
     * Phase 4 done-when: a repaired TARDIS can add a second room, and the cap blocks a third. Built 80 blocks above
     * the test (the interior dimension isn't made by the game test server; the CI world check covers it there). The
     * room goes at the end of the first hallway, turned along it, with an opening the hallway's size.
     */
    @GameTest(template = "platform")
    public static void aRepairedTardisAddsASecondRoomAndTheCapBlocksAThird(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RepairLoopTests.crew(helper, "rooms");
        TardisRecord t = TardisManager.get(server).create(server, "Room test", Optional.of(player.getUUID()));
        BlockPos origin = helper.absolutePos(new BlockPos(0, 80, 0));
        StructureTemplate console = server.getStructureManager().get(TardisContent.tuning().interior().room())
                .orElseThrow();
        Interiors.build(level, origin, console);

        helper.assertFalse(Rooms.add(level, origin, t, LIBRARY, Optional.of(player)).done(),
                "added a room before the room systems were repaired");

        ResourceLocation repairId = TardisRB.id("rooms_1");
        Repair repair = TardisContent.repair(repairId).orElseThrow();
        t.applyRepair(repairId, repair);
        helper.assertTrue(Rooms.cap(t) == 2, "room cap after rooms_1 is " + Rooms.cap(t) + ", expected 2");
        t.addCharge(100);
        RepairLoopTests.give(player, TardisContent.get().cost(TardisContent.room(LIBRARY).orElseThrow().cost()));
        var added = Rooms.add(level, origin, t, LIBRARY, Optional.of(player));
        helper.assertTrue(added.done(), "library: " + added.problem().map(c -> c.getString()).orElse(""));
        helper.assertTrue(t.roomCount() == 2, "room count " + t.roomCount());
        helper.assertTrue(player.getInventory().countItem(Items.BOOK) == 0, "the books weren't taken");

        Rooms.Placement at = Rooms.placement(origin, 1, 9);
        helper.assertTrue(level.getBlockState(at.at(1, 1, 2)).is(Blocks.BOOKSHELF), "no shelves in the library");
        for (int x = at.openX(); x < at.openX() + at.width(); x++) {
            for (int y = 1; y <= at.height(); y++) {
                BlockPos p = at.at(x, y, 0);
                helper.assertTrue(level.getBlockState(p).isAir(), "opening blocked at " + p.subtract(origin));
                // the hallway it opens onto
                helper.assertTrue(level.getBlockState(p.relative(at.facing().getOpposite())).isAir(),
                        "hallway blocked at " + p.subtract(origin));
            }
        }

        RepairLoopTests.give(player, TardisContent.get().cost(TardisContent.room(WORKSHOP).orElseThrow().cost()));
        var third = Rooms.add(level, origin, t, WORKSHOP, Optional.of(player));
        helper.assertFalse(third.done(), "a third room got past the cap");
        helper.assertTrue(t.roomCount() == 2, "room count after the blocked room " + t.roomCount());

        WreckTests.clearInterior(level, origin);
        helper.succeed();
    }
}
