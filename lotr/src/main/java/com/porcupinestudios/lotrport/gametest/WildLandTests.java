package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.WildBlocks;
import com.porcupinestudios.lotrport.world.feature.Grove;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.lotrport.world.map.Roads;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Done-when for the wild lands (Aaron 2026-11-25, the Lone-lands: rocky, varied ground, groves in seeded patches, not
 * a forest, boulders, moss, dry creek beds, ravines): the blocks behave, the gorges are sparse, dry and kept off
 * roads, the groves cover a part of the land and thin out eastwards, and the biomes carry the new features in place
 * of the old variant patches.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WildLandTests {
    private static final String EMPTY = "empty";
    private static final List<String> BIOMES = List.of("lotr_port:lone_lands", "lotr_port:lone_lands_hills");

    private WildLandTests() {}

    @GameTest(template = EMPTY)
    public static void wildBlocksBehave(GameTestHelper helper) {
        BlockPos soil = new BlockPos(2, 1, 2);
        helper.setBlock(soil, WildBlocks.MOORLAND_SOIL.get());
        helper.assertTrue(WildBlocks.MOORLAND_SOIL.get().defaultBlockState().is(BlockTags.DIRT),
                "moorland soil isn't dirt");
        helper.setBlock(soil.above(), WildBlocks.TUSSOCK.get());
        helper.assertBlockPresent(WildBlocks.TUSSOCK.get(), soil.above());
        helper.assertTrue(WildBlocks.BRACKEN.get().defaultBlockState().canSurvive(helper.getLevel(),
                helper.absolutePos(soil.above())), "bracken can't grow on moorland soil");
        // scree stays put where gravel would fall
        BlockPos high = new BlockPos(5, 3, 5);
        helper.setBlock(high, WildBlocks.SCREE.get());
        helper.runAfterDelay(10, () -> {
            helper.assertBlockPresent(WildBlocks.SCREE.get(), high);
            helper.assertTrue(WildBlocks.WEATHERED_STONE.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE)
                    && WildBlocks.SCREE.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_SHOVEL),
                    "wild blocks lack their mining tags");
            helper.setBlock(high, Blocks.AIR);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void lonelandGorgesAreSparseDryAndOffRoads(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        int columns = 0;
        int inGorge = 0;
        int deep = 0;
        for (String id : BIOMES) {
            int b = index(map, id);
            helper.assertTrue(b >= 0, id + " isn't on the map");
            for (int[] c : map.pixelCentres(b, 100_000)) {
                for (int k = 0; k < 64; k += 8) { // 8 samples across each pixel
                    int x = c[0] - 60 + k * 15 % 120;
                    int z = c[1] - 60 + k * 2;
                    double g = map.gorgeAt(x, z);
                    columns++;
                    if (g <= 0) {
                        continue;
                    }
                    inGorge++;
                    if (g > 0.9) {
                        deep++;
                    }
                    helper.assertTrue(Roads.get().distance(x, z) > 2, "a gorge on a road at " + x + "," + z);
                }
            }
        }
        double share = (double) inGorge / columns;
        LotrPort.LOG.info("[wilds] gorges: {} of {} sampled columns ({}%), {} on a floor", inGorge, columns,
                Math.round(share * 1000) / 10.0, deep);
        helper.assertTrue(deep > 0, "no gorge floor found in the Lone-lands");
        helper.assertTrue(share < 0.06, "gorges cover " + share + " of the Lone-lands");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void lonelandGrovesArePatchesThinningEastwards(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        int b = index(map, "lotr_port:lone_lands");
        PlacedFeature groves = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                .get(ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "wilds/groves_lone_lands"));
        helper.assertTrue(groves != null, "no grove feature");
        Grove grove = (Grove) groves.placement().stream().filter(p -> p instanceof Grove).findFirst().orElseThrow();
        Grove.Settings s = grove.settings();
        List<int[]> pixels = map.pixelCentres(b, 100_000);
        int minX = pixels.stream().mapToInt(p -> p[0]).min().orElse(0);
        int maxX = pixels.stream().mapToInt(p -> p[0]).max().orElse(0);
        int[] in = new int[2];
        int[] all = new int[2];
        for (int[] c : pixels) {
            int half = c[0] < (minX + maxX) / 2 ? 0 : 1;
            for (int k = 0; k < 4; k++) {
                int x = c[0] - 48 + 32 * k;
                all[half]++;
                if (Grove.value(s, x, c[1]) >= s.above()) {
                    in[half]++;
                }
            }
        }
        double west = (double) in[0] / all[0];
        double east = (double) in[1] / all[1];
        LotrPort.LOG.info("[wilds] Lone-lands groves: west {}%, east {}%", Math.round(west * 100),
                Math.round(east * 100));
        helper.assertTrue(west > 0.08 && west < 0.5, "west Lone-lands grove share " + west);
        helper.assertTrue(east < west, "the east isn't barer: " + east + " vs " + west);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void lonelandsUseTheWildLandFeatures(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        var configured = helper.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        for (String id : BIOMES) {
            Biome biome = biomes.get(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(id)));
            helper.assertTrue(biome != null, id + " missing");
            var step = biome.getGenerationSettings().features().get(9);
            List<String> names = step.stream().map(h -> h.unwrapKey().map(k -> k.location().toString()).orElse(""))
                    .toList();
            String path = ResourceLocation.parse(id).getPath();
            helper.assertTrue(names.get(0).equals("lotr_port:wilds/ground_cover_" + path), id + " step: " + names);
            helper.assertTrue(names.contains("lotr_port:wilds/boulders_" + path), id + " has no boulders");
            helper.assertTrue(names.stream().noneMatch(n -> n.contains("_variant/") || n.startsWith("lotr_port:trees/")),
                    id + " still has old trees or variant plants: " + names);
        }
        ConfiguredFeature<?, ?> cover = configured.get(ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID,
                "wilds/ground_cover_lone_lands"));
        helper.assertTrue(cover != null, "ground cover feature missing");
        helper.succeed();
    }

    /** Eriador keeps its seven kinds (each lightly re-grounded) and gets four milder Lone-lands kinds besides. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void eriadorKeepsItsSevenAndGainsMilderWilds(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        int b = index(map, "lotr_port:eriador");
        java.util.Map<String, Integer> seen = new java.util.TreeMap<>();
        for (int[] c : map.pixelCentres(b, 100_000)) {
            String v = map.variantAt(c[0], c[1]);
            seen.merge(v == null ? "plain" : v, 1, Integer::sum);
        }
        LotrPort.LOG.info("[wilds] Eriador patch kinds by pixel: {}", seen);
        for (String kind : List.of("wild_meadows", "heath", "copses", "old_woods", "pinewood", "tors", "old_fields",
                "stony_heath", "moorland", "rocky_dells", "brushland")) {
            helper.assertTrue(seen.getOrDefault(kind, 0) > 20, "Eriador has too little " + kind + ": " + seen);
        }
        Biome eriador = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME)
                .get(ResourceLocation.parse("lotr_port:eriador"));
        List<String> names = eriador.getGenerationSettings().features().get(9).stream()
                .map(h -> h.unwrapKey().map(k -> k.location().toString()).orElse("")).toList();
        helper.assertTrue(names.contains("lotr_port:wilds/eriador/tors_ground")
                && names.contains("lotr_port:wilds/eriador/rocky_dells_groves")
                && names.contains("lotr_port:eriador_variant/tors_grey_boulder"),
                "Eriador's step lacks the tuned or new features: " + names);
        helper.succeed();
    }

    private static int index(MiddleEarthMap map, String id) {
        for (int i = 0; i < map.biomes().size(); i++) {
            if (map.biomes().get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
