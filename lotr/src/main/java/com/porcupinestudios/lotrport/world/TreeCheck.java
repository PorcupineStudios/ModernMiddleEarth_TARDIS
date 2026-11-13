package com.porcupinestudios.lotrport.world;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.feature.TemplateTreeFeature;
import com.porcupinestudios.lotrport.world.feature.TemplateTreeFeature.Placed;
import com.porcupinestudios.lotrport.world.map.MapBiomeSource;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * World check (part of {@link WorldCheck}): trees in real Middle-earth forests come out whole and apart. Generates a
 * patch of chunks in each big forest, then, once {@link com.porcupinestudios.lotrport.tree.TreeOverflow} has placed
 * the parts beyond a feature's reach, checks every tree grown there against what it placed: no log or leaf missing
 * (air where the tree put a block), no two trees' logs in one place, no leaves left to decay.
 */
final class TreeCheck {
    private TreeCheck() {}

    private static final List<String> FORESTS = List.of("fangorn", "mirkwood_north", "old_forest", "lothlorien",
            "chetwood", "lindon_woodlands", "gondor_woodlands");
    private static final int RADIUS = 3; // chunks each side of the forest's sample point
    private static final Queue<List<Placed>> GROWN = new ConcurrentLinkedQueue<>();
    private static final Set<Long> AREA = new HashSet<>();

    /** Generates the forest patches (kept loaded); null, or what went wrong. */
    static String start(ServerLevel level) {
        MiddleEarthMap map = ((MapBiomeSource) level.getChunkSource().getGenerator().getBiomeSource()).map();
        int[][] samples = map.samplePositions();
        long t0 = System.nanoTime();
        TemplateTreeFeature.audit = GROWN::add;
        try {
            for (int i = 0; i < samples.length; i++) {
                String id = map.biomes().get(i).id();
                if (samples[i] == null || !FORESTS.contains(id.substring(id.indexOf(':') + 1))) {
                    continue;
                }
                LotrPort.LOG.info("[worldcheck] trees: {} patch round {}, {}", id, samples[i][0], samples[i][1]);
                int cx = samples[i][0] >> 4;
                int cz = samples[i][1] >> 4;
                for (int dx = -RADIUS; dx < RADIUS; dx++) {
                    for (int dz = -RADIUS; dz < RADIUS; dz++) {
                        level.setChunkForced(cx + dx, cz + dz, true);
                        AREA.add(ChunkPos.asLong(cx + dx, cz + dz));
                    }
                }
            }
        } finally {
            TemplateTreeFeature.audit = null;
        }
        LotrPort.LOG.info("[worldcheck] trees: generated {} forest chunks in {} s", AREA.size(),
                (System.nanoTime() - t0) / 1_000_000_000L);
        return AREA.isEmpty() ? "none of the forests is on the map" : null;
    }

    /** After TreeOverflow has run (it places every 10 ticks): null, or what is wrong with the trees. */
    static String result(ServerLevel level) {
        int trees = 0, blocks = 0, missingLogs = 0, missingLeaves = 0, decaying = 0, overlaps = 0;
        List<String> examples = new ArrayList<>();
        Map<BlockPos, Integer> logOwner = new HashMap<>();
        int refused = 0;
        for (List<Placed> tree : GROWN) {
            if (tree.isEmpty()) {
                refused++;
                continue;
            }
            if (!tree.stream().allMatch(b -> AREA.contains(ChunkPos.asLong(b.pos())))) {
                continue; // reaches past the generated patches
            }
            trees++;
            for (Placed b : tree) {
                // a structure's pieces are placed in their own chunk's feature step, after a neighbour's tree already
                // reached in: where a building stands, its blocks (and its air) win, as in LOTR (CI 2026-11-12: a few
                // leaves "missing" inside a structure's box, now and then)
                if (level.structureManager().hasAnyStructureAt(b.pos())) {
                    continue;
                }
                blocks++;
                BlockState here = level.getBlockState(b.pos());
                boolean log = b.state().is(BlockTags.LOGS);
                if (log && logOwner.put(b.pos(), trees) != null) {
                    overlaps++;
                }
                if (log ? !here.is(BlockTags.LOGS) : here.isAir() || here.canBeReplaced() && !here.is(b.state().getBlock())) {
                    if (log) {
                        missingLogs++;
                    } else {
                        missingLeaves++;
                    }
                    if (examples.size() < 8) {
                        examples.add(b.state().getBlock() + " at " + b.pos().toShortString() + " is " + here);
                    }
                }
                if (here.getBlock() instanceof LeavesBlock && here.getValue(LeavesBlock.DISTANCE) >= 7
                        && !here.getValue(LeavesBlock.PERSISTENT)) {
                    decaying++;
                }
            }
        }
        LotrPort.LOG.info("[worldcheck] trees: {} grown, {} refused (another tree in the way); {} whole in the "
                + "patches, {} blocks: {} logs and {} leaves missing, {} log overlaps, {} leaves decaying; e.g. {}",
                GROWN.size() - refused, refused, trees, blocks, missingLogs, missingLeaves, overlaps, decaying,
                examples);
        if (trees < 50) {
            return "only " + trees + " whole trees in the forest patches";
        }
        if (missingLogs + missingLeaves > 0) {
            return "trees missing " + missingLogs + " logs and " + missingLeaves + " leaves, e.g. " + examples;
        }
        if (overlaps > 0) {
            return overlaps + " places where two trees' logs meet";
        }
        return decaying > 0 ? decaying + " tree leaves would decay" : null;
    }
}
