package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.AlignmentHud;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.net.HudNet;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcSpawner;
import com.porcupinestudios.lotrport.player.LotrRespawn;
import com.porcupinestudios.lotrport.progress.MiddleEarthTime;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.unit.UnitInventoryMenu;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Done-whens of five small stories: Middle-earth's day length (#21), alignment pop-ups (#60), the hired unit's
 * inventory (#61), spawn damping (#20) and LOTR's respawn rules.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StoryTests {
    private StoryTests() {}

    /** LOTR's 48000-tick day: the clock runs at half speed, and the time of day reads in Middle-earth ticks. */
    @GameTest(template = "empty")
    public static void middleEarthDayLasts48000Ticks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(MiddleEarthTime.perTick() == 0.5f, "clock speed " + MiddleEarthTime.perTick());
        float before = level.getDayTimePerTick();
        MiddleEarthTime.apply(level);
        float applied = level.getDayTimePerTick();
        level.setDayTimePerTick(before < 0 ? -1f : before); // the test server's clock stays as it was
        helper.assertTrue(applied == 0.5f, "the level's clock runs at " + applied);
        helper.assertTrue(MiddleEarthTime.timeOfDay(level) == level.getDayTime() % 24000 * 2,
                "time of day " + MiddleEarthTime.timeOfDay(level) + " at clock " + level.getDayTime());
        helper.succeed();
    }

    /** A kill's or quest's pop-up carries each faction's change in that faction's colour, and skips zeros. */
    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void alignmentPopupListsEachFactionsChange(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceLocation gondor = LotrPort.id("gondor");
        ResourceLocation mordor = LotrPort.id("mordor");
        Map<ResourceLocation, Float> changes = new LinkedHashMap<>();
        changes.put(gondor, 5f);
        changes.put(mordor, -5f);
        changes.put(LotrPort.id("rohan"), 0f);
        HudNet.AlignmentBonus b = AlignmentHud.popup(player, Vec3.ZERO, Component.literal("Orc"), changes);
        helper.assertTrue(b.bonuses().size() == 2, b.bonuses().size() + " lines, want 2 (no zero line)");
        int gondorColor = Factions.get(player.server, gondor).orElseThrow().color();
        helper.assertTrue(b.bonuses().get(0).amount() == 5f && b.bonuses().get(0).color() == gondorColor
                && b.bonuses().get(1).amount() == -5f, "lines " + b.bonuses());
        helper.succeed();
    }

    /** The unit's gear screen puts a sword in its hand, keeps armour slots for armour, and is the owner's only. */
    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void unitInventorySwapsTheWeapon(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        LotrNpc npc = LotrNpc.spawn(helper.getLevel(), LotrPort.id("gondor_soldier"), at.x, at.y, at.z)
                .orElseThrow();
        npc.hireTo(player);
        Society.hired(helper.getLevel().getServer(), npc, player.getUUID());
        player.moveTo(at.x + 1, at.y, at.z);
        UnitInventoryMenu menu = new UnitInventoryMenu(0, player.getInventory(), npc);
        helper.assertTrue(menu.stillValid(player), "the owner can't open it");
        menu.getSlot(4).set(new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(npc.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND_SWORD), "the sword isn't held");
        helper.assertTrue(!menu.getSlot(1).mayPlace(new ItemStack(Items.DIAMOND_SWORD))
                && menu.getSlot(1).mayPlace(new ItemStack(Items.IRON_CHESTPLATE)), "chest slot takes the wrong items");
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        stranger.moveTo(at.x - 1, at.y, at.z);
        helper.assertTrue(!menu.stillValid(stranger), "a stranger can use another's unit's gear");
        npc.discard();
        helper.succeed();
    }

    /** LOTR's damping: more players together, a smaller cap, cut by at most 0.5 + damping / 2, never below 1. */
    @GameTest(template = "empty")
    public static void spawnDampingGivesFewerSpawnsWithMorePlayers(GameTestHelper helper) {
        helper.assertTrue(NpcSpawner.dampedCap(10, 1, 0.25) == 10, "one player is never damped");
        helper.assertTrue(NpcSpawner.dampedCap(10, 3, 0.25) == 5, "3 players: " + NpcSpawner.dampedCap(10, 3, 0.25));
        helper.assertTrue(NpcSpawner.dampedCap(10, 9, 0.25) == 4, "the cut stops at 0.625: "
                + NpcSpawner.dampedCap(10, 9, 0.25));
        helper.assertTrue(NpcSpawner.dampedCap(10, 9, 0) == 10, "0 is off");
        helper.assertTrue(NpcSpawner.dampedCap(1, 9, 1) == 1, "never below 1");
        helper.succeed();
    }

    /** Bed within 5000 of the death, else spawn within 2000, else 500 to 1500 blocks from the death. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void respawnFollowsLotrRules(GameTestHelper helper) {
        BlockPos death = BlockPos.ZERO;
        helper.assertTrue(LotrRespawn.choose(death, new BlockPos(4000, 70, 0), new BlockPos(100, 70, 0))
                == LotrRespawn.Where.BED, "a bed 4000 away should win");
        helper.assertTrue(LotrRespawn.choose(death, new BlockPos(6000, 70, 0), new BlockPos(1500, 70, 0))
                == LotrRespawn.Where.SPAWN, "a bed 6000 away is too far; the spawn 1500 away should win");
        helper.assertTrue(LotrRespawn.choose(death, null, new BlockPos(2500, 70, 0)) == LotrRespawn.Where.RANDOM,
                "no bed and the spawn 2500 away: random");
        BlockPos dead = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos to = LotrRespawn.randomNear(helper.getLevel(), dead, helper.getLevel().random);
        double d = Math.sqrt(Math.pow(to.getX() - dead.getX(), 2) + Math.pow(to.getZ() - dead.getZ(), 2));
        helper.assertTrue(d >= 499 && d <= 1502, "random respawn " + Math.round(d) + " blocks away");
        helper.succeed();
    }
}
