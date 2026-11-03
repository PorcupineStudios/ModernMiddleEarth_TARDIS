package com.porcupinestudios.lotrport.world;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.FactionDef;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.world.map.MapBiomeSource;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Part of the CI world check: finds the nearest structure of each of a few factions from that faction's home on the
 * map, loads the chunks around it, waits for its NPCs to join the world, and checks they are that faction's people.
 */
final class StructureCheck {
    /** Factions checked: the two the NPC phase tests, which LOTR gives the most structures. */
    private static final String[] FACTIONS = { "gondor", "mordor" };
    private static final int SEARCH_CHUNKS = 160;
    private static final int LOAD_RADIUS = 4;
    private static final int WAIT_TICKS = 60;

    private static final Map<ResourceLocation, BlockPos> FOUND = new LinkedHashMap<>();
    private static int ticks;

    private StructureCheck() {}

    /** Finds and loads the structures; returns a problem, or null when the check continues on later ticks. */
    static String start(MinecraftServer server) {
        ServerLevel level = server.overworld();
        MiddleEarthMap map = ((MapBiomeSource) level.getChunkSource().getGenerator().getBiomeSource()).map();
        List<String> missing = new ArrayList<>();
        for (String name : FACTIONS) {
            ResourceLocation faction = LotrPort.id(name);
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, LotrPort.id("faction/" + name));
            if (level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(tag).isEmpty()) {
                missing.add(name + " has no structures with NPCs");
                continue;
            }
            Optional<FactionDef.MapArea> home = Optional.ofNullable(Factions.definitions().get(faction))
                    .flatMap(FactionDef::map);
            BlockPos from = home.map(m -> map.blockOfPixel(m.x(), m.z())).orElse(BlockPos.ZERO);
            long t0 = System.nanoTime();
            BlockPos at = level.findNearestMapStructure(tag, from, SEARCH_CHUNKS, false);
            LotrPort.LOG.info("[worldcheck] nearest {} structure from {}: {} ({} ms)", name, from.toShortString(),
                    at == null ? "none" : at.toShortString(), (System.nanoTime() - t0) / 1_000_000);
            if (at == null) {
                missing.add(name + " structure within " + SEARCH_CHUNKS + " chunks of " + from.toShortString());
                continue;
            }
            ChunkPos c = new ChunkPos(at);
            for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
                for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                    level.setChunkForced(c.x + dx, c.z + dz, true);
                }
            }
            FOUND.put(faction, at);
        }
        if (!missing.isEmpty()) {
            return "structures missing: " + missing;
        }
        NeoForge.EVENT_BUS.addListener(StructureCheck::onTick);
        return null;
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (++ticks < WAIT_TICKS) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        List<String> empty = new ArrayList<>();
        FOUND.forEach((faction, at) -> {
            AABB box = new AABB(at).inflate(LOAD_RADIUS * 16, 128, LOAD_RADIUS * 16);
            List<LotrNpc> npcs = level.getEntitiesOfClass(LotrNpc.class, box);
            long theirs = npcs.stream().filter(n -> n.faction().equals(Optional.of(faction))).count();
            LotrPort.LOG.info("[worldcheck] {} structure at {}: {} NPCs, {} of {}", faction, at.toShortString(),
                    npcs.size(), theirs, faction);
            if (theirs == 0) {
                empty.add(faction + " at " + at.toShortString());
            }
        });
        WorldCheck.finish(empty.isEmpty() ? null : "structures without their NPCs: " + empty);
    }
}
