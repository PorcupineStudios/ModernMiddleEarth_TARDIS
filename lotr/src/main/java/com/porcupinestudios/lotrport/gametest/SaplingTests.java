package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrSaplingBlock;
import com.porcupinestudios.lotrport.content.SaplingRules;
import com.porcupinestudios.lotrport.tree.Trees;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Saplings grow as LOTR's: the rules read from LOTR's sapling classes are there, and a 3x3 of beech saplings grows
 * the beech party tree with its trunk where the saplings stood, while a lone sapling grows a tree of its own.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SaplingTests {
    private SaplingTests() {}

    @GameTest(template = "empty")
    public static void saplingsGrowLotrTrees(GameTestHelper helper) {
        Block beech = BuiltInRegistries.BLOCK.get(LotrPort.id("beech_sapling"));
        helper.assertTrue(beech instanceof LotrSaplingBlock, "beech_sapling is not a growing sapling");
        helper.assertTrue(SaplingRules.of(LotrPort.id("beech_sapling")).map(r -> r.rules().size() >= 3).orElse(false),
                "beech sapling rules missing (party, large, normal)");
        helper.assertTrue(SaplingRules.of(LotrPort.id("mallorn_sapling")).map(r -> r.rules().stream()
                .anyMatch(SaplingRules.Rule::cross)).orElse(false), "mallorn boughs (plus of five) rule missing");

        // a 3x3 party tree: the whole square becomes trunk
        BlockPos corner = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                helper.getLevel().setBlock(corner.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                helper.getLevel().setBlock(corner.offset(dx, 0, dz), beech.defaultBlockState(), 3);
            }
        }
        boolean grew = ((LotrSaplingBlock) beech).grow(helper.getLevel(), corner.offset(1, 0, 1),
                helper.getLevel().getRandom());
        helper.assertTrue(grew, "a 3x3 of beech saplings grew nothing");
        int saplings = 0;
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                if (helper.getLevel().getBlockState(corner.offset(dx, 0, dz)).is(beech)) {
                    saplings++;
                }
            }
        }
        helper.assertTrue(saplings == 0, saplings + " saplings left in the party tree's square");
        helper.assertTrue(helper.getLevel().getBlockState(corner.offset(1, 0, 1)).is(BlockTags.LOGS)
                || !helper.getLevel().getBlockState(corner.offset(1, 0, 1)).isAir(), "no trunk in the square's middle");

        // a colonist (or anyone) felling it from one trunk log takes the whole tree and gets the sapling back
        BlockPos log = null;
        for (int dy = 0; dy < 4 && log == null; dy++) {
            for (int dx = 0; dx < 3 && log == null; dx++) {
                for (int dz = 0; dz < 3 && log == null; dz++) {
                    BlockPos p = corner.offset(dx, dy, dz);
                    if (Trees.isLog(helper.getLevel().getBlockState(p))) {
                        log = p;
                    }
                }
            }
        }
        helper.assertTrue(log != null, "no trunk log to fell");
        Optional<Trees.Tree> tree = Trees.find(helper.getLevel(), log);
        helper.assertTrue(tree.isPresent() && tree.get().record().isPresent(), "the grown tree was not recorded");
        int logs = tree.get().logs().size();
        Optional<Trees.Harvest> cut = Trees.harvest(helper.getLevel(), log, null, true);
        helper.assertTrue(cut.isPresent() && !cut.get().drops().isEmpty(), "felling gave no drops");
        for (BlockPos p : tree.get().logs()) {
            helper.assertTrue(!Trees.isLog(helper.getLevel().getBlockState(p)), "a log was left standing at " + p);
        }
        helper.assertTrue(cut.get().sapling().map(b -> b == beech).orElse(false), "no beech sapling to replant");
        helper.assertTrue(logs > 1, "felled tree had only " + logs + " logs");
        helper.succeed();
    }
}
