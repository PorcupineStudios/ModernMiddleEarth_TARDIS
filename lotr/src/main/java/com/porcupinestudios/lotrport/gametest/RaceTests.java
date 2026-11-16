package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.player.Races;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Player races: the shipped races load, and a race's size and reach go on the player and come off again. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RaceTests {
    private RaceTests() {}

    @GameTest(template = "empty")
    public static void hobbitIsSmallWithShortReach(GameTestHelper helper) {
        helper.assertTrue(Races.all().size() >= 6, "player_race files missing: " + Races.all().keySet());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        player.setData(LotrRegistries.RACE, "lotr_port:hobbit");
        Races.apply(player);
        helper.assertTrue(Math.abs(player.getScale() - 0.667f) < 0.01f, "hobbit scale " + player.getScale());
        helper.assertTrue(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) < reach, "reach not shorter");
        helper.assertTrue(player.getEyeHeight() < 1.2f, "eye height " + player.getEyeHeight());
        helper.assertTrue(Races.settlerFactor(player.getUUID(), "hobbit") > 1.0, "no settler bias for hobbits");
        helper.assertTrue(Races.settlerFactor(player.getUUID(), "orc") == 1.0, "settler bias for orcs");
        player.setData(LotrRegistries.RACE, "lotr_port:man");
        Races.apply(player); // switching race removes the hobbit's modifiers
        helper.assertTrue(player.getScale() == 1f, "man scale " + player.getScale());
        helper.assertTrue(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) == reach, "reach not restored");
        helper.succeed();
    }
}
