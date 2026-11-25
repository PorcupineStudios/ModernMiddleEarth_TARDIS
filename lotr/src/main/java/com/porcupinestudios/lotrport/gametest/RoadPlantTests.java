package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.feature.RoadVergeFeature;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Roads keep plants off (2026-11-25): every biome that lays roads clears them after its vegetation with
 * {@code lotr_port:road_verge}, whose overgrowth plants are real blocks. LOTR's ground plants grow wild by LOTR's rules
 * (exported from 1.7.10): thistles in the Shire's grass, elanor and niphredil in Lothlorien, arid grass on sand
 * ({@code lotr_port:grows_on/*}). The world check ({@code WorldCheck}) checks real road columns in generated chunks.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoadPlantTests {
    private static final String EMPTY = "empty";

    private RoadPlantTests() {}

    @GameTest(template = EMPTY)
    public static void roadsClearPlantsAndThistlesGrow(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.registryOrThrow(Registries.BIOME);
        ResourceLocation roads = ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "roads");
        ResourceLocation verge = ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "road_verge");
        int withRoads = 0;
        for (var e : biomes.entrySet()) {
            Biome biome = e.getValue();
            var steps = biome.getGenerationSettings().features();
            boolean lays = steps.stream().anyMatch(s -> s.stream().anyMatch(h -> h.is(roads)));
            if (!lays) {
                continue;
            }
            withRoads++;
            int top = GenerationStep.Decoration.TOP_LAYER_MODIFICATION.ordinal();
            helper.assertTrue(steps.size() > top && steps.get(top).stream().anyMatch(h -> h.is(verge)),
                    e.getKey().location() + " lays roads but doesn't clear plants off them");
        }
        helper.assertTrue(withRoads > 100, "only " + withRoads + " biomes lay roads");

        ConfiguredFeature<?, ?> configured = access.registryOrThrow(Registries.CONFIGURED_FEATURE).get(verge);
        helper.assertTrue(configured != null && configured.config() instanceof RoadVergeFeature.Config,
                "no road_verge configured feature");
        RoadVergeFeature.Config config = (RoadVergeFeature.Config) configured.config();
        helper.assertTrue(!config.plants().isEmpty(), "road_verge has no overgrowth plants");
        for (RoadVergeFeature.Plant p : config.plants()) {
            helper.assertTrue(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(p.block())) != Blocks.AIR,
                    "road_verge plant " + p.block() + " is not a block");
        }

        Biome shire = biomes.get(ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "shire"));
        helper.assertTrue(shire != null, "no Shire biome");
        var placed = access.registryOrThrow(Registries.PLACED_FEATURE);
        boolean thistles = shire.getGenerationSettings().features().stream().flatMap(s -> s.stream())
                .anyMatch(h -> h.unwrapKey().map(k -> k.location().getPath().startsWith("plant_grass/thistle_"))
                        .orElse(false) && placed.getKey(h.value()) != null);
        helper.assertTrue(thistles, "the Shire grows no thistles");
        // LOTR's own flowers (from the biome's flower list): Lothlorien grows elanor and niphredil
        Biome lorien = biomes.get(ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "lothlorien"));
        helper.assertTrue(lorien != null, "no Lothlorien biome");
        for (String flower : new String[] { "plant_flowers/elanor_", "plant_flowers/niphredil_" }) {
            helper.assertTrue(lorien.getGenerationSettings().features().stream().flatMap(s -> s.stream())
                    .anyMatch(h -> h.unwrapKey().map(k -> k.location().getPath().startsWith(flower)).orElse(false)),
                    "Lothlorien grows no " + flower);
        }
        // LOTR's plants grow where the census found them: arid grass on sand
        var sand = net.minecraft.world.level.block.Blocks.SAND.defaultBlockState();
        var grass = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "arid_grass"));
        var at = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        helper.getLevel().setBlock(at.below(), sand, 3);
        helper.assertTrue(grass.defaultBlockState().canSurvive(helper.getLevel(), at),
                "arid grass can't grow on sand");
        helper.succeed();
    }

    /**
     * Shader packs wave LOTR's plants: every leaf, sapling and ground plant has a vanilla counterpart in
     * {@code assets/lotr_port/shaders/block_aliases.json} (client/ShaderAliases, used by the Iris mixin), and every
     * alias names real blocks.
     */
    @GameTest(template = EMPTY)
    public static void everyLotrPlantHasAShaderAlias(GameTestHelper helper) {
        com.google.gson.JsonObject aliases;
        try (var in = RoadPlantTests.class.getResourceAsStream("/assets/lotr_port/shaders/block_aliases.json")) {
            helper.assertTrue(in != null, "no shader block aliases");
            aliases = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("aliases");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        for (var e : aliases.entrySet()) {
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(e.getKey())),
                    "shader alias for a block that doesn't exist: " + e.getKey());
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(e.getValue().getAsString())),
                    "shader alias " + e.getKey() + " -> missing block " + e.getValue());
        }
        for (var block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            boolean foliage = block instanceof net.minecraft.world.level.block.LeavesBlock
                    || block instanceof com.porcupinestudios.lotrport.content.LotrSaplingBlock
                    || block instanceof com.porcupinestudios.lotrport.content.PlantBlock
                    && !id.getPath().endsWith("_chandelier");
            if (id.getNamespace().equals(LotrPort.MOD_ID) && foliage) {
                helper.assertTrue(aliases.has(id.toString()), id + " has no shader alias (packs won't wave it)");
            }
        }
        helper.succeed();
    }
}
