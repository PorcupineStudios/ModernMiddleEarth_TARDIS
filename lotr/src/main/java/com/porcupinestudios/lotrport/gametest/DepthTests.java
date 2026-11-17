package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.market.Research;
import com.porcupinestudios.lotrport.colony.people.Council;
import com.porcupinestudios.lotrport.colony.tech.TechDef;
import com.porcupinestudios.lotrport.colony.tech.Techs;
import com.porcupinestudios.lotrport.colony.work.Modifiers;
import com.porcupinestudios.lotrport.colony.work.Production;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.ColonyScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Colony depth (2026-11-17): production picks recipes by orders and targets, the tech tree and its forks, edicts and
 * unity, milestones.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DepthTests {
    private static final ResourceLocation MAN = LotrPort.id("gondor_man");

    private DepthTests() {}

    @GameTest(template = "empty", batch = "buildings", timeoutTicks = 200)
    public static void ordersAndTargetsPickTheRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int x0 = 7_500_000 + level.random.nextInt(10_000) * 512;
        int z0 = 7_000_000;
        int y0 = 150;
        for (int cx = (x0 - 16) >> 4; cx <= (x0 + 32) >> 4; cx++) {
            for (int cz = (z0 - 16) >> 4; cz <= (z0 + 16) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        Colonies colonies = Colonies.get(server);
        Colony colony = colonies.found(player, "Depth Test", new BlockPos(x0 + 20, y0, z0 - 8), LotrPort.id("hobbit"))
                .colony().orElseThrow();
        try {
            BlockPos chest = LifeTests.room(level, x0, y0, z0, Blocks.CHEST);
            level.setBlock(chest.east(), Blocks.FURNACE.defaultBlockState(), 2);
            ChestBlockEntity box = (ChestBlockEntity) level.getBlockEntity(chest);
            box.setItem(0, new ItemStack(Items.WHEAT, 30));
            box.setItem(1, new ItemStack(Items.BEEF, 10));
            box.setItem(2, new ItemStack(Items.POTATO, 10));
            LifeTests.add(helper, player, level, x0, y0, z0, Optional.of(LotrPort.id("kitchen")));
            Persons persons = Persons.get(server);
            LifeTests.citizen(persons, colony, MAN);
            colonies.set(colony, CoreComponents.TREASURY, new CoreComponents.Treasury(100));
            Colony now = colonies.get(colony.id()).orElseThrow();

            // an order for 2 steaks comes first, then the target of 2 baked potatoes; bread has none, so the cook
            // stops once both are met
            Production.setOrder(server, now, Items.COOKED_BEEF.builtInRegistryHolder().key().location(), 2);
            Production.setTarget(server, now, Items.BAKED_POTATO.builtInRegistryHolder().key().location(), 2);
            Production.setTarget(server, now, Items.BREAD.builtInRegistryHolder().key().location(), 1);
            ColonyScheduler.get(server).runDay(server, colonies.get(colony.id()).orElseThrow(), 1);
            int potato = LifeTests.count(level, chest, Items.BAKED_POTATO);
            int bread = LifeTests.count(level, chest, Items.BREAD);
            // (citizens may eat a steak, so the chest isn't counted: the order is)
            Production.Plan plan = Production.plan(server, colonies.get(colony.id()).orElseThrow());
            helper.assertTrue(plan.orders().isEmpty(), "the order is done: " + plan.orders());
            helper.assertTrue(potato <= 2 && bread <= 1, "targets are not overshot: potato " + potato + ", bread " + bread);
            helper.succeed();
        } finally {
            Society.citizens(server, colony).forEach(p -> Persons.get(server).remove(server, p.id()));
            colonies.disband(server, colony, "test");
            BuildingTests.fill(level, x0 - 1, y0, z0 - 1, x0 + 8, y0 + 6, z0 + 8, Blocks.AIR);
        }
    }

    @GameTest(template = "empty", batch = "buildings", timeoutTicks = 200)
    public static void techForksEdictsAndMilestones(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        helper.assertTrue(TechDef.loadProblems().isEmpty(), "tech data: " + TechDef.loadProblems());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int x0 = 8_000_000 + level.random.nextInt(10_000) * 512;
        Colonies colonies = Colonies.get(server);
        Colony colony = colonies.found(player, "Tech Test", new BlockPos(x0, 150, 7_000_000), LotrPort.id("hobbit"))
                .colony().orElseThrow();
        try {
            Persons persons = Persons.get(server);
            List<Person> people = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                people.add(LifeTests.citizen(persons, colony, MAN));
            }
            ResourceLocation civics = LotrPort.id("civics");
            ResourceLocation elders = LotrPort.id("council_of_elders");
            ResourceLocation iron = LotrPort.id("iron_rule");
            helper.assertTrue(Techs.whyNot(server, colony, civics).isPresent(), "civics is free without points");
            Research.add(server, colony, Research.BASIC, 100);
            Research.add(server, colony, LotrPort.id("scholarly"), 100);
            helper.assertTrue(Techs.whyNot(server, colony, elders).isPresent(), "elders before civics");
            Techs.research(server, colony, civics);
            Techs.research(server, colony, iron);
            Colony now = colonies.get(colony.id()).orElseThrow();
            helper.assertTrue(Techs.has(now, civics) && Techs.has(now, iron), "researched: " + Techs.state(server, now));
            helper.assertTrue(Techs.whyNot(server, now, elders).isPresent(), "the fork stays open");
            helper.assertTrue(Production.unlocked.test(now, civics), "recipes see the tech");
            double work = Modifiers.work(server, now);
            helper.assertTrue(Math.abs(work - 1.08) < 1e-6, "iron rule work x1.08 at unity 50: " + work);

            // edicts: one slot at 5 citizens; long hours and day of rest exclude each other
            colonies.set(now, CoreComponents.TREASURY, new CoreComponents.Treasury(100));
            now = colonies.get(colony.id()).orElseThrow();
            Council.proclaim(server, now, LotrPort.id("long_hours"));
            now = colonies.get(colony.id()).orElseThrow();
            helper.assertTrue(Council.state(server, now).edicts().equals(List.of(LotrPort.id("long_hours"))),
                    "long hours in force: " + Council.state(server, now).edicts());
            helper.assertTrue(Council.whyNot(server, now, LotrPort.id("day_of_rest")).isPresent(), "a second edict");

            // a day: the hamlet milestone (5 citizens) pays 50 coins
            ColonyScheduler.get(server).runDay(server, now, 1);
            now = colonies.get(colony.id()).orElseThrow();
            helper.assertTrue(Techs.state(server, now).milestones().containsKey(LotrPort.id("hamlet")),
                    "hamlet reached: " + Techs.state(server, now).milestones());
            helper.succeed();
        } finally {
            Society.citizens(server, colony).forEach(p -> Persons.get(server).remove(server, p.id()));
            colonies.disband(server, colony, "test");
        }
    }
}
