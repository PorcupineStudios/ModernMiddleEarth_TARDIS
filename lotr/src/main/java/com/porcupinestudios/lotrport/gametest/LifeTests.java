package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.ColonyBuildings;
import com.porcupinestudios.lotrport.colony.work.JobDef;
import com.porcupinestudios.lotrport.colony.work.Life;
import com.porcupinestudios.lotrport.colony.work.LifeSettings;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.ColonyScheduler;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A small colony lives a few days by RingRealms' rules: homes go to the beds, the hobbit farms (best race first),
 * the farmstead's chest fills with wheat, the cook bakes it into bread, wages are paid and the head tax comes back,
 * citizens eat from the storehouse, a striker makes nothing, and the long unhappy leave.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifeTests {
    private static final ResourceLocation HOBBIT = LotrPort.id("hobbit");
    private static final ResourceLocation DWARF = LotrPort.id("dwarf");
    private static final ResourceLocation MAN = LotrPort.id("gondor_man");

    private LifeTests() {}

    @GameTest(template = "empty", batch = "buildings", timeoutTicks = 200)
    public static void aColonyLivesByItsRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        helper.assertTrue(JobDef.loadProblems().isEmpty() && LifeSettings.loadProblems().isEmpty()
                && Races.loadProblems().isEmpty(), "data problems: " + JobDef.loadProblems()
                + LifeSettings.loadProblems() + Races.loadProblems());
        helper.assertTrue(Races.of(HOBBIT).equals("hobbit") && Races.of(DWARF).equals("dwarf")
                && Races.of(MAN).equals(Races.MAN), "races: " + Races.of(HOBBIT) + Races.of(DWARF) + Races.of(MAN));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int x0 = 7_000_000 + level.random.nextInt(10_000) * 512;
        int z0 = 7_000_000;
        int y0 = 150;
        for (int cx = (x0 - 16) >> 4; cx <= (x0 + 64) >> 4; cx++) {
            for (int cz = (z0 - 16) >> 4; cz <= (z0 + 16) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        Colonies colonies = Colonies.get(server);
        Colony colony = colonies.found(player, "Life Test", new BlockPos(x0 + 30, y0, z0 - 8), LotrPort.id("hobbit"))
                .colony().orElseThrow();
        try {
            // a house with two beds, a farmstead, a kitchen and a storehouse with bread
            BuildingTests.shell(level, x0, y0, z0, x0 + 6, z0 + 6, 3);
            BuildingTests.door(level, new BlockPos(x0, y0 + 1, z0 + 3), Direction.EAST);
            bed(level, new BlockPos(x0 + 2, y0 + 1, z0 + 1));
            bed(level, new BlockPos(x0 + 4, y0 + 1, z0 + 1));
            BlockPos farmChest = room(level, x0 + 10, y0, z0, Blocks.CHEST);
            BlockPos kitchenChest = room(level, x0 + 20, y0, z0, Blocks.CHEST);
            level.setBlock(kitchenChest.east(), Blocks.FURNACE.defaultBlockState(), 2);
            BlockPos storeChest = room(level, x0 + 30, y0, z0, Blocks.CHEST);
            ((ChestBlockEntity) level.getBlockEntity(storeChest)).setItem(0, new ItemStack(Items.BREAD, 20));
            Building house = add(helper, player, level, x0, y0, z0, Optional.empty());
            Building farm = add(helper, player, level, x0 + 10, y0, z0, Optional.of(LotrPort.id("farmstead")));
            Building kitchen = add(helper, player, level, x0 + 20, y0, z0, Optional.empty());
            Building store = add(helper, player, level, x0 + 30, y0, z0, Optional.of(LotrPort.id("storehouse")));
            helper.assertTrue(house.count("beds") == 2 && kitchen.type().equals(LotrPort.id("kitchen")),
                    "house beds " + house.count("beds") + ", kitchen is " + kitchen.type());

            // three citizens: a man, a dwarf and a hobbit (the hobbit farms: farmers come first, hobbits farm best)
            Persons persons = Persons.get(server);
            Person man = citizen(persons, colony, MAN);
            Person dwarf = citizen(persons, colony, DWARF);
            Person hobbit = citizen(persons, colony, HOBBIT);
            colonies.set(colony, CoreComponents.TREASURY, new CoreComponents.Treasury(100));
            SystemContext day1 = ColonyScheduler.get(server).runDay(server, colony, 1);
            helper.assertTrue(job(persons, hobbit).equals(Optional.of(LotrPort.id("farmer"))),
                    "the hobbit isn't the farmer: " + job(persons, hobbit));
            helper.assertTrue(job(persons, man).isPresent() && job(persons, dwarf).isPresent(),
                    "everyone works: " + job(persons, man) + job(persons, dwarf));
            long homes = Society.citizens(server, colony).stream().filter(p -> p.has(WorkComponents.HOME)).count();
            helper.assertTrue(homes == 2, "two beds, two homes: " + homes);
            // the hobbit grew 8 x 1.5 proficiency x (0.5 + 0 skill) = 6 wheat, the courier carried it to the
            // storehouse, the cook baked it (6 x 0.5 = 3 batches wanted, wheat for 2); and one seed: 6 + 1 + 2 made
            int wheat = count(level, farmChest, Items.WHEAT) + count(level, storeChest, Items.WHEAT);
            helper.assertTrue(wheat == 0 && count(level, kitchenChest, Items.BREAD) == 2 && day1.counted(Life.MADE) == 9,
                    "6 wheat grown and baked into 2 loaves: wheat left " + wheat + ", loaves "
                            + count(level, kitchenChest, Items.BREAD) + ", made " + day1.counted(Life.MADE));
            helper.assertTrue(day1.counted(Life.WAGES) == 5 && day1.counted(Life.TAXES) == 3,
                    "wages 2 + 2 + 1, taxes 3: " + day1.counted(Life.WAGES) + ", " + day1.counted(Life.TAXES));
            long coins = colonies.get(colony.id()).orElseThrow().get(CoreComponents.TREASURY).orElseThrow().coins();
            helper.assertTrue(coins == 100 - 5 + 3, "treasury after wages and taxes: " + coins);
            helper.assertTrue(day1.counted(Life.EATEN) == 6 && count(level, storeChest, Items.BREAD) == 14,
                    "three citizens eat two loaves each: " + day1.counted(Life.EATEN));

            // skill grows with every day worked
            ColonyScheduler.get(server).runDay(server, colony, 1);
            double skill = persons.get(hobbit.id()).flatMap(p -> p.get(WorkComponents.JOB)).orElseThrow().skill();
            helper.assertTrue(skill == 2 * LifeSettings.get().skillPerDay(), "skill after two days: " + skill);

            // an empty treasury: workers strike and make nothing
            colonies.set(colony, CoreComponents.TREASURY, new CoreComponents.Treasury(0));
            ColonyScheduler.get(server).runDay(server, colony, 1);
            int before = count(level, farmChest, Items.WHEAT);
            SystemContext strike = ColonyScheduler.get(server).runDay(server, colony, 1);
            helper.assertTrue(strike.counted(Life.MADE) == 0 && count(level, farmChest, Items.WHEAT) == before,
                    "strikers worked: made " + strike.counted(Life.MADE));

            // unhappy two days running: gone
            Person sad = persons.get(man.id()).orElseThrow();
            persons.setQuiet(sad, WorkComponents.NEEDS, new WorkComponents.Needs(0, 0, 1, 5));
            persons.setQuiet(sad, WorkComponents.HOME, null);
            BlockPos s = storeChest;
            ((ChestBlockEntity) level.getBlockEntity(s)).clearContent();
            ((ChestBlockEntity) level.getBlockEntity(kitchenChest)).clearContent();
            SystemContext leave = ColonyScheduler.get(server).runDay(server, colony, 1);
            helper.assertTrue(leave.counted(Life.LEFT) >= 1 && persons.get(man.id()).isEmpty(),
                    "the unhappy man stayed");
            helper.succeed();
        } finally {
            Society.citizens(server, colony).forEach(p -> Persons.get(server).remove(server, p.id()));
            colonies.disband(server, colony, "test");
            BuildingTests.fill(level, x0 - 1, y0, z0 - 1, x0 + 40, y0 + 6, z0 + 8, Blocks.AIR);
        }
    }

    private static Person citizen(Persons persons, Colony colony, ResourceLocation type) {
        Person p = persons.migrated(UUID.randomUUID(), type, type.getPath(), 0, colony.asOwner());
        persons.setQuiet(p, CoreComponents.CITIZEN, new CoreComponents.Citizen(0, false));
        return p;
    }

    private static Optional<ResourceLocation> job(Persons persons, Person p) {
        return persons.get(p.id()).flatMap(x -> x.get(WorkComponents.JOB)).map(WorkComponents.Job::job);
    }

    /** A 5x5 room (3 high) at x0 with its door on the west side and a block in its north-east corner. */
    private static BlockPos room(ServerLevel level, int x0, int y0, int z0, net.minecraft.world.level.block.Block b) {
        BuildingTests.shell(level, x0, y0, z0, x0 + 6, z0 + 6, 3);
        BuildingTests.door(level, new BlockPos(x0, y0 + 1, z0 + 3), Direction.EAST);
        BlockPos at = new BlockPos(x0 + 4, y0 + 1, z0 + 1);
        level.setBlock(at, b.defaultBlockState(), 2);
        return at;
    }

    private static Building add(GameTestHelper helper, ServerPlayer player, ServerLevel level, int x0, int y0, int z0,
            Optional<ResourceLocation> type) {
        var r = ColonyBuildings.add(player, level, new BlockPos(x0, y0 + 1, z0 + 3), type, Optional.empty());
        helper.assertTrue(r.building().isPresent(), "building at " + x0 + ": " + r.message().getString());
        return r.building().get();
    }

    private static void bed(ServerLevel level, BlockPos foot) {
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
        level.setBlock(foot.south(), bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
    }

    private static int count(ServerLevel level, BlockPos chest, net.minecraft.world.item.Item item) {
        return ((ChestBlockEntity) level.getBlockEntity(chest)).countItem(item);
    }

}
