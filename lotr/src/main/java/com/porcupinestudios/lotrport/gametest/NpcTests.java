package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.npc.Coins;
import com.porcupinestudios.lotrport.npc.Hiring;
import com.porcupinestudios.lotrport.npc.LotrEntities;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcData;
import com.porcupinestudios.lotrport.npc.People;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 6 done-when: a player hires, trades with and commands NPCs from two factions (Gondor and Mordor); a hired NPC
 * keeps its record across a restart (saved and loaded again), and a random orc leaves none.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation GONDOR = LotrPort.id("gondor");
    private static final ResourceLocation MORDOR = LotrPort.id("mordor");

    private NpcTests() {}

    private static LotrNpc spawn(GameTestHelper helper, String type) {
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        Optional<LotrNpc> npc = LotrNpc.spawn(helper.getLevel(), LotrPort.id(type), pos.x, pos.y, pos.z);
        if (npc.isEmpty()) {
            helper.fail("could not spawn " + type);
        }
        return npc.get();
    }

    @GameTest(template = EMPTY)
    public static void everyNpcTypeLoadsFromData(GameTestHelper helper) {
        int expected = ContentData.read("summary.json").get(0).get("npcs").getAsInt();
        helper.assertTrue(NpcData.loadProblems().isEmpty(), "NPC data problems: " + NpcData.loadProblems());
        helper.assertTrue(NpcData.types().size() == expected,
                "loaded " + NpcData.types().size() + " of " + expected + " NPC types");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void npcsTalkByMood(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LotrNpc hobbit = spawn(helper, "hobbit");
        ResourceLocation shire = LotrPort.id("hobbit");
        ResourceLocation bank = hobbit.npcType().speech().orElseThrow();

        Alignment.set(player, shire, 100);
        helper.assertTrue(hobbit.speak(player), "a hobbit had nothing to say to a friend");
        helper.assertTrue(!hobbit.speech().isEmpty() && !hobbit.speech().contains("#"),
                "friendly line not filled in: '" + hobbit.speech() + "'");

        Alignment.set(player, shire, -100);
        helper.assertTrue(hobbit.speak(player), "a hobbit had nothing to say to an enemy");
        String name = player.getGameProfile().getName();
        helper.assertTrue(NpcData.speech(bank, "hostile").stream()
                        .anyMatch(l -> l.replace("#", name).replace("@", "here").equals(hobbit.speech())),
                "enemy got a line that isn't hostile: '" + hobbit.speech() + "'");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void hiringFromTwoFactions(GameTestHelper helper) {
        hire(helper, "gondorian_captain", GONDOR);
        hire(helper, "mordor_orc_mercenary_captain", MORDOR);
        helper.succeed();
    }

    private static LotrNpc hire(GameTestHelper helper, String captainType, ResourceLocation faction) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LotrNpc captain = spawn(helper, captainType);
        int cost = Hiring.units(captain).get(0).cost();

        Alignment.set(player, faction, 0);
        Coins.give(player, 1000);
        helper.assertTrue(Hiring.hire(player, captain, 0).unit().isEmpty(),
                captainType + " hired out a unit at 0 alignment");

        Alignment.set(player, faction, 1000);
        Hiring.Result result = Hiring.hire(player, captain, 0);
        helper.assertTrue(result.unit().isPresent(), captainType + ": " + result.problem().getString());
        LotrNpc unit = result.unit().get();
        helper.assertTrue(unit.isOwnedBy(player), "the unit is not the player's");
        helper.assertTrue(unit.faction().equals(Optional.of(faction)), "the unit fights for " + unit.faction());
        helper.assertTrue(unit.command() == LotrNpc.Command.FOLLOW, "a new unit does not follow");
        helper.assertTrue(Coins.count(player) == 1000 - cost, "paid " + (1000 - Coins.count(player)) + " not " + cost);
        helper.assertTrue(People.get(server).get(unit.getUUID()).flatMap(People.Person::owner)
                .equals(Optional.of(player.getUUID())), "no person record for the hired unit");

        // commanding: sneak + right-click switches between guarding the spot and following
        player.setShiftKeyDown(true);
        player.interactOn(unit, InteractionHand.MAIN_HAND);
        helper.assertTrue(unit.command() == LotrNpc.Command.GUARD, "the unit did not start guarding");
        player.interactOn(unit, InteractionHand.MAIN_HAND);
        helper.assertTrue(unit.command() == LotrNpc.Command.FOLLOW, "the unit did not follow again");
        player.setShiftKeyDown(false);
        helper.assertFalse(unit.isEnemy(player), "the unit would attack its owner");
        return unit;
    }

    @GameTest(template = EMPTY)
    public static void tradingWithTwoFactions(GameTestHelper helper) {
        trade(helper, "gondor_blacksmith", GONDOR);
        trade(helper, "mordor_orc_trader", MORDOR);
        helper.succeed();
    }

    private static void trade(GameTestHelper helper, String traderType, ResourceLocation faction) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LotrNpc trader = spawn(helper, traderType);
        Alignment.set(player, faction, -10);
        helper.assertFalse(trader.willDealWith(player), traderType + " trades at negative alignment");
        Alignment.set(player, faction, 100);
        helper.assertTrue(trader.willDealWith(player), traderType + " won't trade at 100 alignment");

        MerchantOffer buy = null;
        MerchantOffer sell = null;
        for (MerchantOffer offer : trader.getOffers()) {
            if (Coins.value(offer.getCostA()) > 0 && buy == null) {
                buy = offer;
            } else if (Coins.value(offer.getResult()) > 0 && sell == null) {
                sell = offer;
            }
        }
        helper.assertTrue(buy != null, traderType + " sells nothing for coins");
        helper.assertTrue(sell != null, traderType + " buys nothing for coins");

        // pay what the trading screen would ask and take the goods, as the result slot does
        ItemStack a = buy.getCostA().copy();
        ItemStack b = buy.getCostB().copy();
        helper.assertTrue(buy.satisfiedBy(a, b), traderType + ": its price does not satisfy its own offer");
        helper.assertTrue(buy.take(a, b), traderType + ": could not take the price");
        helper.assertTrue(a.isEmpty() && b.isEmpty(), traderType + ": price left over after paying");
        trader.notifyTrade(buy);
        helper.assertTrue(buy.getUses() == 1, traderType + ": the trade was not counted");
        helper.assertFalse(buy.getResult().isEmpty(), traderType + ": the goods are empty");
    }

    @GameTest(template = EMPTY)
    public static void aHiredUnitKeepsItsRecordAndARandomOrcLeavesNone(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        LotrNpc unit = hire(helper, "gondorian_captain", GONDOR);
        unit.setCommand(LotrNpc.Command.GUARD);

        // a restart: the world's records and the entity are saved and read back
        CompoundTag saved = People.get(server).save(new CompoundTag(), server.registryAccess());
        People reloaded = People.load(saved, server.registryAccess());
        helper.assertTrue(reloaded.get(unit.getUUID()).isPresent(), "the record did not survive saving");
        helper.assertTrue(reloaded.get(unit.getUUID()).get().type().equals(unit.typeId()), "the record lost its type");

        CompoundTag entity = unit.saveWithoutId(new CompoundTag());
        LotrNpc copy = LotrEntities.NPC.get().create(helper.getLevel());
        helper.assertTrue(copy != null, "could not make an NPC");
        copy.load(entity);
        helper.assertTrue(copy.owner().equals(unit.owner()), "the unit forgot its owner");
        helper.assertTrue(copy.command() == LotrNpc.Command.GUARD, "the unit forgot its orders");
        helper.assertTrue(copy.typeId().equals(unit.typeId()), "the unit forgot its type");
        helper.assertTrue(copy.faction().equals(unit.faction()), "the unit forgot its faction");

        LotrNpc orc = spawn(helper, "mordor_orc");
        helper.assertTrue(People.get(server).get(orc.getUUID()).isEmpty(), "a random orc got a person record");
        helper.assertTrue(orc.owner().isEmpty() && orc.command() == LotrNpc.Command.NONE, "a random orc is hired");
        helper.assertFalse(orc.getMainHandItem().isEmpty(), "the orc spawned without a weapon");
        helper.succeed();
    }
}
