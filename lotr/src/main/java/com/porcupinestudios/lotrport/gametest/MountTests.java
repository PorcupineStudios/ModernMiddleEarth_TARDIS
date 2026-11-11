package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.mount.LotrHorse;
import com.porcupinestudios.lotrport.mount.MountArmorItem;
import com.porcupinestudios.lotrport.mount.Mounts;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * LOTR's mounts: every breed is its own entity with LOTR's stat rolls, armour fits only its own kind of mount and
 * protects it, NPCs that spawn riding come on an armoured, saddled mount, and boars turn on what hurts them.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MountTests {
    private MountTests() {}

    private static LotrHorse mount(GameTestHelper helper, String breed) {
        LotrHorse h = Mounts.TYPE_BY_BREED.get(breed).get().create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        h.moveTo(at.x, at.y, at.z, 0, 0);
        h.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(h.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null);
        return h;
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id(id)));
    }

    @GameTest(template = "empty", batch = "mounts")
    public static void breedsRollLotrStats(GameTestHelper helper) {
        for (String breed : new String[] {"horse", "shire_pony", "zebra", "wild_boar", "camel", "elk", "rhino",
                "giraffe"}) {
            helper.assertTrue(Mounts.TYPE_BY_BREED.containsKey(breed), "no mount breed " + breed);
            helper.assertTrue(Mounts.stats(breed).has("food"), "no numbers loaded for " + breed);
        }
        for (int i = 0; i < 40; i++) {
            LotrHorse rhino = mount(helper, "rhino");
            double health = rhino.getAttributeBaseValue(Attributes.MAX_HEALTH);
            helper.assertTrue(health >= 40 && health <= 45.01, "a rhino rolled " + health + " health");
            helper.assertTrue(rhino.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 4, "a rhino doesn't hit for 4");
            LotrHorse boar = mount(helper, "wild_boar");
            helper.assertTrue(boar.getAttributeBaseValue(Attributes.MAX_HEALTH) <= 25, "a boar has over 25 health");
            LotrHorse pony = mount(helper, "shire_pony");
            helper.assertTrue(pony.getAttributeBaseValue(Attributes.MAX_HEALTH) <= 30 * 0.75 + 0.01
                    && pony.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) <= 0.3375 * 0.8 + 1e-6
                    && pony.getAttributeBaseValue(Attributes.JUMP_STRENGTH) <= 0.5 + 1e-6,
                    "a Shire pony is as strong as a horse");
            LotrHorse foal = (LotrHorse) rhino.getBreedOffspring(helper.getLevel(), mount(helper, "rhino"));
            double born = foal.getAttributeBaseValue(Attributes.MAX_HEALTH);
            helper.assertTrue(born >= 20 && born <= 50, "a rhino calf has " + born + " health");
        }
        helper.assertTrue(mount(helper, "horse").isFood(new ItemStack(Items.APPLE)), "horses don't eat apples");
        helper.assertTrue(mount(helper, "giraffe").isFood(new ItemStack(Items.OAK_LEAVES))
                && !mount(helper, "giraffe").isFood(new ItemStack(Items.APPLE)), "giraffes don't eat just leaves");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "mounts")
    public static void armourFitsItsMount(GameTestHelper helper) {
        ItemStack gondor = item("gondorian_horse_armor");
        ItemStack boarArmour = item("dwarven_boar_armor");
        helper.assertTrue(gondor.getItem() instanceof MountArmorItem && boarArmour.getItem() instanceof MountArmorItem,
                "mount armour items are missing");
        LotrHorse horse = mount(helper, "horse");
        LotrHorse boar = mount(helper, "wild_boar");
        LotrHorse camel = mount(helper, "camel");
        LotrHorse elk = mount(helper, "elk");
        helper.assertTrue(horse.isBodyArmorItem(gondor) && !horse.isBodyArmorItem(boarArmour),
                "a horse wears the wrong armour");
        helper.assertTrue(boar.isBodyArmorItem(boarArmour) && !boar.isBodyArmorItem(gondor),
                "a boar wears the wrong armour");
        helper.assertTrue(horse.isBodyArmorItem(new ItemStack(Items.IRON_HORSE_ARMOR))
                && !elk.isBodyArmorItem(new ItemStack(Items.IRON_HORSE_ARMOR)), "vanilla horse armour fits wrongly");
        helper.assertTrue(camel.isBodyArmorItem(new ItemStack(Items.RED_CARPET))
                && !horse.isBodyArmorItem(new ItemStack(Items.RED_CARPET)), "carpets are for camels");
        // LOTR's zebras take horse armour; giraffe and camel armour are the port's own (LOTR had none)
        LotrHorse giraffe = mount(helper, "giraffe");
        helper.assertTrue(mount(helper, "zebra").isBodyArmorItem(gondor), "a zebra can't wear horse armour");
        helper.assertTrue(giraffe.isBodyArmorItem(item("iron_giraffe_armor"))
                && !giraffe.isBodyArmorItem(item("iron_camel_armor")) && !giraffe.isBodyArmorItem(gondor),
                "a giraffe wears the wrong armour");
        helper.assertTrue(camel.isBodyArmorItem(item("diamond_camel_armor"))
                && !camel.isBodyArmorItem(item("diamond_giraffe_armor")), "a camel wears the wrong armour");
        horse.setBodyArmorItem(gondor);
        // entities only tick in loaded chunks, and the 1-block template may sit on a chunk's edge
        ThrownTests.inLoadedArea(helper, 2, 2, () -> {
            helper.getLevel().addFreshEntity(horse);
            // armour counts from the horse's next tick; a forced chunk can take a few ticks to start ticking
            helper.succeedWhen(() -> {
                helper.assertTrue(horse.getArmorValue() >= 9, "Gondorian horse armour gives " + horse.getArmorValue());
                horse.discard();
            });
        });
    }

    @GameTest(template = "empty", batch = "mounts")
    public static void npcsSpawnRiding(GameTestHelper helper) {
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        LotrNpc soldier = LotrNpc.spawn(helper.getLevel(), LotrPort.id("gondor_soldier"), at.x, at.y, at.z)
                .orElseThrow();
        for (int i = 0; i < 400 && !soldier.isPassenger(); i++) {
            soldier.maybeRide(); // one spawn in six
        }
        helper.assertTrue(soldier.getVehicle() instanceof LotrHorse, "the soldier never came riding");
        LotrHorse horse = (LotrHorse) soldier.getVehicle();
        helper.assertTrue(horse.getType() == Mounts.TYPE_BY_BREED.get("horse").get(), "it rides " + horse.getType());
        helper.assertTrue(horse.isSaddled() && horse.isTamed() && horse.npcOwned(), "its horse isn't tame and saddled");
        helper.assertTrue(horse.getBodyArmorItem().is(BuiltInRegistries.ITEM.get(LotrPort.id("gondorian_horse_armor"))),
                "its horse wears " + horse.getBodyArmorItem());
        horse.discard();
        soldier.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "mounts", timeoutTicks = 200)
    public static void boarsFightBack(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 3, 3, () -> {
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(2, 1, 2))) {
                helper.setBlock(p, net.minecraft.world.level.block.Blocks.STONE);
                // earlier batches' tests stood here too and left blocks outside the 1-block template
                helper.setBlock(p.above(), net.minecraft.world.level.block.Blocks.AIR);
                helper.setBlock(p.above(2), net.minecraft.world.level.block.Blocks.AIR);
            }
            LotrHorse boar = mount(helper, "wild_boar");
            helper.getLevel().addFreshEntity(boar);
            Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(1.5, 2, 2.5));
            float health = pig.getHealth();
            helper.runAfterDelay(5, () -> boar.hurt(helper.getLevel().damageSources().mobAttack(pig), 1f));
            helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < health || !pig.isAlive(),
                    "the boar didn't turn on what hurt it (target " + boar.getTarget() + ")"));
        });
    }
}
