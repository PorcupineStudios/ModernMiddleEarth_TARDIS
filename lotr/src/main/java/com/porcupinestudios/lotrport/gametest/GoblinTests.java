package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.goblin.GoblinBlocks;
import com.porcupinestudios.lotrport.content.goblin.GoblinTraps;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcData;
import com.porcupinestudios.lotrport.world.delve.DelveFeature;
import com.porcupinestudios.lotrport.world.delve.DelvePlan;
import com.porcupinestudios.lotrport.world.delve.DelveSpawns;
import com.porcupinestudios.lotrport.world.delve.DelveSpec;
import com.porcupinestudios.lotrport.world.delve.GoblinRules;
import com.porcupinestudios.lotrport.world.delve.Mat;
import com.porcupinestudios.lotrport.world.delve.Palette;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Goblin Town (2026-11-26, Aaron: THE Goblin Town and smaller generic ones, scrappy wooden scaffolding, a Front Porch
 * whose floor swings open, decoy caves that look just like it, the deep falls into Gollum's lake, the back door).
 * Done when: every part is placed and zoned, the chute drops from the porch to the landing, a decoy at the porch's
 * spot is the same block for block above its floor ({@link GoblinRules}); generic towns and decoys are scattered
 * over the mountains, never on THE town; the floor swings open over a drop (by standing on it or by redstone), never
 * on rock, and shuts again; the Great Goblin and Gollum keep to their zones, one at a time.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoblinTests {
    private static final String EMPTY = "empty";

    private GoblinTests() {}

    private static DelveFeature.Config config(GameTestHelper helper, String id) {
        return DelveFeature.config(helper.getLevel().getServer(), LotrPort.id(id))
                .orElseThrow(() -> new IllegalStateException("no configured feature lotr_port:" + id));
    }

    @GameTest(template = EMPTY, batch = "delves", timeoutTicks = 400)
    public static void goblinTownKeepsItsRules(GameTestHelper helper) {
        DelveFeature.Config town = config(helper, "goblin_town");
        String wrong = GoblinRules.check(town.spec().getAsJsonObject(), helper.getLevel().getSeed(),
                DelveFeature.ground());
        if (wrong != null) {
            helper.fail("Goblin Town: " + wrong);
        }
        DelvePlan plan = DelveFeature.plan(helper.getLevel().getServer(), town, helper.getLevel().getSeed());
        LotrPort.LOG.info("[goblin test] Goblin Town: {} pieces; porch {}, landing {}, lake {}", plan.pieceCount(),
                java.util.Arrays.toString(plan.doorway("front_porch")), java.util.Arrays.toString(plan.doorway("landing")),
                java.util.Arrays.toString(plan.doorway("lake")));
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "delves", timeoutTicks = 600)
    public static void goblinTownsAndDecoysAreScattered(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long seed = helper.getLevel().getSeed();
        int[] the = DelveFeature.plan(server, config(helper, "goblin_town"), seed).doorway("front_porch");
        // the Misty Mountains and their foothills
        int[] box = {44000, -30000, 60000, 30000};
        for (String id : List.of("goblin_towns", "goblin_porches")) {
            List<DelvePlan> plans = DelveFeature.plansNear(server, config(helper, id), seed, box[0], box[1], box[2],
                    box[3]);
            int min = id.equals("goblin_towns") ? 5 : 30;
            if (plans.size() < min) {
                helper.fail(id + ": only " + plans.size() + " copies over the Misty Mountains (want " + min + ")");
            }
            for (DelvePlan p : plans) {
                int[] porch = p.doorway("front_porch");
                if (porch == null) {
                    helper.fail(id + ": a copy without a porch");
                }
                if (the != null && Math.hypot(porch[0] - the[0], porch[2] - the[2]) < 300) {
                    helper.fail(id + ": a copy at " + porch[0] + " " + porch[2] + " is on THE Goblin Town");
                }
                if (id.equals("goblin_porches") && p.doorway("landing") != null) {
                    helper.fail("a decoy porch at " + porch[0] + " " + porch[2] + " has a chute");
                }
            }
            LotrPort.LOG.info("[goblin test] {}: {} copies over the Misty Mountains", id, plans.size());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "delves")
    public static void goblinBlocksAndLootExist(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String id : List.of("goblin_town", "goblin_towns", "goblin_porches")) {
            DelveSpec spec = DelveFeature.spec(config(helper, id));
            for (Palette p : spec.palettes.values()) {
                for (Mat m : p.mats()) {
                    if (!DelveFeature.blockExists(m.id)) {
                        helper.fail(id + " palette " + p + " names no block " + m.id);
                    }
                    if (m.loot != null && !DelveFeature.lootExists(server, m.loot)) {
                        helper.fail(id + " palette " + p + " names no loot table " + m.loot);
                    }
                }
            }
        }
        helper.succeed();
    }

    /** A 3 x 5 porch floor at relative y 3 over a pit (or on stone), with stone at the bottom. */
    private static List<BlockPos> floor(GameTestHelper helper, int x0, boolean pit) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> out = new java.util.ArrayList<>();
        for (int x = x0; x < x0 + 3; x++) {
            for (int z = 4; z < 9; z++) {
                for (int y = -4; y < 3; y++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)),
                            y == -4 || !pit ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                BlockPos p = helper.absolutePos(new BlockPos(x, 3, z));
                level.setBlockAndUpdate(p, GoblinBlocks.PORCH_FLOOR.get().defaultBlockState());
                out.add(p);
            }
        }
        return out;
    }

    private static int displays(GameTestHelper helper, List<BlockPos> floor) {
        AABB box = new AABB(floor.get(0)).minmax(new AABB(floor.get(floor.size() - 1))).inflate(3);
        return helper.getLevel().getEntitiesOfClass(Display.BlockDisplay.class, box).size();
    }

    private static void clearAmbush(GameTestHelper helper, List<BlockPos> floor) {
        AABB box = new AABB(floor.get(0)).minmax(new AABB(floor.get(floor.size() - 1))).inflate(8);
        helper.getLevel().getEntitiesOfClass(LotrNpc.class, box).forEach(e -> e.discard());
    }

    @GameTest(template = EMPTY, batch = "delves", timeoutTicks = 200)
    public static void goblinTrapSwingsOpenAndShuts(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 16, 16, () -> trapSwings(helper));
    }

    private static void trapSwings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> decoy = floor(helper, 1, false);
        List<BlockPos> trap = floor(helper, 9, true);
        if (GoblinTraps.spring(level, decoy.get(7), null)) {
            helper.fail("a porch floor on rock gave way");
        }
        if (!GoblinTraps.spring(level, trap.get(7), null)) {
            helper.fail("the porch floor over the pit didn't spring");
        }
        for (BlockPos p : trap) {
            if (!level.getBlockState(p).isAir()) {
                helper.fail("the sprung floor still holds a block at " + p);
            }
        }
        if (displays(helper, trap) != trap.size()) {
            helper.fail(displays(helper, trap) + " swinging leaves for " + trap.size() + " floor blocks");
        }
        int ambush = helper.getLevel().getEntitiesOfClass(LotrNpc.class,
                new AABB(trap.get(0)).minmax(new AABB(trap.get(trap.size() - 1))).inflate(8)).size();
        if (ambush < 2) {
            helper.fail("only " + ambush + " goblins wait at the bottom");
        }
        // let it swing all the way open, then shut it
        helper.runAfterDelay(30, () -> {
            GoblinTraps.closeNow(level, trap.get(7));
            for (BlockPos p : trap) {
                if (!(level.getBlockState(p).getBlock() instanceof GoblinBlocks.PorchFloorBlock)) {
                    helper.fail("the shut floor lacks its block at " + p);
                }
            }
            if (displays(helper, trap) != 0) {
                helper.fail("leaves left after the floor shut");
            }
            clearAmbush(helper, trap);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "delves", timeoutTicks = 200)
    public static void goblinTrapSpringsOnRedstone(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 16, 16, () -> {
            ServerLevel level = helper.getLevel();
            List<BlockPos> trap = floor(helper, 5, true);
            // beside the floor's west edge
            level.setBlockAndUpdate(trap.get(0).west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                if (!GoblinTraps.isOpen(level, trap.get(7))) {
                    helper.fail("a redstone block beside the floor didn't spring it");
                }
                GoblinTraps.closeNow(level, trap.get(7));
                clearAmbush(helper, trap);
                helper.succeed();
            });
        });
    }

    /** Someone who stays on the floor goes down: within the settings' longest wait plus the swing. */
    @GameTest(template = EMPTY, batch = "delves", timeoutTicks = 500)
    public static void goblinTrapSpringsUnderfoot(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 16, 16, () -> {
            ServerLevel level = helper.getLevel();
            List<BlockPos> trap = floor(helper, 5, true);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            BlockPos on = trap.get(7);
            player.setPos(on.getX() + 0.5, on.getY() + 1, on.getZ() + 0.5);
            helper.succeedWhen(() -> {
                helper.assertTrue(GoblinTraps.isOpen(level, on), "the floor hasn't given way yet");
                GoblinTraps.closeNow(level, on);
                clearAmbush(helper, trap);
                level.getServer().getPlayerList().remove(player);
            });
        });
    }

    /** The Great Goblin only on his throne, Gollum only by his lake, one of each at a time. */
    @GameTest(template = EMPTY, batch = "delves")
    public static void goblinSpawnsByZone(GameTestHelper helper) {
        for (String id : List.of("goblin_town", "goblin_towns")) {
            DelveSpawns.Table table = DelveSpawns.tables().get(LotrPort.id(id));
            if (table == null) {
                helper.fail("no delve_spawns/" + id + ".json");
            }
            RandomSource random = RandomSource.create(11);
            Set<String> town = new HashSet<>();
            Set<String> throne = new HashSet<>();
            Set<String> lake = new HashSet<>();
            for (int i = 0; i < 5000; i++) {
                add(town, DelveSpawns.pick(table, 150, "town", random));
                add(throne, DelveSpawns.pick(table, 180, "throne", random));
                add(lake, DelveSpawns.pick(table, 77, "gollum_lake", random));
            }
            if (town.contains("great_goblin") || town.contains("gollum") || throne.contains("gollum")) {
                helper.fail(id + ": the Great Goblin or Gollum outside their zones: " + town + " " + throne);
            }
            if (id.equals("goblin_town") && (!throne.contains("great_goblin") || !lake.equals(Set.of("gollum")))) {
                helper.fail("Goblin Town: throne " + throne + ", lake " + lake);
            }
            for (DelveSpawns.Entry e : table.entries()) {
                String n = e.npc().getPath();
                if ((n.equals("great_goblin") || n.equals("gollum")) && e.maxAlive() != 1) {
                    helper.fail(id + ": " + n + " must be one at a time: " + e);
                }
                if (NpcData.type(e.npc()).isEmpty()) {
                    helper.fail("no NPC type " + e.npc());
                }
            }
        }
        helper.succeed();
    }

    private static void add(Set<String> to, DelveSpawns.Entry e) {
        if (e != null) {
            to.add(e.npc().getPath());
        }
    }
}
