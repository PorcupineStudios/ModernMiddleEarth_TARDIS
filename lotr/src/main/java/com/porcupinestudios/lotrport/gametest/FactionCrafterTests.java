package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.crafting.FactionRecipe;
import com.porcupinestudios.lotrport.faction.Alignment;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Faction mechanical crafters (compat/create, converter/create_crafters.py): a faction recipe's alignment need, and,
 * with Create loaded (the CI test server runs without it; locally {@code -PwithCreate}), that every crafter is a
 * mechanical crafter to Create.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FactionCrafterTests {
    private FactionCrafterTests() {}

    @GameTest(template = "empty")
    public static void alignmentNeedIsPositiveAlignment(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        var gondor = LotrPort.id("gondor");
        var need = new FactionRecipe.Need(gondor, 0f);
        Alignment.set(player, gondor, 0f);
        helper.assertFalse(need.met(player), "alignment 0 counts as positive");
        Alignment.set(player, gondor, 10f);
        helper.assertTrue(need.met(player), "alignment 10 isn't enough");
        Alignment.set(player, gondor, -10f);
        helper.assertFalse(need.met(player), "negative alignment is enough");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void factionCraftersAreCreateCrafters(GameTestHelper helper) {
        if (ModList.get().isLoaded("create")) {
            String problem = com.porcupinestudios.lotrport.compat.create.CrafterChecks.check();
            helper.assertTrue(problem == null, String.valueOf(problem));
        }
        helper.succeed();
    }
}
