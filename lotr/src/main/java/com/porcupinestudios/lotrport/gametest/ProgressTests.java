package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrComponents;
import com.porcupinestudios.lotrport.npc.InvasionSpawner;
import com.porcupinestudios.lotrport.progress.Achievements;
import com.porcupinestudios.lotrport.progress.Calendar;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The LOTR tables from the reflection dump (converter/extra.py): modifiers as enchantments, achievements as
 * advancements, drinks, the calendar and invasions.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProgressTests {
    private ProgressTests() {}

    @GameTest(template = "empty")
    public static void modifiersAreEnchantments(GameTestHelper helper) {
        var reg = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        long n = reg.keySet().stream().filter(k -> k.getNamespace().equals(LotrPort.MOD_ID)).count();
        helper.assertTrue(n >= 50, "only " + n + " LOTR enchantments loaded");
        helper.assertTrue(reg.containsKey(LotrPort.id("strong1")) && reg.containsKey(LotrPort.id("bane_orc")),
                "keen / orc-bane missing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void achievementsAreAdvancements(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer p = (ServerPlayer) helper.makeMockServerPlayerInLevel();
        helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(LotrPort.id("general/kill_orc")) != null,
                "advancement general/kill_orc missing");
        helper.assertTrue(Achievements.grant(p, "killOrc") && Achievements.has(p, "killOrc"), "could not grant");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void drinksFeedAndReturnTheVessel(GameTestHelper helper) {
        @SuppressWarnings("removal")
        Player p = helper.makeMockServerPlayerInLevel();
        ((ServerPlayer) p).setGameMode(GameType.SURVIVAL);
        ItemStack miruvor = new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("miruvor")));
        miruvor.set(LotrComponents.VESSEL.get(), "goblet_gold");
        ItemStack back = miruvor.getItem().finishUsingItem(miruvor, helper.getLevel(), p);
        helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST), "miruvor gave no strength");
        helper.assertTrue(BuiltInRegistries.ITEM.getKey(back.getItem()).getPath().equals("golden_goblet"),
                "got back " + BuiltInRegistries.ITEM.getKey(back.getItem()));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void calendarCountsShireReckoning(GameTestHelper helper) {
        int[] d = Calendar.date(helper.getLevel());
        helper.assertTrue(d[0] >= 3001 && d[2] >= 1, "bad date " + d[0] + "/" + d[1] + "/" + d[2]);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invasionsLoaded(GameTestHelper helper) {
        helper.assertTrue(InvasionSpawner.invasions().size() >= 40, "only " + InvasionSpawner.invasions().size()
                + " invasions");
        helper.assertTrue(InvasionSpawner.invasions().values().stream().allMatch(i -> !i.mobs().isEmpty()),
                "an invasion has no mobs");
        helper.succeed();
    }
}
