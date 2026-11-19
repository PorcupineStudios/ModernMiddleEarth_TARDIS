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

            // a plaque registers the room by its door (it replaced the writ); an empty room says what it lacks
            int x3 = x0 + 38;
            shell(level, x3, y0, z0, x3 + 4, z0 + 4, 3);
            door(level, new BlockPos(x3, y0 + 1, z0 + 2), Direction.EAST);
            BlockPos plaque = new BlockPos(x3 + 1, y0 + 2, z0 + 1);
            var empty = ColonyBuildings.plaque(player, level, plaque);
            helper.assertTrue(empty.building().isEmpty() && empty.message().getString().contains("needs"),
                    "an empty room didn't say what it lacks: " + empty.message().getString());
            level.setBlock(new BlockPos(x3 + 3, y0 + 1, z0 + 3), Blocks.CHEST.defaultBlockState(), 2);
            var byPlaque = ColonyBuildings.plaque(player, level, plaque);
            helper.assertTrue(byPlaque.building().isPresent(), "a plaque didn't register a room with a chest: "
                    + byPlaque.message().getString());

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

    /**
     * How a plaque finds its room (Aaron 2026-11-18): hung outside beside a door it names the room behind that door;
     * hung inside away from any door it names the room it hangs in (an open gate in its wall is a door, never a way
     * out); taking the plaque down or breaking the door takes the building down the next tick; a colony has one Town
     * Hall.
     */
    @GameTest(template = "empty", batch = "buildings", timeoutTicks = 100)
    public static void plaquesFindTheirRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int x0 = 7_000_000 + level.random.nextInt(10_000) * 512;
        int z0 = 6_000_000;
        int y0 = 150;
        for (int cx = (x0 - 16) >> 4; cx <= (x0 + 48) >> 4; cx++) {
            for (int cz = (z0 - 16) >> 4; cz <= (z0 + 32) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        Colonies colonies = Colonies.get(server);
        Colony colony = colonies.found(player, "Plaque Test", new BlockPos(x0 + 15, y0, z0 - 8), LotrPort.id("gondor"))
                .colony().orElseThrow();
        Runnable cleanup = () -> {
            colonies.disband(server, colony, "test");
            fill(level, x0 - 2, y0, z0 - 2, x0 + 40, y0 + 9, z0 + 12, Blocks.AIR);
        };
        var plaqueBlock = com.porcupinestudios.lotrport.colony.ColonyBlocks.PLAQUE.get().defaultBlockState();
        var facingProp = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
        try {
            // 1: a plaque outside, on the wall beside the door: the room behind the door
            shell(level, x0, y0, z0, x0 + 4, z0 + 4, 3);
            door(level, new BlockPos(x0 + 2, y0 + 1, z0), Direction.SOUTH);
            level.setBlock(new BlockPos(x0 + 3, y0 + 1, z0 + 3), Blocks.CHEST.defaultBlockState(), 2);
            BlockPos outside = new BlockPos(x0 + 1, y0 + 2, z0 - 1);
            level.setBlock(outside, plaqueBlock.setValue(facingProp, Direction.NORTH), 2);
            var a = ColonyBuildings.plaque(player, level, outside);
            helper.assertTrue(a.building().isPresent() && a.building().get().door().equals(new BlockPos(x0 + 2,
                    y0 + 1, z0)) && a.building().get().plaque().equals(Optional.of(outside))
                    && a.building().get().blocks().contains(new BlockPos(x0 + 2, y0 + 1, z0 + 2).asLong()),
                    "a plaque beside the door didn't name the room behind it: " + a.message().getString());

            // 2: a plaque inside, away from the door; an open fence gate in another wall is a door, not a way out
            int x1 = x0 + 8;
            shell(level, x1, y0, z0, x1 + 4, z0 + 4, 3);
            door(level, new BlockPos(x1, y0 + 1, z0 + 2), Direction.EAST);
            level.setBlock(new BlockPos(x1 + 2, y0 + 1, z0 + 4), Blocks.OAK_FENCE_GATE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FenceGateBlock.OPEN, true), 2);
            BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
            level.setBlock(new BlockPos(x1 + 1, y0 + 1, z0 + 1), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.setBlock(new BlockPos(x1 + 1, y0 + 1, z0 + 2), bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
            BlockPos inside = new BlockPos(x1 + 3, y0 + 2, z0 + 2);
            level.setBlock(inside, plaqueBlock.setValue(facingProp, Direction.WEST), 2);
            var b = ColonyBuildings.plaque(player, level, inside);
            helper.assertTrue(b.building().isPresent() && b.building().get().type().equals(LotrPort.id("house"))
                    && b.building().get().floor() == 9,
                    "a plaque inside didn't name its own room (or the open gate let it out): "
                    + b.message().getString() + " " + b.building().map(Building::counts).orElse(null));

            // 3: one Town Hall a colony
            int x2 = x0 + 16;
            shell(level, x2, y0, z0, x2 + 5, z0 + 5, 3);
            door(level, new BlockPos(x2, y0 + 1, z0 + 2), Direction.EAST);
            level.setBlock(new BlockPos(x2 + 3, y0 + 1, z0 + 3), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
            int x3 = x0 + 24;
            shell(level, x3, y0, z0, x3 + 5, z0 + 5, 3);
            door(level, new BlockPos(x3, y0 + 1, z0 + 2), Direction.EAST);
            level.setBlock(new BlockPos(x3 + 3, y0 + 1, z0 + 3), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
            var hall = ColonyBuildings.add(player, level, new BlockPos(x2, y0 + 1, z0 + 2),
                    Optional.of(LotrPort.id("town_hall")), Optional.empty());
            helper.assertTrue(hall.building().isPresent(), "no Town Hall: " + hall.message().getString());
            var second = ColonyBuildings.add(player, level, new BlockPos(x3, y0 + 1, z0 + 2),
                    Optional.of(LotrPort.id("town_hall")), Optional.empty());
            helper.assertTrue(second.building().isEmpty(), "a second Town Hall was accepted");
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }

        // 4: take the first plaque down and break the second room's door: both buildings go the next tick
        java.util.UUID first = Buildings.of(colonies.get(colony.id()).orElseThrow()).list().stream()
                .filter(x -> x.plaque().isPresent() && x.plaque().get().getZ() == z0 - 1).findFirst().orElseThrow()
                .id();
        Building house = Buildings.of(colonies.get(colony.id()).orElseThrow()).list().stream()
                .filter(x -> x.type().equals(LotrPort.id("house"))).findFirst().orElseThrow();
        level.setBlock(new BlockPos(x0 + 1, y0 + 2, z0 - 1), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(house.door(), Blocks.AIR.defaultBlockState(), 2 | 16);
        level.setBlock(house.door().above(), Blocks.AIR.defaultBlockState(), 2 | 16);
        com.porcupinestudios.lotrport.colony.building.BuildingSystems.changed(level, house.door());
        helper.succeedWhen(() -> {
            Buildings now = Buildings.of(colonies.get(colony.id()).orElseThrow());
            helper.assertTrue(now.get(first).isEmpty(), "the building stayed after its plaque was taken down");
            helper.assertTrue(now.get(house.id()).isEmpty(), "the house stayed after its door was broken");
            cleanup.run();
        });
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
