package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.market.Market;
import com.porcupinestudios.lotrport.colony.market.Research;
import com.porcupinestudios.lotrport.colony.market.StoreLock;
import com.porcupinestudios.lotrport.colony.work.Stores;
import com.porcupinestudios.lotrport.npc.Coins;
import com.porcupinestudios.lotrport.society.CoreComponents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The colony market (2026-11-12): supply-and-demand prices, daily intake limits, research upgrades, piped stores. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketTests {
    private MarketTests() {}

    @GameTest(template = "empty")
    public static void theMarketCapsWhatItTakesAndResearchRaisesTheCap(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Colonies colonies = Colonies.get(server);
        UUID ruler = UUID.randomUUID();
        BlockPos hall = new BlockPos(800000 + (int) (Math.abs(ruler.getLeastSignificantBits()) % 50000), 70, -900000);
        Colony c = colonies.foundAi(server, UUID.randomUUID(), ruler, "Market Test", helper.getLevel().dimension(),
                hall, LotrPort.id("gondor"), 0).orElseThrow();
        ResourceLocation bread = ResourceLocation.parse("minecraft:bread");
        ResourceLocation wheat = ResourceLocation.parse("minecraft:wheat");
        helper.assertTrue(Market.buyCost(bread, 4, 1) > Market.buyCost(bread, 1000, 1),
                "scarce bread should cost more than plentiful bread");
        colonies.set(colonies.get(c.id()).orElseThrow(), CoreComponents.TREASURY, new CoreComponents.Treasury(10000));
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(Items.BREAD, 40));
        int ceiling = Market.ceiling(0);
        Market.Answer sold = Market.sell(player, c, bread, Integer.MAX_VALUE);
        helper.assertTrue(sold.ok(), "selling failed: " + sold.message().getString());
        int left = player.getInventory().countItem(Items.BREAD);
        helper.assertTrue(left == 40 - ceiling, "the market took " + (40 - left) + ", cap " + ceiling);
        helper.assertTrue(Coins.count(player) > 0, "no coins for the bread");
        helper.assertTrue(!Market.sell(player, c, bread, 1).ok(), "sold past today's limit");
        // a lower limit of its own; research points raise the ceiling
        Market.setLimit(server, c, wheat, 4);
        helper.assertTrue(Market.limit(Market.state(server, c), wheat) == 4, "wheat limit not set");
        helper.assertTrue(!Market.upgrade(server, c).ok(), "upgraded without research points");
        // points of another tier don't pay for a basic upgrade
        Research.add(server, c, LotrPort.id("scholarly"), 1000);
        helper.assertTrue(!Market.upgrade(server, c).ok(), "scholarly points paid a basic upgrade");
        Research.add(server, c, Research.BASIC, Market.upgradeCost(0));
        helper.assertTrue(Market.upgrade(server, c).ok(), "upgrade with enough points failed");
        Market.State st = Market.state(server, c);
        helper.assertTrue(st.level() == 1 && Market.room(st, bread) == Market.ceiling(1) - ceiling,
                "after the upgrade: level " + st.level() + ", room " + Market.room(st, bread));
        // tiers: basic is free, higher ones cost goods and need a bigger colony
        helper.assertTrue(Research.tiers().keySet().containsAll(java.util.List.of(Research.BASIC,
                LotrPort.id("scholarly"), LotrPort.id("advanced"))), "tiers: " + Research.tiers().keySet());
        helper.assertTrue(Research.tier(Research.BASIC).cost().isEmpty()
                && !Research.tier(LotrPort.id("scholarly")).cost().isEmpty(), "tier costs are wrong");
        helper.assertTrue(Research.whyNot(server, c, LotrPort.id("scholarly")).isPresent(),
                "an empty colony may do scholarly research");
        helper.assertTrue(Market.upgradeTier(5).equals(LotrPort.id("scholarly")), "level 5 should cost scholarly points");
        // buying back costs coins and leaves the stores
        Colony now = colonies.get(c.id()).orElseThrow();
        int before = new Stores(server, helper.getLevel(), now).count(bread, null);
        helper.assertTrue(Market.buy(player, now, bread, 1).ok(), "couldn't buy bread back");
        int after = new Stores(server, helper.getLevel(), colonies.get(c.id()).orElseThrow()).count(bread, null);
        helper.assertTrue(after == before - 1, "stores " + before + " -> " + after);
        // a store a hopper touches doesn't count
        helper.setBlock(1, 1, 1, Blocks.CHEST);
        helper.assertTrue(!StoreLock.fed(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))), "fed alone");
        helper.setBlock(1, 2, 1, Blocks.HOPPER);
        helper.assertTrue(StoreLock.fed(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))),
                "a hopper on a chest should mark it fed");
        colonies.disband(server, c, "test");
        helper.succeed();
    }
}
