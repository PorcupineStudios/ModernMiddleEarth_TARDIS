package com.porcupinestudios.lotrport.world;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.map.MapBiomeSource;
import com.porcupinestudios.lotrport.world.map.MapTerrain;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World generation types: the map biome source and the map terrain density function. */
public final class LotrWorldgen {
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, LotrPort.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, LotrPort.MOD_ID);

    static {
        BIOME_SOURCES.register("middle_earth", () -> MapBiomeSource.CODEC);
        DENSITY_FUNCTIONS.register("map_terrain", () -> MapTerrain.MAP_CODEC);
    }

    private LotrWorldgen() {}

    public static void register(IEventBus modBus) {
        BIOME_SOURCES.register(modBus);
        DENSITY_FUNCTIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(LotrWorldgen::onServerStarted);
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
