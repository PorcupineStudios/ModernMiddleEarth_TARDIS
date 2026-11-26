package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.FactionDef;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcData;
import com.porcupinestudios.lotrport.npc.NpcType;
import com.porcupinestudios.lotrport.player.Races;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The goblins (2026-11-26, Aaron: a new faction with its own model, which players can be too). Done when: the faction
 * loads, friendly with Gundabad and at war with dwarves and elves; its NPCs are drawn on the goblin body with 64x64
 * skins and fight for it; the Goblin race puts a player on that body in their own skin, at goblin size.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoblinFactionTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation GOBLINS = LotrPort.id("goblins");

    private GoblinFactionTests() {}

    @GameTest(template = EMPTY)
    public static void goblinsAreAFactionOfTheMistyMountains(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FactionDef def = Factions.definitions().get(GOBLINS);
        helper.assertTrue(def != null, "no faction lotr_port:goblins");
        helper.assertTrue(def.playable() && def.pledgeRank().isPresent(), "goblins can't be pledged to");
        helper.assertTrue(Factions.relation(server, GOBLINS, LotrPort.id("gundabad")).isGood(),
                "goblins and Gundabad aren't friends");
        for (String enemy : List.of("durins_folk", "high_elf", "wood_elf", "dale", "ranger_north")) {
            helper.assertTrue(Factions.relation(server, GOBLINS, LotrPort.id(enemy)).isBad(),
                    "goblins aren't enemies of " + enemy);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void goblinNpcsUseTheGoblinBody(GameTestHelper helper) {
        for (String id : List.of("goblin", "goblin_archer", "great_goblin")) {
            NpcType type = NpcData.type(LotrPort.id(id)).orElse(null);
            helper.assertTrue(type != null, "no NPC type " + id);
            helper.assertTrue(type.faction().filter(GOBLINS::equals).isPresent(), id + " isn't a goblin");
            helper.assertTrue("goblin".equals(type.model()) && type.skinHeight() == 64,
                    id + " isn't drawn on the goblin body (model " + type.model() + ", skin height "
                            + type.skinHeight() + ")");
            helper.assertTrue(!type.skins().isEmpty(), id + " has no skins");
        }
        ThrownTests.inLoadedArea(helper, 4, 4, () -> {
            var at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5));
            LotrNpc npc = LotrNpc.spawn(helper.getLevel(), LotrPort.id("goblin"), at.x, at.y, at.z).orElseThrow();
            npc.setNoAi(true);
            helper.assertTrue(FactionKills.factionOf(npc).filter(GOBLINS::equals).isPresent(),
                    "a spawned goblin doesn't fight for the goblins");
            helper.assertTrue(npc.tallSkin() && "goblin".equals(npc.model()), "a spawned goblin isn't on the body");
            npc.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void playersCanBeGoblins(GameTestHelper helper) {
        ResourceLocation id = LotrPort.id("goblin");
        Races.Def def = Races.all().get(id);
        helper.assertTrue(def != null, "no player race lotr_port:goblin");
        helper.assertTrue(def.model().filter("goblin"::equals).isPresent(), "the goblin race isn't on the goblin body");
        helper.assertTrue(def.ownSkin(), "goblin players don't keep their own skin");
        helper.assertTrue(def.factions().contains(GOBLINS), "the goblin race doesn't belong to the goblins");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Races.set(player, id);
        helper.assertTrue(Races.of(player).filter(id::equals).isPresent(), "the race didn't stick");
        helper.assertTrue(Math.abs(player.getScale() - def.scale()) < 0.01,
                "a goblin player is " + player.getScale() + " high, not " + def.scale());
        Races.set(player, null);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}
