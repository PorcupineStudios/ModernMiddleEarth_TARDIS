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
                ResourceLocation.parse("not_installed:willow_log"), LotrPort.id("willow_wood")));
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

    /** With Regions Unexplored installed, its willow log drops as LOTR's (the log is "Willow Wood" in LOTR). */
    @GameTest(template = "empty")
    public static void modLogDropsAsLotrs(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("regions_unexplored")) {
            helper.succeed(); // shortcut: only meaningful with the mod; run with it in run/mods
            return;
        }
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                ResourceLocation.parse("regions_unexplored:willow_log"));
        var pos = new net.minecraft.core.BlockPos(1, 2, 1);
        helper.setBlock(pos, block);
        var abs = helper.absolutePos(pos);
        var drops = net.minecraft.world.level.block.Block.getDrops(helper.getLevel().getBlockState(abs),
                helper.getLevel(), abs, null);
        var willow = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(LotrPort.id("willow_wood"));
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(willow), "the willow log dropped " + drops);
        helper.succeed();
    }

    /** With Regions Unexplored installed, its sideways willow log and stairs become LOTR's, turned the same way. */
    @GameTest(template = "empty")
    public static void modBlocksBecomeLotrs(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("regions_unexplored")) {
            helper.succeed();
            return;
        }
        var reg = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var log = reg.get(ResourceLocation.parse("regions_unexplored:willow_log")).defaultBlockState()
                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X);
        var swapped = com.porcupinestudios.lotrport.compat.Unify.swap(log);
        helper.assertTrue(swapped.getBlock() == reg.get(LotrPort.id("willow_wood"))
                && swapped.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
                == net.minecraft.core.Direction.Axis.X, "the willow log became " + swapped);
        var stairs = reg.get(ResourceLocation.parse("regions_unexplored:willow_stairs")).defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.EAST);
        var s2 = com.porcupinestudios.lotrport.compat.Unify.swap(stairs);
        helper.assertTrue(s2.getBlock() == reg.get(LotrPort.id("willow_wood_stairs"))
                && s2.getValue(net.minecraft.world.level.block.StairBlock.FACING) == net.minecraft.core.Direction.EAST,
                "the willow stairs became " + s2);
        var oak = net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();
        helper.assertTrue(com.porcupinestudios.lotrport.compat.Unify.swap(oak) == oak, "an oak log changed");
        helper.succeed();
    }

    /** With the mods installed, a compat sub-biome grows its mod biome's plants instead of LOTR's trees. */
    @GameTest(template = "empty")
    public static void subBiomeGrowsModVegetation(GameTestHelper helper) {
        boolean bop = net.neoforged.fml.ModList.get().isLoaded("biomesoplenty");
        boolean ru = net.neoforged.fml.ModList.get().isLoaded("regions_unexplored");
        if (!bop && !ru) {
            helper.succeed();
            return;
        }
        var biome = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME,
                        LotrPort.id("ithilien_gardens")));
        var step = biome.value().getGenerationSettings().features()
                .get(net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal());
        List<String> ids = new ArrayList<>();
        step.forEach(h -> ids.add(h.unwrapKey().map(k -> k.location().toString()).orElse("?")));
        String want = bop ? "biomesoplenty:trees_mediterranean_forest" : "regions_unexplored:";
        helper.assertTrue(ids.stream().anyMatch(s -> s.startsWith(want)), "no " + want + " in " + ids);
        helper.assertTrue(!ids.contains("lotr_port:trees/ithilien"), "LOTR's Ithilien trees are still there");
        if (bop && ru) { // Biomes O' Plenty is listed first for Ithilien: Regions Unexplored's stays out
            helper.assertTrue(ids.stream().noneMatch(s -> s.startsWith("regions_unexplored:")), "both mods' plants");
        }
        helper.succeed();
    }
}
