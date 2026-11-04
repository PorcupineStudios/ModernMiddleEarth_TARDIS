package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.npc.Coins;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcData;
import com.porcupinestudios.lotrport.quest.ActiveQuest;
import com.porcupinestudios.lotrport.quest.QuestData;
import com.porcupinestudios.lotrport.quest.QuestPool;
import com.porcupinestudios.lotrport.quest.Quests;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.lotrport.travel.FastTravel;
import com.porcupinestudios.lotrport.travel.Waypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 8 done-when: a player fast travels between waypoints (only to regions they have visited, and not again until
 * the wait is over) and finishes a mini-quest from a Gondor NPC for coins and alignment.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TravelQuestTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation GONDOR = LotrPort.id("gondor");

    private TravelQuestTests() {}

    @GameTest(template = EMPTY)
    public static void everyWaypointAndQuestPoolLoads(GameTestHelper helper) {
        var summary = ContentData.read("summary.json").get(0);
        helper.assertTrue(FastTravel.loadProblems().isEmpty(), "waypoint problems: " + FastTravel.loadProblems());
        helper.assertTrue(QuestData.loadProblems().isEmpty(), "quest problems: " + QuestData.loadProblems());
        int waypoints = summary.get("waypoints").getAsInt();
        int pools = summary.get("quest_pools").getAsInt();
        helper.assertTrue(FastTravel.waypoints().size() == waypoints,
                "loaded " + FastTravel.waypoints().size() + " of " + waypoints + " waypoints");
        helper.assertTrue(QuestData.pools().size() == pools,
                "loaded " + QuestData.pools().size() + " of " + pools + " quest pools");
        helper.assertTrue(FastTravel.settings().cooldownMax() > 0, "no fast travel settings");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void fastTravelBetweenTwoWaypoints(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // two waypoints with no faction (or one the player isn't hostile to), in different regions if possible
        List<Map.Entry<ResourceLocation, Waypoint>> all = new ArrayList<>(FastTravel.waypoints().entrySet());
        all.sort(Map.Entry.comparingByKey());
        helper.assertTrue(all.size() >= 2, "fewer than two waypoints");
        Map.Entry<ResourceLocation, Waypoint> a = all.get(0);
        Map.Entry<ResourceLocation, Waypoint> b = all.stream()
                .filter(e -> !e.getValue().region().equals(a.getValue().region())).findFirst().orElse(all.get(1));

        helper.assertTrue(FastTravel.problem(player, a.getKey()).isPresent(), "travelled to an unvisited region");
        helper.assertFalse(FastTravel.travel(player, a.getKey()), "travelled to an unvisited region");
        FastTravel.unlock(player, a.getValue().region());
        FastTravel.unlock(player, b.getValue().region());

        helper.assertTrue(FastTravel.travel(player, a.getKey()), "could not travel to " + a.getKey() + ": "
                + FastTravel.problem(player, a.getKey()).map(c -> c.getString()).orElse(""));
        at(helper, player, a.getValue());
        helper.assertTrue(FastTravel.waitTicks(player) > 0, "no wait after travelling");
        helper.assertFalse(FastTravel.travel(player, b.getKey()), "travelled again before the wait was over");

        player.setData(LotrRegistries.TRAVEL, FastTravel.data(player).ready());
        helper.assertTrue(FastTravel.travel(player, b.getKey()), "could not travel to " + b.getKey() + ": "
                + FastTravel.problem(player, b.getKey()).map(c -> c.getString()).orElse(""));
        at(helper, player, b.getValue());
        helper.assertTrue(FastTravel.data(player).trips() == 2, "trips: " + FastTravel.data(player).trips());
        helper.succeed();
    }

    private static void at(GameTestHelper helper, ServerPlayer player, Waypoint w) {
        helper.assertTrue(Math.abs(player.getX() - (w.x() + 0.5)) < 1 && Math.abs(player.getZ() - (w.z() + 0.5)) < 1,
                "player at " + player.blockPosition().toShortString() + ", not " + w.x() + ", " + w.z());
    }

    /** As in LOTR, a quest giver only asks while it shows the quest mark, and taking the quest ends its offer. */
    @GameTest(template = EMPTY)
    public static void onlyMarkedGiversOfferQuests(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Optional<ResourceLocation> giverType = NpcData.types().entrySet().stream()
                .filter(e -> e.getValue().faction().equals(Optional.of(GONDOR)) && e.getValue().quests().isPresent()
                        && !e.getValue().trader() && !e.getValue().captain())
                .map(Map.Entry::getKey).sorted().findFirst();
        helper.assertTrue(giverType.isPresent(), "no Gondor NPC gives quests");
        LotrNpc giver = spawn(helper, giverType.get());
        Alignment.set(player, GONDOR, 10);
        giver.clearQuestOffer();
        helper.assertFalse(Quests.interact(player, giver), "an NPC without the quest mark offered a quest");
        helper.assertTrue(giver.startQuestOffer(), "the giver could not start an offer");
        helper.assertTrue(giver.isOfferingQuest() && giver.questOfferColor() != -1, "no quest mark");
        Quests.Offer offer = Quests.roll(player, giver);
        helper.assertTrue(offer.quest().isPresent(), "no quest: " + offer.problem().getString());
        helper.assertTrue(Quests.accept(player, giver, offer.quest().get()), "could not take the offered quest");
        helper.assertFalse(giver.isOfferingQuest(), "the offer stayed open after the quest was taken");
        helper.assertFalse(Quests.accept(player, giver, offer.quest().get()), "took a second quest without a mark");
        helper.assertTrue(Quests.log(player).active().size() == 1, "quest not taken");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void finishAMiniQuestFromGondor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Optional<ResourceLocation> giverType = NpcData.types().entrySet().stream()
                .filter(e -> e.getValue().faction().equals(Optional.of(GONDOR)) && e.getValue().quests().isPresent()
                        && !e.getValue().trader() && !e.getValue().captain())
                .map(Map.Entry::getKey).sorted().findFirst();
        helper.assertTrue(giverType.isPresent(), "no Gondor NPC gives quests");
        LotrNpc giver = spawn(helper, giverType.get());

        Alignment.set(player, GONDOR, -50);
        helper.assertTrue(Quests.offer(player, giver).quest().isEmpty(), "an enemy of Gondor was given a quest");

        Alignment.set(player, GONDOR, 10);
        Quests.Offer offer = Quests.offer(player, giver);
        helper.assertTrue(offer.quest().isPresent(), "no quest: " + offer.problem().getString());
        ActiveQuest quest = offer.quest().get();
        helper.assertFalse(Quests.handIn(player, 0), "handed in a quest that isn't done");

        switch (quest.kind()) {
            case COLLECT -> player.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.get(quest.target()),
                    quest.count()));
            case KILL_FACTION -> {
                for (int i = 0; i < quest.count(); i++) {
                    Zombie enemy = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
                    FactionKills.setFaction(enemy, quest.target());
                    Quests.killed(player, enemy);
                    enemy.discard();
                }
            }
            case KILL_NPC -> {
                for (int i = 0; i < quest.count(); i++) {
                    LivingEntity enemy = spawn(helper, quest.target());
                    Quests.killed(player, enemy);
                    enemy.discard();
                }
            }
        }
        int coins = Coins.count(player);
        float alignment = Alignment.get(player, GONDOR);
        if (!Quests.handIn(player, 0)) {
            helper.fail("could not hand in " + quest.kind() + " " + quest.target() + " x" + quest.count()
                    + " (progress " + Quests.progress(player, Quests.log(player).active().get(0)) + ")");
        }
        helper.assertTrue(Coins.count(player) == coins + quest.coins(), "reward coins: "
                + (Coins.count(player) - coins) + " not " + quest.coins());
        helper.assertTrue(Alignment.get(player, GONDOR) > alignment, "no alignment reward");
        helper.assertTrue(Quests.log(player).active().isEmpty() && Quests.log(player).completed() == 1,
                "the quest is still open");
        if (quest.kind() == QuestPool.Kind.COLLECT) {
            helper.assertTrue(player.getInventory().countItem(BuiltInRegistries.ITEM.get(quest.target())) == 0,
                    "the items were not taken");
        }
        helper.succeed();
    }

    private static LotrNpc spawn(GameTestHelper helper, ResourceLocation type) {
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        Optional<LotrNpc> npc = LotrNpc.spawn(helper.getLevel(), type, pos.x, pos.y, pos.z);
        if (npc.isEmpty()) {
            helper.fail("could not spawn " + type);
        }
        return npc.get();
    }
}
