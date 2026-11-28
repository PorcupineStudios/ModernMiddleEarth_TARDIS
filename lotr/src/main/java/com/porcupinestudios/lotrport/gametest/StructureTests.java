package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.structure.LotrStructure;
import com.porcupinestudios.lotrport.world.structure.LotrStructurePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * LOTR structures are placed as LOTR's decorator placed them and never stand in each other: every LOTR structure is
 * planned over a square of Shire chunks (off the axes, where LOTR builds nothing), without generating them. Logs, per
 * structure, how many stood against how many LOTR would have built there (its rolls times the share that stood in
 * LOTR, {@link LotrStructure#expected}).
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureTests {
    /** Chunks from the map origin (in the Shire) to the square's corner, and its side. */
    private static final int FROM = 3;
    private static final int SIDE = 32;
    private static final long SEED = 12345L;

    private StructureTests() {}

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void lotrStructuresNeverOverlap(GameTestHelper helper) {
        MiddleEarthTests.Terrain terrain = MiddleEarthTests.middleEarth(helper);
        MinecraftServer server = helper.getLevel().getServer();
        Map<LotrStructure, String> lotr = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<Structure>, Structure> e : server.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).entrySet()) {
            if (e.getValue() instanceof LotrStructure l) {
                lotr.put(l, e.getKey().location().getPath());
            }
        }
        helper.assertTrue(!lotr.isEmpty(), "no LOTR structures are registered");
        List<LotrStructure.Placed> placed = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, Double> expected = new TreeMap<>();
        long t0 = System.nanoTime();
        for (int cx = FROM; cx < FROM + SIDE; cx++) {
            for (int cz = FROM; cz < FROM + SIDE; cz++) {
                Structure.GenerationContext ctx = new Structure.GenerationContext(server.registryAccess(),
                        terrain.generator(), terrain.source(), terrain.random(), server.getStructureManager(), SEED,
                        new ChunkPos(cx, cz), terrain.heights(), b -> true);
                for (Map.Entry<LotrStructure, String> s : lotr.entrySet()) {
                    double e = s.getKey().expected(ctx);
                    if (e > 0) {
                        expected.merge(s.getValue(), e, Double::sum);
                    }
                    LotrStructure.Placed p = s.getKey().place(ctx);
                    if (p != null) {
                        placed.add(p);
                        counts.merge(s.getValue(), 1, Integer::sum);
                    }
                }
            }
        }
        LotrPort.LOG.info("[structures] {} placed in {} Shire chunks in {} ms, LOTR {}", placed.size(), SIDE * SIDE,
                (System.nanoTime() - t0) / 1_000_000,
                String.format("%.1f", expected.values().stream().mapToDouble(Double::doubleValue).sum()));
        for (String id : new TreeSet<>(lotr.values())) {
            if (counts.containsKey(id) || expected.containsKey(id)) {
                LotrPort.LOG.info("[structures]   {}: placed {}, LOTR {}", id, counts.getOrDefault(id, 0),
                        String.format("%.1f", expected.getOrDefault(id, 0.0)));
            }
        }
        helper.assertTrue(!placed.isEmpty(), "no LOTR structure in " + SIDE * SIDE + " Shire chunks");
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                LotrStructure.Plan a = placed.get(i).plan();
                LotrStructure.Plan b = placed.get(j).plan();
                helper.assertTrue(!a.overlaps(b, 0), a.template() + " at " + a.x() + ", " + a.z() + " overlaps "
                        + b.template() + " at " + b.x() + ", " + b.z());
            }
        }
        helper.succeed();
    }

    /**
     * A template's own air stays air: the Hobbit inn's cellar and its stairwell aren't filled by the foundations
     * (#91: the stairwell's open ground-layer spot got grass and the dirt under it, the cellar dirt).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void foundationsLeaveCellarsEmpty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // away from the test grid, on the flat test world's surface
        BlockPos at = helper.absolutePos(BlockPos.ZERO).offset(0, 0, -300);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()) - 1;
        int ground = 4;
        LotrStructurePiece piece = new LotrStructurePiece(level.getStructureManager(), LotrPort.id("hobbit_tavern/0"),
                at.atY(surface - ground), Rotation.NONE, ground, 8);
        BoundingBox box = piece.getBoundingBox();
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.random, box,
                new ChunkPos(at), at);
        BlockPos corner = new BlockPos(box.minX(), box.minY(), box.minZ());
        // template coordinates: the stairwell at x 20, z 23, y 3 and 4; the cellar below
        for (BlockPos rel : new BlockPos[] {new BlockPos(20, 4, 23), new BlockPos(20, 3, 23),
                new BlockPos(17, 2, 25), new BlockPos(18, 1, 26)}) {
            BlockPos p = corner.offset(rel);
            helper.assertTrue(level.getBlockState(p).isAir(), "cellar " + rel + " holds " + level.getBlockState(p));
        }
        helper.succeed();
    }
}
