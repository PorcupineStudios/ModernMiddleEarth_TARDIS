package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.animal.AnimalType;
import com.porcupinestudios.lotrport.animal.FactionMount;
import com.porcupinestudios.lotrport.animal.LotrAnimals;
import com.porcupinestudios.lotrport.animal.LotrMount;
import com.porcupinestudios.lotrport.animal.MountArmorItem;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.npc.NpcSpawns;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * LOTR's animals: every type spawns with LOTR's health, biomes carry LOTR's own animal lists, wargs and spiders ask
 * LOTR's alignment for riding, mount armor fits its mount, and sounds and spawn rules are LOTR's.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimalTests {
    private AnimalTests() {}

    @GameTest(template = "empty")
    public static void animalsSpawn(GameTestHelper helper) {
        helper.assertTrue(LotrAnimals.TYPES.size() >= 16, "LOTR animal types missing: " + LotrAnimals.TYPES.keySet());
        for (AnimalType t : LotrAnimals.TYPES.values()) {
            Mob mob = (Mob) helper.spawnWithNoFreeWill(LotrAnimals.ENTITY.get(t.id()).get(), new BlockPos(1, 2, 1));
            helper.assertTrue(mob.isAlive(), t.id() + " did not spawn");
            if (!t.kind().equals("pony") && !t.factionMob()) { // wargs and spiders roll theirs on spawn
                helper.assertTrue(Math.abs(mob.getMaxHealth() - t.health()) < 0.01,
                        t.id() + " health " + mob.getMaxHealth() + " != LOTR's " + t.health());
            }
            mob.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void biomesHaveLotrAnimals(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        check(helper, biomes.getOrThrow(key("shire")), Set.of("lotr_port:shire_pony", "lotr_port:deer", "minecraft:sheep"));
        check(helper, biomes.getOrThrow(key("fangorn")), Set.of("lotr_port:wild_boar", "lotr_port:bear"));
        List<MobSpawnSettings.SpawnerData> marsh = biomes.getOrThrow(key("dead_marshes")).getMobSettings()
                .getMobs(MobCategory.CREATURE).unwrap();
        helper.assertTrue(marsh.isEmpty(), "the Dead Marshes have no animals in LOTR, here " + marsh);
        helper.succeed();
    }

    /** LOTR: riding, feeding or saddling a warg takes 50 alignment with its faction; the warg is of that faction. */
    @GameTest(template = "empty")
    public static void wargsNeedAlignment(GameTestHelper helper) {
        FactionMount warg = (FactionMount) helper.spawnWithNoFreeWill(LotrAnimals.ENTITY.get("mordor_warg").get(),
                new BlockPos(1, 2, 1));
        warg.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(warg.blockPosition()),
                MobSpawnType.SPAWN_EGG, null);
        ResourceLocation mordor = LotrPort.id("mordor");
        helper.assertTrue(FactionKills.factionOf(warg).equals(Optional.of(mordor)), "warg faction "
                + FactionKills.factionOf(warg));
        helper.assertTrue(warg.kind().alignment() == 50f, "LOTR asks 50 alignment, here " + warg.kind().alignment());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(warg.position());
        Alignment.set(player, mordor, 0f);
        warg.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertFalse(player.isPassenger(), "rode a Mordor warg with 0 Mordor alignment");
        Alignment.set(player, mordor, 60f);
        warg.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getVehicle() == warg, "couldn't ride a Mordor warg with 60 alignment");
        helper.assertTrue(warg.canUseSlot(EquipmentSlot.BODY), "wargs wear armor");
        helper.succeed();
    }

    /** LOTR's mount armor fits its own mount only; LOTR's horse armor is vanilla body armor for horses. */
    @GameTest(template = "empty")
    public static void mountArmorFits(GameTestHelper helper) {
        ItemStack boarArmor = armor("boar");
        ItemStack rhinoArmor = armor("rhino");
        ItemStack horseArmor = armor("horse");
        LotrMount boar = (LotrMount) helper.spawnWithNoFreeWill(LotrAnimals.ENTITY.get("wild_boar").get(),
                new BlockPos(1, 2, 1));
        helper.assertTrue(boar.isBodyArmorItem(boarArmor), "boar armor doesn't fit a boar");
        helper.assertFalse(boar.isBodyArmorItem(rhinoArmor), "rhino armor fits a boar");
        Horse horse = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(3, 2, 1));
        helper.assertTrue(horse.isBodyArmorItem(horseArmor), "LOTR horse armor doesn't fit a horse");
        helper.assertFalse(horse.isBodyArmorItem(boarArmor), "boar armor fits a horse");
        boar.setBodyArmorItem(boarArmor);
        int protection = ((ArmorItem) boarArmor.getItem()).getDefense();
        helper.assertTrue(protection > 0, "boar armor has no protection");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(boar.getArmorValue() == protection, "armored boar has " + boar.getArmorValue()
                    + " armor, LOTR's " + protection);
            helper.succeed();
        });
    }

    private static ItemStack armor(String mount) {
        return BuiltInRegistries.ITEM.stream().filter(i -> i instanceof MountArmorItem m && m.mount().equals(mount))
                .findFirst().map(ItemStack::new).orElseThrow(() -> new AssertionError("no " + mount + " armor"));
    }

    /** Sounds and spawning as LOTR's code has them (from the exporter's creatures.json). */
    @GameTest(template = "empty")
    public static void soundsAndSpawnRules(GameTestHelper helper) {
        AnimalType boar = LotrAnimals.TYPES.get("wild_boar");
        helper.assertTrue(ResourceLocation.withDefaultNamespace("entity.pig.ambient").equals(boar.sounds().ambient()),
                "the boar grunts like LOTR's pig, here " + boar.sounds().ambient());
        AnimalType warg = LotrAnimals.TYPES.get("mordor_warg");
        helper.assertTrue(AnimalType.Sounds.event(warg.sounds().ambient()) != null, "warg sound "
                + warg.sounds().ambient() + " not registered");
        helper.assertTrue(warg.riderChance() == 3 && !warg.riders().isEmpty(), "LOTR: 1 in 3 wargs bring a rider");
        NpcSpawns.Table angmar = NpcSpawns.forBiome(LotrPort.id("angmar")).orElseThrow();
        helper.assertTrue(angmar.entries().stream().anyMatch(e -> e.npc().getPath().equals("angmar_warg") && e.dark()),
                "Angmar's wargs spawn in the dark: " + angmar.entries());
        helper.assertTrue(NpcSpawns.forBiome(LotrPort.id("mordor")).map(NpcSpawns.Table::hostilesInDay).orElse(false),
                "Mordor's orcs come out by day in LOTR");
        FactionMount spider = (FactionMount) helper.spawnWithNoFreeWill(
                LotrAnimals.ENTITY.get("mirkwood_spider").get(), new BlockPos(1, 2, 1));
        spider.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(spider.blockPosition()),
                MobSpawnType.SPAWN_EGG, null);
        double scale = spider.getAttributeValue(Attributes.SCALE);
        helper.assertTrue(scale == 0.5 || scale == 1.0 || scale == 1.5, "Mirkwood spider size " + scale);
        helper.assertTrue(spider.rideable() == (scale > 0.5), "LOTR: only grown spiders are ridden");
        helper.succeed();
    }

    private static ResourceKey<Biome> key(String id) {
        return ResourceKey.create(Registries.BIOME, LotrPort.id(id));
    }

    private static void check(GameTestHelper helper, Biome biome, Set<String> want) {
        Set<String> have = biome.getMobSettings().getMobs(MobCategory.CREATURE).unwrap().stream()
                .map(s -> BuiltInRegistries.ENTITY_TYPE.getKey(s.type).toString()).collect(Collectors.toSet());
        helper.assertTrue(have.containsAll(want), "creatures " + have + " lack some of " + want);
    }
}
