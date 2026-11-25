package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.road.RoadData;
import com.porcupinestudios.lotrport.road.RoadPaving;
import com.porcupinestudios.lotrport.road.RoadTier;
import com.porcupinestudios.lotrport.road.RoadWorks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Road works (lotr/docs/roads.md): a stretch lays its tier on natural ground only, traffic wears a trail into a dirt
 * path but never into a road waymakers build, and routes see the stretch as road.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoadWorksTests {
    private RoadWorksTests() {}

    private static final ResourceLocation TRAIL = LotrPort.id("trail");
    private static final ResourceLocation DIRT_PATH = LotrPort.id("dirt_path");
    private static final ResourceLocation COBBLE = LotrPort.id("cobble_road");

    @GameTest(template = "empty", batch = "road_works")
    public static void stretchPavesNaturalGroundOnly(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 1; y < 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
                helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
            }
        }
        helper.setBlock(new BlockPos(8, 1, 7), Blocks.OAK_PLANKS);
        helper.setBlock(new BlockPos(4, 2, 7), Blocks.SHORT_GRASS);
        BlockPos a = helper.absolutePos(new BlockPos(2, 1, 7));
        BlockPos b = helper.absolutePos(new BlockPos(13, 1, 7));
        RoadWorks works = RoadWorks.get(server);
        RoadWorks.Stretch s = works.add(server, helper.getLevel().dimension(),
                List.of(new RoadWorks.Point(a.getX(), a.getZ()), new RoadWorks.Point(b.getX(), b.getZ())), COBBLE,
                RoadWorks.COMMAND);
        try {
            RoadPaving.flush(server);
            List<net.minecraft.world.level.block.Block> laid = List.of(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
                    Blocks.GRAVEL);
            for (int x = 2; x < 14; x++) {
                if (x == 8) {
                    continue;
                }
                BlockState st = helper.getBlockState(new BlockPos(x, 1, 7));
                helper.assertTrue(laid.contains(st.getBlock()), "road column " + x + " is " + st + ", not cobble road");
            }
            helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(8, 1, 7));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 2, 7));
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(5, 1, 12));
            helper.assertTrue(works.snapshot().road(helper.getLevel().dimension(), a.getX() + 3, a.getZ()),
                    "routes don't see the cobble road");
            helper.assertTrue(works.at(helper.getLevel().dimension(), a.getX() + 3, a.getZ() + 1).isPresent(),
                    "no stretch at a road column");
        } finally {
            works.remove(s.id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "road_works")
    public static void trafficWearsTrailsButNeverBuildsRoads(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        RoadTier path = RoadData.tier(DIRT_PATH).orElse(null);
        helper.assertTrue(path != null && path.byTraffic(), "no dirt_path tier worn by traffic");
        helper.assertFalse(RoadData.tier(COBBLE).map(RoadTier::byTraffic).orElse(true), "cobble roads wear in");
        RoadWorks works = RoadWorks.get(server);
        // far off, so nothing is laid: only the tiers change
        RoadWorks.Stretch s = works.add(server, helper.getLevel().dimension(),
                List.of(new RoadWorks.Point(900_000, 900_000), new RoadWorks.Point(900_100, 900_000)), TRAIL,
                RoadWorks.TRAFFIC);
        try {
            helper.assertTrue(works.addWear(server, s, path.wear() - 1).equals(TRAIL),
                    "a trail became a path before its wear");
            helper.assertTrue(works.addWear(server, s, 1).equals(DIRT_PATH), "a worn trail didn't become a path");
            works.addWear(server, s, 1_000_000);
            helper.assertTrue(RoadData.tier(s.tier()).map(RoadTier::byTraffic).orElse(false),
                    "traffic alone made " + s.tier());
            works.setTier(server, s, COBBLE);
            helper.assertTrue(s.tier().equals(COBBLE), "waymakers couldn't set a cobble road");
        } finally {
            works.remove(s.id);
        }
        helper.succeed();
    }
}
