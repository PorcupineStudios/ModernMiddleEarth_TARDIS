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

    /**
     * Rhosgobel rabbits live round Rhosgobel, on the western eaves of southern Mirkwood: the waypoint sits in southern
     * Mirkwood on the map, the rabbits are on its spawn list and the Vale of Anduin's beside it, and the spawn rule
     * keeps them within its radius (2048 blocks); other mounts spawn anywhere their biomes have them.
     */
    @GameTest(template = "empty", batch = "mounts")
    public static void rhosgobelRabbitsLiveRoundRhosgobel(GameTestHelper helper) {
        var type = Mounts.TYPE_BY_BREED.get("rhosgobel_rabbit").get();
        var home = com.porcupinestudios.lotrport.travel.FastTravel.waypoint(LotrPort.id("rhosgobel")).orElse(null);
        helper.assertTrue(home != null, "no Rhosgobel waypoint");
        var map = com.porcupinestudios.lotrport.world.map.MiddleEarthMap.get();
        String biome = map.biomes().get(map.biomeIndexAt(home.x(), home.z())).id();
        helper.assertTrue(biome.equals("lotr_port:mirkwood_corrupted"), "Rhosgobel lies in " + biome
                + ", not southern Mirkwood");
        helper.assertTrue(Mounts.nearHome(type, new BlockPos(home.x(), 70, home.z())), "not at Rhosgobel itself");
        helper.assertTrue(Mounts.nearHome(type, new BlockPos(home.x() - 1500, 70, home.z() + 800)),
                "not 1700 blocks out, at the Vale of Anduin");
        helper.assertFalse(Mounts.nearHome(type, new BlockPos(home.x() + 2100, 70, home.z())),
                "2100 blocks from Rhosgobel");
        helper.assertFalse(Mounts.nearHome(type, BlockPos.ZERO), "far off in the Shire's direction");
        helper.assertTrue(Mounts.nearHome(Mounts.TYPE_BY_BREED.get("horse").get(), BlockPos.ZERO),
                "horses are kept to a home too");
        var biomes = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        for (String b : new String[] {"mirkwood_corrupted", "anduin_vale"}) {
            var mobs = biomes.getOrThrow(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.BIOME, LotrPort.id(b))).getMobSettings()
                    .getMobs(net.minecraft.world.entity.MobCategory.CREATURE).unwrap();
            helper.assertTrue(mobs.stream().anyMatch(m -> m.type == type), "no Rhosgobel rabbits on " + b + "'s list");
        }
        var shire = biomes.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.BIOME, LotrPort.id("shire"))).getMobSettings()
                .getMobs(net.minecraft.world.entity.MobCategory.CREATURE).unwrap();
        helper.assertFalse(shire.stream().anyMatch(m -> m.type == type), "Rhosgobel rabbits in the Shire");
        LotrHorse rabbit = mount(helper, "rhosgobel_rabbit");
        helper.assertFalse(rabbit.isSaddleable(), "a Rhosgobel rabbit takes a saddle");
        helper.succeed();
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

    // forced chunks can take a while to start ticking on a busy CI server: the default 100 ticks ran out (2026-11-12)
    @GameTest(template = "empty", batch = "mounts", timeoutTicks = 600)
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

    // its own 5 x 5 stone pen (structure boar_pen): on the 1-block "empty" template the 3 x 3 floor it laid stuck out
    // into its neighbours' space, and holes opened under the boar (CI 2026-11-12: boar 2 blocks below the pig, unable
    // to reach or see it)
    @GameTest(template = "boar_pen", batch = "mounts", timeoutTicks = 400)
    public static void boarsFightBack(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 5, 5, () -> {
            LotrHorse boar = mount(helper, "wild_boar");
            boar.moveTo(helper.absoluteVec(new Vec3(2.5, 1, 1.5)));
            helper.getLevel().addFreshEntity(boar);
            Pig pig = helper.spawn(EntityType.PIG, new Vec3(2.5, 1, 3.5));
            float health = pig.getHealth();
            helper.succeedWhen(() -> {
                // hurt it again until it notices (its chunk may start ticking a few ticks late)
                if (boar.getTarget() == null && boar.invulnerableTime == 0) {
                    boar.hurt(helper.getLevel().damageSources().mobAttack(pig), 0.5f);
                }
                helper.assertTrue(pig.getHealth() < health || !pig.isAlive(),
                        "the boar didn't turn on what hurt it (target " + boar.getTarget() + ", ticks " + boar.tickCount
                                + ", hurt by " + boar.getLastHurtByMob() + ", pig ticks " + pig.tickCount
                                + "; boar at " + boar.position() + " size " + boar.getBbWidth() + "x"
                                + boar.getBbHeight() + ", pig at " + pig.position() + ", in reach "
                                + boar.isWithinMeleeAttackRange(pig) + ", sees " + boar.getSensing().hasLineOfSight(pig)
                                + ", nav done " + boar.getNavigation().isDone() + ", baby " + boar.isBaby()
                                + ", standing " + boar.isStanding() + ", eating " + boar.isEating() + ", tame "
                                + boar.isTamed() + ", attack " + boar.getAttributeValue(
                                        net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                                + ", speed " + boar.getAttributeValue(
                                        net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                                + ", pig hurt time " + pig.hurtTime + " invulnerable " + pig.isInvulnerable()
                                + ", running " + boar.goalSelector.getAvailableGoals().stream()
                                        .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                                        .map(g -> g.getGoal().getClass().getSimpleName() + "(" + g.getPriority()
                                                + ")").toList() + ")");
            });
        });
    }

    /** LOTR's rhino charge: ridden faster than 0.32 blocks a tick it gores a pig in its way. */
    @GameTest(template = "empty", batch = "mounts", timeoutTicks = 200)
    public static void rhinoChargeGores(GameTestHelper helper) {
        LotrHorse rhino = mount(helper, "rhino");
        rhino.setTamed(true);
        rhino.equipSaddle(new ItemStack(Items.SADDLE), null);
        rhino.setNoAi(true);
        helper.getLevel().addFreshEntity(rhino);
        var rider = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        rider.moveTo(rhino.getX(), rhino.getY(), rhino.getZ());
        rider.startRiding(rhino, true);
        Vec3 ahead = rhino.position().add(4, 0, 0);
        Pig pig = EntityType.PIG.create(helper.getLevel());
        pig.moveTo(ahead.x, ahead.y, ahead.z, 0, 0);
        pig.setNoAi(true);
        helper.getLevel().addFreshEntity(pig);
        float full = pig.getHealth();
        rhino.setYRot(-90f); // facing +x
        helper.onEachTick(() -> {
            if (rhino.getX() < ahead.x + 2) {
                rhino.setPos(rhino.getX() + 0.5, rhino.getY(), rhino.getZ());
                rhino.setYRot(-90f);
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(!pig.isAlive() || pig.getHealth() < full,
                "the charging rhino didn't hurt the pig"));
    }
}
