package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.compat.ReplaceDropsModifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regions Unexplored / Biomes O' Plenty integration (converter/compat.py), without either mod installed. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompatTests {
    private CompatTests() {}

    /** A drop table keeps only pairs whose both items exist: a missing mod's ids replace nothing. */
    @GameTest(template = "empty")
    public static void replaceDropsSkipsMissingMods(GameTestHelper helper) {
        var map = ReplaceDropsModifier.resolve(Map.of(
                ResourceLocation.parse("minecraft:oak_log"), ResourceLocation.parse("minecraft:birch_log"),
                ResourceLocation.parse("regions_unexplored:willow_log"), LotrPort.id("willow_wood")));
        helper.assertTrue(map.size() == 1 && map.get(Items.OAK_LOG) == Items.BIRCH_LOG, "resolved " + map);
        helper.succeed();
    }

    /** Every Middle-earth biome, the compat sub-biomes too, is in #lotr_port:middle_earth (ores, caves, delves). */
    @GameTest(template = "empty")
    public static void everyBiomeIsMiddleEarth(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        TagKey<Biome> tag = TagKey.create(Registries.BIOME, LotrPort.id("middle_earth"));
        List<String> missing = new ArrayList<>();
        biomes.holders().filter(h -> h.key().location().getNamespace().equals(LotrPort.MOD_ID) && !h.is(tag))
                .forEach(h -> missing.add(h.key().location().getPath()));
        helper.assertTrue(biomes.containsKey(LotrPort.id("ithilien_gardens")), "the compat sub-biomes aren't there");
        helper.assertTrue(missing.isEmpty(), "not in #lotr_port:middle_earth: " + missing);
        helper.succeed();
    }
}
