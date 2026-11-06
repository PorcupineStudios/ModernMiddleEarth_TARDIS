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
    public static void loreBooksSitInChests(GameTestHelper helper) {
        var template = helper.getLevel().getStructureManager().get(LotrPort.id("angmar_camp/1"));
        helper.assertTrue(template.isPresent(), "template angmar_camp/1 missing");
        net.minecraft.core.BlockPos at = helper.absolutePos(new net.minecraft.core.BlockPos(0, 1, 0));
        template.get().placeInWorld(helper.getLevel(), at, at,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                helper.getLevel().random, 2);
        var size = template.get().getSize();
        boolean found = false;
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(at, at.offset(size))) {
            if (helper.getLevel().getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    var book = c.getItem(i).get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
                    found |= book != null && !book.pages().isEmpty();
                }
            }
        }
        helper.assertTrue(found, "no lore book with pages in the camp's chests");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mountedUnitsRide(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer p = (ServerPlayer) helper.makeMockServerPlayerInLevel();
        var pos = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5));
        var rider = com.porcupinestudios.lotrport.npc.LotrNpc.spawn(helper.getLevel(), LotrPort.id("gondor_soldier"),
                pos.x, pos.y, pos.z).orElseThrow();
        com.porcupinestudios.lotrport.npc.Hiring.mount(rider, net.minecraft.resources.ResourceLocation.parse(
                "minecraft:horse"), p);
        helper.assertTrue(rider.getVehicle() instanceof net.minecraft.world.entity.animal.horse.Horse,
                "the soldier is not on a horse");
        var orc = com.porcupinestudios.lotrport.npc.LotrNpc.spawn(helper.getLevel(), LotrPort.id("mordor_orc"),
                pos.x, pos.y, pos.z).orElseThrow();
        com.porcupinestudios.lotrport.npc.Hiring.mount(orc, LotrPort.id("mordor_warg"), p);
        helper.assertTrue(orc.getVehicle() instanceof com.porcupinestudios.lotrport.npc.LotrNpc w
                && LotrPort.id("mordor_warg").equals(w.typeId()), "the orc is not on a warg");
        rider.getVehicle().discard();
        orc.getVehicle().discard();
        rider.discard();
        orc.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void questsHaveLotrLines(GameTestHelper helper) {
        var pool = com.porcupinestudios.lotrport.quest.QuestData.pool(LotrPort.id("hobbit")).orElseThrow();
        var speech = pool.quests().stream().flatMap(e -> e.speech().stream()).findFirst();
        helper.assertTrue(speech.isPresent(), "no hobbit quest has speech");
        helper.assertTrue(!com.porcupinestudios.lotrport.npc.NpcData.speech(speech.get(), "start").isEmpty(),
                "no start lines for " + speech.get());
        var spy = com.porcupinestudios.lotrport.quest.QuestData.pool(LotrPort.id("ruffian_spy")).orElseThrow();
        helper.assertTrue(spy.quests().stream().anyMatch(e -> e.kind()
                == com.porcupinestudios.lotrport.quest.QuestPool.Kind.PICKPOCKET), "no pickpocket quest");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void invasionsLoaded(GameTestHelper helper) {
        helper.assertTrue(InvasionSpawner.invasions().size() >= 40, "only " + InvasionSpawner.invasions().size()
                + " invasions");
        helper.assertTrue(InvasionSpawner.invasions().values().stream().allMatch(i -> !i.mobs().isEmpty()),
                "an invasion has no mobs");
        helper.succeed();
    }
}
