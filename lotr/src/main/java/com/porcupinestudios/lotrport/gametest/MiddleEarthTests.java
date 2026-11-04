package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.lotrport.world.map.MapBiomeSource;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 3 done-when: Middle-earth exists and is generated from LOTR's map, the map origin is in the Shire on dry land,
 * terrain is smooth where biomes meet, and biome lookup stays within its time budget. The other half, a new
 * Middle-earth world spawning there, is the "Middle-earth world" CI step ({@code WorldCheck}).
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiddleEarthTests {
    private static final String EMPTY = "empty";
    /** Biome lookups that must finish within {@link #LOOKUP_BUDGET_MS}. */
    private static final int LOOKUPS = 1_000_000;
    private static final long LOOKUP_BUDGET_MS = 1000;
    /** Largest height step between neighbouring columns: of the map's ground alone, and of the finished terrain. */
    private static final double MAX_MAP_STEP = 3.0;
    private static final int MAX_TERRAIN_STEP = 8;

    private MiddleEarthTests() {}

    /** Middle-earth's generator with what it needs to sample terrain without a level. */
    private record Terrain(ChunkGenerator generator, RandomState random, LevelHeightAccessor heights) {
        MapBiomeSource source() {
            return (MapBiomeSource) generator.getBiomeSource();
        }

        int ground(int x, int z) {
            return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
        }
    }

    /**
     * The Middle-earth level's generator when the level exists; the GameTest server skips datapack dimensions, so
     * otherwise the same generator is built from the loaded biomes and noise settings.
     */
    private static Terrain middleEarth(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = LotrDimensions.middleEarth(server);
        if (level != null) {
            helper.assertTrue(LotrDimensions.isMiddleEarth(level), "Middle-earth is not generated from the map");
            return new Terrain(level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level);
        }
        RegistryAccess registries = server.registryAccess();
        Holder<NoiseGeneratorSettings> settings = registries.registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(ResourceKey.create(Registries.NOISE_SETTINGS, LotrPort.id("middle_earth")));
        ChunkGenerator generator = new NoiseBasedChunkGenerator(
                new MapBiomeSource(registries.lookupOrThrow(Registries.BIOME)), settings);
        RandomState random =
                RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), 12345L);
        NoiseSettings noise = settings.value().noiseSettings();
        return new Terrain(generator, random, LevelHeightAccessor.create(noise.minY(), noise.height()));
    }

    private static String key(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> k.location().toString()).orElse("?");
    }

    @GameTest(template = EMPTY)
    public static void originIsDryShire(GameTestHelper helper) {
        Terrain terrain = middleEarth(helper);
        String biome = key(terrain.source().biomeAtBlock(0, 0));
        helper.assertTrue(biome.equals("lotr_port:shire"), "the map origin is in " + biome + ", not the Shire");
        int y = terrain.ground(0, 0);
        LotrPort.LOG.info("[phase3] ground at the origin: y {}", y);
        helper.assertTrue(y > terrain.generator().getSeaLevel(), "the origin is under water (ground at y " + y + ")");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void terrainIsSmoothAcrossBiomeBorders(GameTestHelper helper) {
        Terrain terrain = middleEarth(helper);
        MapBiomeSource source = terrain.source();
        MiddleEarthMap map = source.map();
        int borders = 0;
        double worstMap = 0;
        int worstTerrain = 0;
        String worstAt = "";
        // Walk east from the Shire; check 128 blocks either side of each of the first few borders.
        Holder<Biome> last = source.biomeAtBlock(0, 0);
        for (int x = 0; x < 40_000 && borders < 4; x += 8) {
            Holder<Biome> here = source.biomeAtBlock(x, 0);
            if (here == last) {
                continue;
            }
            borders++;
            int prevGround = terrain.ground(x - 128, 0);
            double prevMap = map.heightAt(x - 128, 0);
            for (int dx = x - 127; dx <= x + 128; dx++) {
                double mapHeight = map.heightAt(dx, 0);
                int ground = terrain.ground(dx, 0);
                if (Math.abs(mapHeight - prevMap) > worstMap) {
                    worstMap = Math.abs(mapHeight - prevMap);
                }
                if (Math.abs(ground - prevGround) > worstTerrain) {
                    worstTerrain = Math.abs(ground - prevGround);
                    worstAt = key(last) + " -> " + key(here) + " at x " + dx;
                }
                prevMap = mapHeight;
                prevGround = ground;
            }
            last = here;
        }
        LotrPort.LOG.info("[phase3] {} borders, largest step: map {}, terrain {} ({})", borders,
                String.format("%.2f", worstMap), worstTerrain, worstAt);
        helper.assertTrue(borders > 0, "no biome border east of the Shire");
        helper.assertTrue(worstMap <= MAX_MAP_STEP, "the map's ground jumps " + worstMap + " blocks");
        helper.assertTrue(worstTerrain <= MAX_TERRAIN_STEP,
                "the terrain jumps " + worstTerrain + " blocks: " + worstAt);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void biomeLookupIsWithinBudget(GameTestHelper helper) {
        MapBiomeSource source = middleEarth(helper).source();
        int sink = 0;
        for (int i = 0; i < 200_000; i++) {
            sink += source.biomeAtBlock(i * 7, i * 13).hashCode();
        }
        long start = System.nanoTime();
        for (int i = 0; i < LOOKUPS; i++) {
            sink += source.biomeAtBlock((i % 1000) * 37 - 18_000, (i / 1000) * 41 - 20_000).hashCode();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        LotrPort.LOG.info("[phase3] {} biome lookups in {} ms ({})", LOOKUPS, ms, sink == 42 ? "" : "ok");
        helper.assertTrue(ms <= LOOKUP_BUDGET_MS, LOOKUPS + " biome lookups took " + ms + " ms");
        helper.succeed();
    }

    /**
     * Aaron (2026-11-04): the planet seen from space and the world's looping edges must agree on where the world is
     * cut off. Both come from one surface: the map's block bounds, and the globe's picture covers exactly that.
     */
    @GameTest(template = EMPTY)
    public static void planetEdgesAreTheMapEdges(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        var planet = com.porcupinestudios.lotrport.shared.SharedApiHooks.middleEarthPlanet();
        var min = map.blockOfPixel(0, 0);
        var max = map.blockOfPixel(map.width(), map.height());
        helper.assertTrue(planet.minX() == min.getX() && planet.maxX() == max.getX() && planet.minZ() == min.getZ()
                && planet.maxZ() == max.getZ(), "planet " + planet + " isn't the map " + min + " .. " + max);
        var pic = planet.picture().orElseThrow();
        helper.assertTrue(pic.minX() == planet.minX() && pic.maxX() == planet.maxX() && pic.minZ() == planet.minZ()
                && pic.maxZ() == planet.maxZ(), "the globe's picture doesn't cover exactly the planet: " + pic);
        helper.succeed();
    }
}
