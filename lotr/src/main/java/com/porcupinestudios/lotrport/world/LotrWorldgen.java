package com.porcupinestudios.lotrport.world;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.feature.RoadFeature;
import com.porcupinestudios.lotrport.world.map.MapBiomeSource;
import com.porcupinestudios.lotrport.world.map.MapTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World generation types: the map biome source, the map terrain density function and the road feature. */
public final class LotrWorldgen {
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, LotrPort.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, LotrPort.MOD_ID);

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, LotrPort.MOD_ID);

    static {
        FEATURES.register("roads", RoadFeature::new);
        BIOME_SOURCES.register("middle_earth", () -> MapBiomeSource.CODEC);
        DENSITY_FUNCTIONS.register("map_terrain", () -> MapTerrain.MAP_CODEC);
    }

    private LotrWorldgen() {}

    public static void register(IEventBus modBus) {
        BIOME_SOURCES.register(modBus);
        DENSITY_FUNCTIONS.register(modBus);
        FEATURES.register(modBus);
        NeoForge.EVENT_BUS.addListener(LotrWorldgen::onServerStarted);
        NeoForge.EVENT_BUS.addListener(LotrWorldgen::onCreateSpawn);
    }

    /**
     * A new Middle-earth world spawns on dry land: vanilla's pick near the map's centre could land in a hollow below
     * sea level or by a lake, depending on the seed (CI world check, 2026-11-04). Searches outwards from (0, 0) using
     * the generator's height (no chunks made) for ground at or above sea level outside oceans, rivers and lakes.
     */
    private static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || !LotrDimensions.isMiddleEarth(level)) {
            return;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int sea = level.getSeaLevel();
        for (int r = 0; r <= 1024; r += 8) {
            for (int i = -r; i <= r; i += 8) {
                int[][] spots = r == 0 ? new int[][] {{0, 0}}
                        : new int[][] {{i, -r}, {i, r}, {-r, i}, {r, i}};
                for (int[] xz : spots) {
                    int y = generator.getBaseHeight(xz[0], xz[1], Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                    if (y <= sea || wet(generator, random, xz[0], y, xz[1])) {
                        continue;
                    }
                    event.getSettings().setSpawn(new BlockPos(xz[0], y, xz[1]), 0);
                    event.setCanceled(true);
                    return;
                }
                if (r == 0) {
                    break;
                }
            }
        }
    }

    private static boolean wet(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        String id = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                QuartPos.fromBlock(z), random.sampler()).unwrapKey().map(k -> k.location().getPath()).orElse("");
        return id.contains("ocean") || id.contains("river") || id.contains("lake");
    }

    /** A Middle-earth world type is one whose overworld is generated from the map. */
    private static void onServerStarted(ServerStartedEvent event) {
        boolean middleEarthWorld = LotrDimensions.overworldIsMiddleEarth(event.getServer());
        LotrWorldData data = LotrWorldData.get(event.getServer());
        if (data.startsInMiddleEarth() != middleEarthWorld) {
            data.setStartsInMiddleEarth(middleEarthWorld);
        }
        WorldCheck.runIfAsked(event.getServer());
    }
}
