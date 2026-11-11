package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.BuildingSettings;
import com.porcupinestudios.lotrport.colony.building.BuildingType;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.building.ColonyBuildings;
import com.porcupinestudios.lotrport.colony.building.RoomScan;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Colony buildings measure like RingRealms' {@code RoomSelfTest}: an 11x11 square split into an L (floor 56, its own
 * chest and table) and a storehouse in the fourth quadrant (floor 16, its own chest), no block shared; a 7x7
 * two-storey house with a stairwell hole (2 storeys, floor 49, the bed upstairs and the chest downstairs); a room
 * open to the sky is refused; a broken building is lost when measured again.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildingTests {
    private BuildingTests() {}

    @GameTest(template = "empty", batch = "buildings", timeoutTicks = 200)
    public static void roomsMeasureLikeRingRealms(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        helper.assertTrue(BuildingType.loadProblems().isEmpty() && BuildingSettings.loadProblems().isEmpty(),
                "data problems: " + BuildingType.loadProblems() + BuildingSettings.loadProblems());
        helper.assertTrue(BuildingType.all().size() >= 10, "building types: " + BuildingType.all().keySet());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // a fresh spot each run (the test world keeps its saved data), far from other tests
        int x0 = 6_000_000 + level.random.nextInt(10_000) * 512;
        int z0 = 6_000_000;
        int y0 = 150;
        for (int cx = (x0 - 16) >> 4; cx <= (x0 + 48) >> 4; cx++) {
            for (int cz = (z0 - 16) >> 4; cz <= (z0 + 32) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        Colonies colonies = Colonies.get(server);
        Colony colony = colonies.found(player, "Room Test", new BlockPos(x0 + 15, y0, z0 - 8), LotrPort.id("gondor"))
                .colony().orElseThrow();
        try {
            // the L and the quadrant
            shell(level, x0, y0, z0, x0 + 10, z0 + 10, 3);
            fill(level, x0 + 5, y0 + 1, z0 + 5, x0 + 5, y0 + 3, z0 + 10, Blocks.STONE);
            fill(level, x0 + 5, y0 + 1, z0 + 5, x0 + 10, y0 + 3, z0 + 5, Blocks.STONE);
            door(level, new BlockPos(x0, y0 + 1, z0 + 2), Direction.EAST);
            door(level, new BlockPos(x0 + 10, y0 + 1, z0 + 8), Direction.WEST);
            level.setBlock(new BlockPos(x0 + 2, y0 + 1, z0 + 8), Blocks.CHEST.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + 8, y0 + 1, z0 + 8), Blocks.CHEST.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + 8, y0 + 1, z0 + 2), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
            var l = ColonyBuildings.add(player, level, new BlockPos(x0, y0 + 2, z0 + 2),
                    Optional.of(LotrPort.id("workshop")), Optional.of("The L"));
            helper.assertTrue(l.building().isPresent(), "the L: " + l.message().getString());
            var q = ColonyBuildings.add(player, level, new BlockPos(x0 + 10, y0 + 1, z0 + 8), Optional.empty(),
                    Optional.empty());
            helper.assertTrue(q.building().isPresent(), "the quadrant: " + q.message().getString());
            Building lb = l.building().get();
            Building qb = q.building().get();
            helper.assertTrue(lb.floor() == 56 && qb.floor() == 16,
                    "floor: L 56, quadrant 16: " + lb.floor() + ", " + qb.floor());
            helper.assertTrue(lb.count("chests") == 1 && qb.count("chests") == 1, "each counts only its own chest");
            helper.assertTrue(lb.count("tables") == 1 && qb.count("tables") == 0, "the table is the L's");
            helper.assertTrue(qb.type().equals(LotrPort.id("storehouse")), "a chest room is a storehouse: " + qb.type());
            Buildings all = Buildings.of(colonies.get(colony.id()).orElseThrow());
            helper.assertTrue(all.at(new BlockPos(x0 + 8, y0 + 1, z0 + 8)).map(b -> b.id().equals(qb.id()))
                    .orElse(false), "the quadrant owns its chest");
            helper.assertTrue(all.at(new BlockPos(x0 + 2, y0 + 1, z0 + 8)).map(b -> b.id().equals(lb.id()))
                    .orElse(false) && all.at(new BlockPos(x0 + 8, y0 + 1, z0 + 2)).map(b -> b.id().equals(lb.id()))
                    .orElse(false), "the L owns both arms");
            boolean shared = false;
            for (long p : qb.blocks()) {
                shared |= lb.blocks().contains(p);
            }
            helper.assertTrue(!shared, "a block belongs to both");
            var again = ColonyBuildings.add(player, level, new BlockPos(x0 + 10, y0 + 2, z0 + 8), Optional.empty(),
                    Optional.empty());
            helper.assertTrue(again.building().map(b -> b.id().equals(qb.id())).orElse(false)
                    && Buildings.of(colonies.get(colony.id()).orElseThrow()).list().size() == 2,
                    "a writ on the same door measured a second building");

            // two storeys
            int x1 = x0 + 20;
            shell(level, x1, y0, z0, x1 + 6, z0 + 6, 7);
            fill(level, x1 + 1, y0 + 4, z0 + 1, x1 + 5, y0 + 4, z0 + 5, Blocks.OAK_PLANKS);
            level.setBlock(new BlockPos(x1 + 5, y0 + 4, z0 + 5), Blocks.AIR.defaultBlockState(), 2);
            door(level, new BlockPos(x1, y0 + 1, z0 + 2), Direction.EAST);
            level.setBlock(new BlockPos(x1 + 3, y0 + 1, z0 + 4), Blocks.CHEST.defaultBlockState(), 2);
            BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
            level.setBlock(new BlockPos(x1 + 2, y0 + 5, z0 + 2), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.setBlock(new BlockPos(x1 + 2, y0 + 5, z0 + 3), bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
            var h = ColonyBuildings.add(player, level, new BlockPos(x1, y0 + 1, z0 + 2), Optional.empty(),
                    Optional.empty());
            helper.assertTrue(h.building().isPresent(), "the two-storey house: " + h.message().getString());
            Building hb = h.building().get();
            helper.assertTrue(hb.count(RoomScan.STOREYS) == 2 && hb.count("beds") == 1 && hb.count("chests") == 1
                    && hb.floor() == 49, "two storeys, a bed, a chest, floor 49: " + hb.counts());
            helper.assertTrue(hb.type().equals(LotrPort.id("house")), "a room with a bed is a house: " + hb.type());
            helper.assertTrue(hb.blocks().contains(new BlockPos(x1 + 2, y0 + 5, z0 + 3).asLong())
                    && hb.blocks().contains(new BlockPos(x1 + 3, y0 + 6, z0 + 3).asLong())
                    && !hb.blocks().contains(new BlockPos(x1 + 3, y0 + 4, z0 + 3).asLong()),
                    "the bed and upstairs are the house's, the upper floor's planks are not");
            var codec = Building.CODEC;
            Building back = codec.parse(NbtOps.INSTANCE, codec.encodeStart(NbtOps.INSTANCE, hb).getOrThrow())
                    .getOrThrow();
            helper.assertTrue(back.equals(hb), "a building isn't saved and loaded back the same");

            // open to the sky: refused
            int x2 = x0 + 32;
            shell(level, x2, y0, z0, x2 + 4, z0 + 4, 2);
            door(level, new BlockPos(x2, y0 + 1, z0 + 2), Direction.EAST);
            level.setBlock(new BlockPos(x2 + 2, y0 + 3, z0 + 2), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x2 + 2, y0 + 1, z0 + 1), Blocks.CHEST.defaultBlockState(), 2);
            var open = ColonyBuildings.add(player, level, new BlockPos(x2, y0 + 1, z0 + 2), Optional.empty(),
                    Optional.empty());
            helper.assertTrue(open.building().isEmpty(), "a roofless room was accepted");

            // break the house's bed: measured again it is a storehouse (it keeps a chest); without the chest it is lost
            level.setBlock(new BlockPos(x1 + 2, y0 + 5, z0 + 3), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x1 + 2, y0 + 5, z0 + 2), Blocks.AIR.defaultBlockState(), 2);
            Colony now = colonies.get(colony.id()).orElseThrow();
            helper.assertTrue(ColonyBuildings.recheck(level, now, hb), "the house went with its bed");
            Building retyped = Buildings.of(now).get(hb.id()).orElseThrow();
            helper.assertTrue(retyped.type().equals(LotrPort.id("storehouse")), "bedless house is now " + retyped.type());
            level.setBlock(new BlockPos(x1 + 3, y0 + 1, z0 + 4), Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(!ColonyBuildings.recheck(level, now, retyped)
                    && Buildings.of(now).get(hb.id()).isEmpty(), "an empty room stayed a building");
            helper.succeed();
        } finally {
            colonies.disband(server, colony, "test");
            fill(level, x0 - 1, y0, z0 - 1, x0 + 40, y0 + 9, z0 + 11, Blocks.AIR);
        }
    }

    /** Floor at y0, walls y0+1..y0+h, roof at y0+h+1, hollow inside (RingRealms' test shell). */
    static void shell(ServerLevel level, int x0, int y0, int z0, int x1, int z1, int h) {
        fill(level, x0, y0, z0, x1, y0 + h + 1, z1, Blocks.STONE);
        fill(level, x0 + 1, y0 + 1, z0 + 1, x1 - 1, y0 + h, z1 - 1, Blocks.AIR);
    }

    static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            if (level.getBlockEntity(p) != null) {
                level.removeBlockEntity(p);
            }
            level.setBlock(p, b.defaultBlockState(), 2 | 16);
        }
    }

    /** A wooden door whose front faces {@code facing} (into the room). */
    static void door(ServerLevel level, BlockPos foot, Direction facing) {
        BlockState d = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
        level.setBlock(foot, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2 | 16);
        level.setBlock(foot.above(), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2 | 16);
    }

}
