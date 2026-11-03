package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.lotrport.world.map.MapBiomeSource;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
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

    private static ServerLevel middleEarth(GameTestHelper helper) {
        ServerLevel level = LotrDimensions.middleEarth(helper.getLevel().getServer());
        helper.assertTrue(level != null, "there is no Middle-earth level");
        helper.assertTrue(LotrDimensions.isMiddleEarth(level), "Middle-earth is not generated from the map");
        return level;
    }

    private static String key(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> k.location().toString()).orElse("?");
    }

    @GameTest(template = EMPTY)
    public static void originIsDryShire(GameTestHelper helper) {
        ServerLevel level = middleEarth(helper);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        MapBiomeSource source = (MapBiomeSource) generator.getBiomeSource();
        String biome = key(source.biomeAtBlock(0, 0));
        helper.assertTrue(biome.equals("lotr_port:shire"), "the map origin is in " + biome + ", not the Shire");
        int y = generator.getBaseHeight(0, 0, Heightmap.Types.OCEAN_FLOOR_WG, level,
                level.getChunkSource().randomState());
        LotrPort.LOG.info("[phase3] ground at the origin: y {}", y);
        helper.assertTrue(y > generator.getSeaLevel(), "the origin is under water (ground at y " + y + ")");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void terrainIsSmoothAcrossBiomeBorders(GameTestHelper helper) {
        ServerLevel level = middleEarth(helper);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        MapBiomeSource source = (MapBiomeSource) generator.getBiomeSource();
        RandomState random = level.getChunkSource().randomState();
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
            int prevGround = generator.getBaseHeight(x - 128, 0, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
            double prevMap = map.heightAt(x - 128, 0);
            for (int dx = x - 127; dx <= x + 128; dx++) {
                double mapHeight = map.heightAt(dx, 0);
                int ground = generator.getBaseHeight(dx, 0, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
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
        MapBiomeSource source = (MapBiomeSource) middleEarth(helper).getChunkSource().getGenerator()
                .getBiomeSource();
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
}
