package com.porcupinestudios.lotrport.world;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * CI check for a new Middle-earth world: started with {@code -Dlotr_port.worldcheck=true} on a server whose
 * {@code level-type} is {@code lotr_port:middle_earth}. Logs where the world spawns, prints WORLDCHECK PASSED or
 * WORLDCHECK FAILED, and stops the server.
 */
final class WorldCheck {
    private WorldCheck() {}

    static void runIfAsked(MinecraftServer server) {
        if (!Boolean.getBoolean("lotr_port.worldcheck")) {
            return;
        }
        String problem = null;
        try {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            String biome = overworld.getBiome(spawn).unwrapKey().map(k -> k.location().toString()).orElse("?");
            LotrPort.LOG.info("[worldcheck] overworld is Middle-earth: {}, startsInMiddleEarth: {}, spawn {} in {}",
                    LotrDimensions.overworldIsMiddleEarth(server), LotrWorldData.get(server).startsInMiddleEarth(),
                    spawn.toShortString(), biome);
            if (!LotrDimensions.overworldIsMiddleEarth(server)) {
                problem = "the overworld is not generated from the Middle-earth map";
            } else if (!LotrWorldData.get(server).startsInMiddleEarth()) {
                problem = "the world was not marked as starting in Middle-earth";
            } else if (biome.contains("ocean")) {
                problem = "the world spawns in the ocean";
            } else if (spawn.getY() < overworld.getSeaLevel()) {
                problem = "the spawn is below sea level at y " + spawn.getY();
            }
        } catch (RuntimeException e) {
            LotrPort.LOG.error("[worldcheck] crashed", e);
            problem = e.toString();
        }
        LotrPort.LOG.info(problem == null ? "WORLDCHECK PASSED" : "WORLDCHECK FAILED: " + problem);
        server.halt(false);
    }
}
