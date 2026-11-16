package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** LOTR creatures' own blows and tricks (npc/behaviour): on-hit effects, the olog-hai's splash, scorpions, bombs. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreatureTests {
    private static final String EMPTY = "empty";

    private CreatureTests() {}

    private static LotrNpc spawn(GameTestHelper helper, String type, double x, double z) {
        Vec3 pos = helper.absoluteVec(new Vec3(x, 2, z));
        return LotrNpc.spawn(helper.getLevel(), LotrPort.id(type), pos.x, pos.y, pos.z)
                .orElseThrow(() -> new IllegalStateException("could not spawn " + type));
    }

    /** A still pig with plenty of health, so a troll's blow doesn't kill it. */
    private static Pig pig(GameTestHelper helper, double x, double z) {
        Pig pig = EntityType.PIG.create(helper.getLevel());
        Vec3 pos = helper.absoluteVec(new Vec3(x, 2, z));
        pig.moveTo(pos.x, pos.y, pos.z, 0, 0);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        pig.setHealth(500);
        helper.getLevel().addFreshEntity(pig);
        return pig;
    }

    @GameTest(template = EMPTY, batch = "creatures")
    public static void mirkTrollHitPoisons(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        LotrNpc troll = spawn(helper, "mirk_troll", 1.5, 1.5);
        Pig pig = pig(helper, 2.5, 1.5);
        helper.assertTrue(troll.doHurtTarget(pig), "the Mirk-troll's blow didn't land");
        helper.assertTrue(pig.hasEffect(MobEffects.POISON), "a Mirk-troll's blow didn't poison");
        // normal difficulty 2: 3 x 2 - 1 = 5 seconds
        helper.assertTrue(pig.getEffect(MobEffects.POISON).getDuration() == 100, "poison isn't 5 s");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "creatures")
    public static void ologSplashHurtsBystander(GameTestHelper helper) {
        LotrNpc olog = spawn(helper, "olog_hai", 1.5, 1.5);
        Pig target = pig(helper, 3.5, 1.5);
        Pig bystander = pig(helper, 5.5, 1.5);
        helper.assertTrue(olog.doHurtTarget(target), "the olog-hai's blow didn't land");
        helper.assertTrue(bystander.getHealth() < 500, "the olog-hai's hammer didn't splash a pig 2 blocks off");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "creatures")
    public static void scorpionMilkedForPoison(GameTestHelper helper) {
        LotrNpc scorpion = spawn(helper, "desert_scorpion", 1.5, 1.5);
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        scorpion.interact(player, InteractionHand.MAIN_HAND);
        var poison = BuiltInRegistries.ITEM.get(LotrPort.id("bottle_of_poison"));
        helper.assertTrue(player.getMainHandItem().is(poison), "milking a scorpion gave "
                + player.getMainHandItem() + ", not a bottle of poison");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "creatures", timeoutTicks = 200)
    public static void wargBombardierBlowsUp(GameTestHelper helper) {
        helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                .set(false, helper.getLevel().getServer());
        LotrNpc warg = spawn(helper, "mordor_warg_bombardier", 1.5, 1.5);
        Pig pig = pig(helper, 3.5, 1.5);
        warg.setTarget(pig);
        helper.succeedWhen(() -> helper.assertTrue(warg.isRemoved(), "the bombardier hasn't gone off"));
    }
}
