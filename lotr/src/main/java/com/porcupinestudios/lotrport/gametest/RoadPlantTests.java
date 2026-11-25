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
 * {@code lotr_port:road_verge}, whose overgrowth plants are real blocks; and LOTR's thistles grow wild (the Shire's
 * grass patches include them). The world check ({@code WorldCheck}) checks real road columns in generated chunks.
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
                .anyMatch(h -> h.unwrapKey().map(k -> k.location().getPath().startsWith("grass/thistle_"))
                        .orElse(false) && placed.getKey(h.value()) != null);
        helper.assertTrue(thistles, "the Shire grows no thistles");
        helper.succeed();
    }
}
