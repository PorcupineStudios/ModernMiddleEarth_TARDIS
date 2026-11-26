package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.delve.DelveFeature;
import com.porcupinestudios.lotrport.world.delve.DelvePlan;
import com.porcupinestudios.lotrport.world.delve.DelveSpawns;
import com.porcupinestudios.lotrport.world.delve.DelveSpec;
import com.porcupinestudios.lotrport.world.delve.Mat;
import com.porcupinestudios.lotrport.world.delve.MemoryCanvas;
import com.porcupinestudios.lotrport.world.delve.Palette;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Moria (the {@code lotr_port:moria} delve, 2026-11-25, Aaron: a maze of caves, tunnels, great halls, mines, forges,
 * stairs and bridges right through the Misty Mountains). Done when: the hidden route runs from the West-gate to the
 * East-gate, the maze stays under the mountain, the plan is the same every time, its blocks and loot tables exist,
 * and the gate and the great hall build where they should. Their own batch: planning Moria takes about a second on
 * the server thread, which threw the colony timing test off when they ran alongside it.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DelveTests {
    private static final String EMPTY = "empty";

    private DelveTests() {}

    private static DelveFeature.Config moria(GameTestHelper helper) {
        return DelveFeature.config(helper.getLevel().getServer(), LotrPort.id("moria"))
                .orElseThrow(() -> new IllegalStateException("no configured feature lotr_port:moria"));
    }

    private static DelvePlan plan(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        return DelveFeature.plan(server, moria(helper), helper.getLevel().getSeed());
    }

    @GameTest(template = EMPTY, batch = "delves", timeoutTicks = 400)
    public static void moriaJoinsTheGatesUnderTheMountain(GameTestHelper helper) {
        DelvePlan plan = plan(helper);
        if (!plan.routeComplete()) {
            helper.fail("Moria's route is broken:\n" + plan.summary);
        }
        List<int[]> route = plan.routePoints();
        int[] west = plan.doorway("west_gate");
        int[] east = plan.doorway("east_gate");
        if (west == null || east == null) {
            helper.fail("a gate wasn't placed:\n" + plan.summary);
        }
        int[] first = route.get(0);
        int[] last = route.get(route.size() - 1);
        if (Math.abs(first[0] - west[0]) > 200 || Math.abs(last[0] - east[0]) > 200) {
            helper.fail("the route runs from " + first[0] + " to " + last[0] + ", not gate to gate");
        }
        if (plan.routeLevels() < 3) {
            helper.fail("the route stays on " + plan.routeLevels() + " levels");
        }
        LotrPort.LOG.info("[delve test] Moria route: {} nodes over {} levels; {} nodes, {} pieces", route.size(),
                plan.routeLevels(), plan.nodeCount(), plan.pieceCount());
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "delves", timeoutTicks = 400)
    public static void moriaStaysUnderTheMountain(GameTestHelper helper) {
        DelvePlan plan = plan(helper);
        MiddleEarthMap map = MiddleEarthMap.get();
        int bad = 0;
        String first = null;
        for (int[] r : plan.mazeRooms()) {
            int[][] spots = {{r[0], r[2]}, {r[3], r[2]}, {r[0], r[5]}, {r[3], r[5]}, {(r[0] + r[3]) / 2, (r[2] + r[5]) / 2}};
            for (int[] s : spots) {
                double g = map.heightAt(s[0], s[1]);
                if (g < r[4] + 3) {
                    bad++;
                    if (first == null) {
                        first = String.format("room top %d at (%d, %d) under ground %.1f", r[4], s[0], s[1], g);
                    }
                }
            }
        }
        if (bad > 0) {
            helper.fail(bad + " room corners break the mountainside, first " + first);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "delves", timeoutTicks = 600)
    public static void moriaPlansTheSameEveryTime(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long seed = helper.getLevel().getSeed();
        DelvePlan a = DelveFeature.makePlan(server, moria(helper), seed);
        DelvePlan b = DelveFeature.makePlan(server, moria(helper), seed);
        if (a.pieceCount() != b.pieceCount() || !a.summary.equals(b.summary)) {
            helper.fail("two plans of one seed differ: " + a.pieceCount() + " vs " + b.pieceCount() + " pieces");
        }
        List<int[]> ra = a.routePoints();
        List<int[]> rb = b.routePoints();
        for (int i = 0; i < ra.size(); i++) {
            if (!java.util.Arrays.equals(ra.get(i), rb.get(i))) {
                helper.fail("the routes differ at node " + i);
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "delves")
    public static void moriaBlocksAndLootExist(GameTestHelper helper) {
        DelveSpec spec = DelveFeature.spec(moria(helper));
        MinecraftServer server = helper.getLevel().getServer();
        for (Palette p : spec.palettes.values()) {
            for (Mat m : p.mats()) {
                if (!DelveFeature.blockExists(m.id)) {
                    helper.fail("palette " + p + " names no block " + m.id);
                }
                if (m.loot != null && !DelveFeature.lootExists(server, m.loot)) {
                    helper.fail("palette " + p + " names no loot table " + m.loot);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "delves", timeoutTicks = 400)
    public static void moriaBuildsItsGateAndGreatHall(GameTestHelper helper) {
        DelvePlan plan = plan(helper);
        MiddleEarthMap map = MiddleEarthMap.get();
        int[] door = plan.doorway("west_gate");
        MemoryCanvas gate = MemoryCanvas.chunkAt(door[0], door[2], map::heightAt);
        plan.build(gate);
        Mat d = gate.at(door[0], door[1] + 1, door[2]);
        if (d == null || !d.id.equals("lotr_port:ithildin_dwarven_door")) {
            helper.fail("the West-gate's doorway at " + door[0] + " " + door[1] + " " + door[2] + " holds " + d);
        }
        int[] hall = plan.setPiece("dwarrowdelf");
        if (hall == null) {
            helper.fail("the Dwarrowdelf wasn't placed");
        }
        int cx = (hall[0] + hall[3]) / 2;
        int cz = (hall[2] + hall[5]) / 2;
        MemoryCanvas inside = new MemoryCanvas(cx - 32, cz - 32, cx + 31, cz + 31, map::heightAt);
        plan.build(inside);
        if (inside.count("dwarven_pillar") < 100) {
            helper.fail("the Dwarrowdelf has no pillars where expected");
        }
        helper.succeed();
    }

    /**
     * Moria's people by depth (Aaron 2026-11-25): orcs above, trolls below, never a troll on the upper levels, the
     * Balrog only under y -30 and at most one at a time.
     */
    @GameTest(template = EMPTY, batch = "delves")
    public static void moriaSpawnsByDepth(GameTestHelper helper) {
        DelveSpawns.Table table = DelveSpawns.tables().get(LotrPort.id("moria"));
        if (table == null) {
            helper.fail("no delve_spawns/moria.json");
        }
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(7);
        java.util.Set<String> upper = new java.util.HashSet<>();
        java.util.Set<String> deep = new java.util.HashSet<>();
        for (int i = 0; i < 20000; i++) {
            upper.add(DelveSpawns.pick(table, 150, random).npc().getPath());
            deep.add(DelveSpawns.pick(table, -50, random).npc().getPath());
        }
        if (upper.stream().anyMatch(n -> n.contains("troll") || n.equals("balrog"))) {
            helper.fail("trolls or the Balrog on the upper levels: " + upper);
        }
        if (!deep.contains("troll") || !deep.contains("balrog")) {
            helper.fail("no trolls or Balrog in the lowest deeps: " + deep);
        }
        for (DelveSpawns.Entry e : table.entries()) {
            if (e.npc().getPath().equals("balrog") && (e.maxAlive() != 1 || e.minY() > -30 || e.maxY() > -30
                    || e.chance() > 0.01f)) {
                helper.fail("the Balrog must be rare, deep and alone: " + e);
            }
            if (!com.porcupinestudios.lotrport.npc.NpcData.type(e.npc()).isPresent()) {
                helper.fail("no NPC type " + e.npc());
            }
        }
        helper.succeed();
    }
}
