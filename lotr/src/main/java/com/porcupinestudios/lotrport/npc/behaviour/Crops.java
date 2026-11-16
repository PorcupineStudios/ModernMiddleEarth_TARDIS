package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.content.farm.BerryBushBlock;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.phys.AABB;

/** Animals raiding farms (LOTR's birds and rabbits): what they eat and what keeps them off. */
final class Crops {
    private Crops() {}

    /** Mob griefing on (LOTRMod.canGrief). */
    static boolean canGrief(Level level) {
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** A crop, or (birds) a berry bush with berries. */
    static boolean edible(Level level, BlockPos pos, boolean berries) {
        Block b = level.getBlockState(pos).getBlock();
        return b instanceof CropBlock || berries && b instanceof BerryBushBlock bush && bush.hasBerries();
    }

    /** A crop is eaten whole; a bush loses its berries. */
    static void eat(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof BerryBushBlock bush) {
            bush.strip(level, pos);
        } else {
            level.removeBlock(pos, false);
        }
    }

    /** One random spot within range and y range (of 32 tries) that's edible and passes the check, or null. */
    static BlockPos find(LotrNpc npc, int range, int yRange, boolean berries, java.util.function.Predicate<BlockPos> ok) {
        var r = npc.random();
        BlockPos at = npc.blockPosition();
        for (int i = 0; i < 32; i++) {
            BlockPos p = at.offset(r.nextIntBetweenInclusive(-range, range), r.nextIntBetweenInclusive(-yRange, yRange),
                    r.nextIntBetweenInclusive(-range, range));
            if (edible(npc.level(), p, berries) && ok.test(p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * A scarecrow within 16 (8 up or down) of the spot (LOTRScarecrows): a fence with a pumpkin or a head 2-6 blocks
     * above it, sitting on a solid block.
     */
    static boolean scarecrowNear(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-16, -8, -16), pos.offset(16, 8, 16))) {
            if (level.getBlockState(p).getBlock() instanceof FenceBlock && scarecrowOn(level, p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean scarecrowOn(Level level, BlockPos fence) {
        for (int up = 2; up <= 6; up++) {
            var head = level.getBlockState(fence.above(up));
            if ((head.is(net.minecraft.world.level.block.Blocks.PUMPKIN)
                    || head.getBlock() instanceof net.minecraft.world.level.block.CarvedPumpkinBlock
                    || head.getBlock() instanceof net.minecraft.world.level.block.AbstractSkullBlock)
                    && level.getBlockState(fence.above(up - 1)).isSolidRender(level, fence.above(up - 1))) {
                return true;
            }
        }
        return false;
    }

    /** A farmhand NPC (behaviour_data {@code farmhand}) within 16 of the spot (LOTREntityRabbit.anyFarmhandsNearby). */
    static boolean farmhandNear(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(LotrNpc.class, new AABB(pos).inflate(16),
                n -> CreatureBehaviour.data(n, "farmhand", 0) > 0).isEmpty();
    }
}
