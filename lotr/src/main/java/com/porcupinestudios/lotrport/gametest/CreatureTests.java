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
        // facing the pig, as when it charges: LOTR's push goes the way the attacker faces, and spawned facing away it
        // cancels vanilla's hit knockback (the old flake)
        aurochs.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, pig.position());
        aurochs.setYBodyRot(aurochs.getYRot());
        helper.assertTrue(aurochs.doHurtTarget(pig), "the aurochs' blow didn't land");
        helper.assertTrue(pig.getDeltaMovement().horizontalDistance() > 0.1, "the aurochs didn't knock the pig back");
        helper.succeed();
    }

    /**
     * LOTR's rabbits try 32 random spots within 16 for a crop, 1 in 20 a tick: random, so up to 3 tries. Its own
     * batch (the empty template is 1 block, so a shared batch packs other tests and their animals into the field),
     * and a field of 80 crops so a search hits one about once every 7 seconds. The field lies level with the ground
     * the rabbit stands on (farmland in place of the ground block, wheat at y 0): planted one block up, on farmland
     * set on top of the ground, the rabbit stood a block below it, which is the likeliest reason it failed now and
     * then ("running []", the rabbit never ate; not reproduced locally). Only wheat gone from farmland that is still
     * farmland counts: a trampled crop (farmland turned to dirt) isn't an eaten one.
     */
    @GameTest(template = EMPTY, batch = "creature_rabbit", timeoutTicks = 3000, attempts = 3, requiredSuccesses = 1)
    public static void rabbitEatsCrop(GameTestHelper helper) {
        helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                .set(true, helper.getLevel().getServer());
        java.util.List<net.minecraft.core.BlockPos> field = new java.util.ArrayList<>();
        ThrownTests.inLoadedArea(helper, 12, 8, () -> {
            for (int x = 3; x <= 12; x++) {
                for (int z = -1; z <= 6; z++) {
                    var crop = new net.minecraft.core.BlockPos(x, 0, z);
                    helper.setBlock(crop.below(), net.minecraft.world.level.block.Blocks.FARMLAND);
                    helper.setBlock(crop, net.minecraft.world.level.block.Blocks.WHEAT);
                    field.add(crop);
                }
            }
            LotrNpc rabbit = spawn(helper, "rabbit", 1.5, 1.5);
            rabbit.setPersistenceRequired();
            Vec3 floor = helper.absoluteVec(new Vec3(1.5, 0, 1.5));
            rabbit.moveTo(floor.x, floor.y, floor.z);
            helper.succeedWhen(() -> {
                if (rabbit.position().distanceTo(floor) > 8) {
                    rabbit.moveTo(floor.x, floor.y, floor.z); // wandered out of its 16-block search of the field
                }
                // the bombardier's batch turns mob griefing off while this runs
                helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                        .set(true, helper.getLevel().getServer());
                helper.assertTrue(field.stream().anyMatch(
                    p -> !helper.getBlockState(p).is(net.minecraft.world.level.block.Blocks.WHEAT)
                            && helper.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.FARMLAND)),
                        "the rabbit ate no wheat; running " + rabbit.goals().getAvailableGoals().stream()
                    .filter(g -> g.isRunning()).map(g -> g.getGoal().getClass().getSimpleName()).toList()
                    + " alive " + rabbit.isAlive() + " at " + rabbit.position());
            });
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

    /**
     * LOTR's animals spawn and stay like 1.7.10's creatures (Aaron 2026-11-18: aurochs were rare and vanished): their
     * tables spawn with the land and count only animals against the cap, and an aurochs doesn't despawn when no player
     * is near, while an orc still does.
     */
    @GameTest(template = EMPTY, batch = "creatures")
    public static void lotrAnimalsSpawnAndStayLikeAnimals(GameTestHelper helper) {
        var table = com.porcupinestudios.lotrport.npc.NpcSpawns.animalsForBiome(LotrPort.id("rohan"));
        helper.assertTrue(table.isPresent() && table.get().entries().stream()
                .anyMatch(e -> e.npc().equals(LotrPort.id("aurochs"))), "Rohan has no aurochs");
        helper.assertTrue(table.get().generationChance() > 0, "Rohan's animals don't come with the land");
        helper.assertTrue(table.get().countOnlyAnimals(), "Rohan's animal cap counts people too");
        helper.assertTrue(com.porcupinestudios.lotrport.npc.NpcSpawns.isAnimal(LotrPort.id("aurochs"))
                && !com.porcupinestudios.lotrport.npc.NpcSpawns.isAnimal(LotrPort.id("mordor_orc")),
                "aurochs and orcs sorted wrong");
        LotrNpc aurochs = spawn(helper, "aurochs", 1.5, 1.5);
        LotrNpc orc = spawn(helper, "mordor_orc", 3.5, 1.5);
        helper.assertTrue(!aurochs.removeWhenFarAway(1000), "an aurochs despawns when no player is near");
        helper.assertTrue(orc.removeWhenFarAway(1000), "an orc no longer despawns");
        aurochs.discard();
        orc.discard();
        helper.succeed();
    }

    /** A hit deer runs (LOTR's deer were plain animals): it takes no target and never strikes back. */
    @GameTest(template = EMPTY, batch = "creatures", timeoutTicks = 200)
    public static void deerRunsAndNeverFightsBack(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 4, 4, () -> {
            LotrNpc deer = spawn(helper, "deer", 1.5, 1.5);
            Pig pig = pig(helper, 2.5, 1.5);
            float before = pig.getHealth();
            deer.hurt(helper.getLevel().damageSources().mobAttack(pig), 1f);
            helper.runAfterDelay(60, () -> {
                helper.assertTrue(deer.getTarget() == null, "the deer targets " + deer.getTarget());
                helper.assertTrue(pig.getHealth() >= before, "the deer hit back: pig at " + pig.getHealth());
                helper.succeed();
            });
        });
    }
}
