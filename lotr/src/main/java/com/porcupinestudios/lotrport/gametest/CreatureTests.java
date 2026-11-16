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
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true); // peaceful removes NPCs at once
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

    /** Its own batch: the blast would kill the other tests' animals. */
    @GameTest(template = EMPTY, batch = "creature_bomb", timeoutTicks = 300)
    public static void wargBombardierBlowsUp(GameTestHelper helper) {
        helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                .set(false, helper.getLevel().getServer());
        ThrownTests.inLoadedArea(helper, 8, 4, () -> {
            LotrNpc warg = spawn(helper, "mordor_warg_bombardier", 1.5, 1.5);
            warg.setPersistenceRequired();
            Pig pig = pig(helper, 3.5, 1.5);
            warg.setTarget(pig);
            helper.succeedWhen(() -> helper.assertTrue(warg.isRemoved(), "the bombardier hasn't gone off"));
        });
    }

    @GameTest(template = EMPTY, batch = "creatures")
    public static void aurochsKnocksBack(GameTestHelper helper) {
        LotrNpc aurochs = spawn(helper, "aurochs", 1.5, 1.5);
        Pig pig = pig(helper, 3.5, 1.5);
        helper.assertTrue(aurochs.doHurtTarget(pig), "the aurochs' blow didn't land");
        helper.assertTrue(pig.getDeltaMovement().horizontalDistance() > 0.1, "the aurochs didn't knock the pig back");
        helper.succeed();
    }

    /** LOTR's rabbits try 32 random spots within 16 for a crop, 1 in 20 a tick: a field of 24 is found in ~1000 ticks. */
    @GameTest(template = EMPTY, batch = "creatures", timeoutTicks = 3000)
    public static void rabbitEatsCrop(GameTestHelper helper) {
        helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                .set(true, helper.getLevel().getServer());
        java.util.List<net.minecraft.core.BlockPos> field = new java.util.ArrayList<>();
        ThrownTests.inLoadedArea(helper, 8, 4, () -> {
            for (int x = 3; x <= 8; x++) {
                for (int z = 0; z <= 3; z++) {
                    var crop = new net.minecraft.core.BlockPos(x, 1, z);
                    helper.setBlock(crop.below(), net.minecraft.world.level.block.Blocks.FARMLAND);
                    helper.setBlock(crop, net.minecraft.world.level.block.Blocks.WHEAT);
                    field.add(crop);
                }
            }
            LotrNpc rabbit = spawn(helper, "rabbit", 1.5, 1.5);
            rabbit.setPersistenceRequired();
            Vec3 floor = helper.absoluteVec(new Vec3(1.5, 0, 1.5));
            rabbit.moveTo(floor.x, floor.y, floor.z);
            helper.succeedWhen(() -> helper.assertTrue(field.stream().anyMatch(
                    p -> !helper.getBlockState(p).is(net.minecraft.world.level.block.Blocks.WHEAT)), "the rabbit ate no wheat"));
        });
    }

    @GameTest(template = EMPTY, batch = "creatures", timeoutTicks = 1200)
    public static void birdStealsSeeds(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 8, 4, () -> {
            LotrNpc bird = spawn(helper, "bird", 1.5, 1.5);
            bird.setPersistenceRequired();
            Vec3 floor = helper.absoluteVec(new Vec3(1.5, 0, 1.5)); // the test floor is at y 0
            bird.moveTo(floor.x, floor.y, floor.z);
            Vec3 at = helper.absoluteVec(new Vec3(4.5, 0, 1.5));
            var seeds = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), at.x, at.y, at.z,
                    new ItemStack(Items.WHEAT_SEEDS, 8));
            seeds.setNeverPickUp();
            helper.getLevel().addFreshEntity(seeds);
            helper.succeedWhen(() -> helper.assertTrue(!seeds.isAlive() || seeds.getItem().getCount() < 8,
                    "the bird hasn't stolen any seeds"));
        });
    }
}
