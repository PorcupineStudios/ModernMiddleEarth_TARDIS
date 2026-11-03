package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
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
     * Phase 4 done-when: a repaired TARDIS can add a second room, and the cap blocks a third. Built 20 blocks above
     * the test (the interior dimension isn't made by the game test server; the CI world check covers it there).
     */
    @GameTest(template = "platform")
    public static void aRepairedTardisAddsASecondRoomAndTheCapBlocksAThird(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RepairLoopTests.crew(helper, "rooms");
        TardisRecord t = TardisManager.get(server).create(server, "Room test", Optional.of(player.getUUID()));
        BlockPos origin = helper.absolutePos(new BlockPos(0, 20, 0));
        StructureTemplate console = server.getStructureManager().get(TardisContent.tuning().interior().room())
                .orElseThrow();
        console.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(),
                Block.UPDATE_CLIENTS);

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

        BlockPos corner = Rooms.corner(origin, 1);
        helper.assertTrue(level.getBlockState(corner.offset(1, 1, 2)).is(Blocks.BOOKSHELF), "no shelves in the library");
        BlockPos door = TardisContent.tuning().interior().doorway();
        for (int dz = -1; dz <= 0; dz++) {
            for (int dy = 0; dy < 2; dy++) {
                BlockPos p = corner.offset(door.getX(), door.getY() + dy, dz);
                helper.assertTrue(level.getBlockState(p).isAir(), "doorway blocked at " + p);
            }
        }

        RepairLoopTests.give(player, TardisContent.get().cost(TardisContent.room(WORKSHOP).orElseThrow().cost()));
        var third = Rooms.add(level, origin, t, WORKSHOP, Optional.of(player));
        helper.assertFalse(third.done(), "a third room got past the cap");
        helper.assertTrue(t.roomCount() == 2, "room count after the blocked room " + t.roomCount());

        int step = TardisContent.tuning().interior().roomStep();
        for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(8, 4, step * 2 - 1))) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        helper.succeed();
    }
}
