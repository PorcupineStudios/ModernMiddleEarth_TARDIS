package com.porcupinestudios.lotrport.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.Diplomacy;
import com.porcupinestudios.lotrport.faction.Faction;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.faction.Pledges;
import com.porcupinestudios.lotrport.faction.Realms;
import com.porcupinestudios.lotrport.faction.Relation;
import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 4 done-when: a kill raises alignment with the right factions, and two factions change diplomatic state by
 * command. Also: every LOTR faction loads from data, pledging needs the pledge rank, and a realm is a faction.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FactionTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation GONDOR = LotrPort.id("gondor");
    private static final ResourceLocation ROHAN = LotrPort.id("rohan");
    private static final ResourceLocation MORDOR = LotrPort.id("mordor");

    private FactionTests() {}

    @GameTest(template = EMPTY)
    public static void everyFactionLoadsFromData(GameTestHelper helper) {
        int expected = ContentData.read("summary.json").get(0).get("factions").getAsInt();
        int loaded = Factions.definitions().size();
        helper.assertTrue(Factions.loadProblems().isEmpty(), "faction data problems: " + Factions.loadProblems());
        helper.assertTrue(loaded == expected, "loaded " + loaded + " of " + expected + " factions");
        helper.assertTrue(Factions.relation(helper.getLevel().getServer(), GONDOR, MORDOR).isBad(),
                "Gondor and Mordor are not enemies");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void killingAnOrcRaisesAlignmentWithItsEnemies(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var here = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5));
        player.moveTo(here.x, here.y, here.z); // the mock player starts at the world's origin, not in the test
        ResourceLocation highElf = LotrPort.id("high_elf");
        ResourceLocation rohan = LotrPort.id("rohan");
        // far from every faction's lands and people: nobody hears of the kill
        Zombie first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        FactionKills.setFaction(first, MORDOR);
        first.hurt(player.damageSources().playerAttack(player), 1000f);
        helper.assertTrue(first.isDeadOrDying(), "the first orc did not die");
        // (other tests run beside this one, so a faction counts as out of reach only if no NPC of it is near)
        for (Faction f : Factions.all(server)) {
            if (com.porcupinestudios.lotrport.faction.FactionInfluence.reach(player, f.id())
                    == com.porcupinestudios.lotrport.faction.FactionInfluence.Reach.NONE) {
                helper.assertTrue(Alignment.get(player, f.id()) == 0, f.id() + " changed with no one near");
            }
        }
        float gondorBefore = Alignment.get(player, GONDOR);
        float elfBefore = Alignment.get(player, highElf);
        float mordorBefore = Alignment.get(player, MORDOR);
        boolean rohanAway = com.porcupinestudios.lotrport.faction.FactionInfluence.reach(player, rohan)
                == com.porcupinestudios.lotrport.faction.FactionInfluence.Reach.NONE;
        float rohanBefore = Alignment.get(player, rohan);
        // with a Gondor soldier, a High Elf and a Mordor orc standing by, those three factions hear of it
        for (String type : new String[] { "gondor_soldier", "high_elf", "mordor_orc" }) {
            var at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5));
            com.porcupinestudios.lotrport.npc.LotrNpc.spawn(helper.getLevel(), LotrPort.id(type), at.x, at.y, at.z)
                    .orElseThrow().setNoAi(true);
        }
        Zombie orc = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        FactionKills.setFaction(orc, MORDOR);
        orc.hurt(player.damageSources().playerAttack(player), 1000f);
        helper.assertTrue(orc.isDeadOrDying(), "the orc did not die");
        helper.assertTrue(Alignment.get(player, GONDOR) > gondorBefore, "no alignment with Gondor for killing an orc");
        helper.assertTrue(Alignment.get(player, highElf) > elfBefore, "no alignment with the High Elves near one");
        helper.assertTrue(Alignment.get(player, MORDOR) < mordorBefore, "no penalty with Mordor for killing its orc");
        helper.assertTrue(!rohanAway || Alignment.get(player, rohan) == rohanBefore,
                "Rohan heard of it with no Rohirrim near");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void factionsChangeRelationByCommand(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Relation start = Factions.defaultRelation(server, GONDOR, ROHAN);
        helper.assertTrue(start.isGood(), "Gondor and Rohan start as " + start.getSerializedName());
        run(server, "lotr faction relation lotr_port:gondor lotr_port:rohan enemy");
        helper.assertTrue(Factions.relation(server, ROHAN, GONDOR) == Relation.ENEMY, "the war was not declared");
        run(server, "lotr faction relation lotr_port:gondor lotr_port:rohan default");
        helper.assertTrue(Factions.relation(server, GONDOR, ROHAN) == start, "the relation did not go back");
        run(server, "lotr faction vassal lotr_port:rohan lotr_port:gondor");
        helper.assertTrue(Diplomacy.get(server).overlord(ROHAN).equals(Optional.of(GONDOR)), "Rohan is no vassal");
        run(server, "lotr faction vassal lotr_port:rohan none");
        helper.assertTrue(Diplomacy.get(server).overlord(ROHAN).isEmpty(), "Rohan is still a vassal");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pledgingNeedsThePledgeRank(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        float needed = Factions.get(server, ROHAN).flatMap(Faction::pledgeAlignment).orElse(-1f);
        helper.assertTrue(needed > 0, "Rohan has no pledge rank");
        Alignment.set(player, ROHAN, needed - 1);
        helper.assertTrue(Pledges.pledge(player, ROHAN) == Pledges.Result.NOT_ENOUGH_ALIGNMENT, "pledged too early");
        Alignment.set(player, ROHAN, needed);
        helper.assertTrue(Pledges.pledge(player, ROHAN) == Pledges.Result.PLEDGED, "could not pledge");
        boolean condition = Conditions.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"lotr_port:pledged\",\"faction\":\"lotr_port:rohan\"}"))
                .getOrThrow().test(ConditionContext.of(player));
        helper.assertTrue(condition, "the pledged condition is false");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aRealmIsAFaction(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Realms.Realm realm = Realms.get(server).found(server, "Test March", Optional.of(player.getUUID()),
                Optional.of(GONDOR), 0x3355AA);
        helper.assertTrue(Factions.get(server, realm.id()).map(Faction::realm).orElse(false), "realm not a faction");
        helper.assertTrue(Factions.relation(server, realm.id(), MORDOR) == Factions.relation(server, GONDOR, MORDOR),
                "the realm does not share Gondor's relation with Mordor");
        Alignment.set(player, GONDOR, Factions.get(server, GONDOR).flatMap(Faction::pledgeAlignment).orElse(0f));
        helper.assertTrue(Pledges.pledge(player, GONDOR) == Pledges.Result.PLEDGED, "could not pledge to Gondor");
        helper.assertTrue(Diplomacy.get(server).overlord(realm.id()).equals(Optional.of(GONDOR)),
                "pledging did not make the realm Gondor's vassal");
        Pledges.unpledge(player);
        helper.assertTrue(Diplomacy.get(server).overlord(realm.id()).isEmpty(), "unpledging kept the vassal link");
        helper.succeed();
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
