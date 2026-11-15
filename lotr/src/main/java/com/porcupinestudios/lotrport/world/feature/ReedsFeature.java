package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.content.ReedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * Feature {@code lotr_port:reeds}: a patch of LOTR's reeds (LOTRWorldGenReeds): {@code tries} spots within 8 blocks
 * (4 up or down) of the origin; a spot is used only over still water at most {@link #MAX_DEPTH} deep, not where it
 * would freeze. The stem fills the water from the bed up (waterlogged) and rises 1 to 3 blocks above it, so reeds
 * never stand on dry land or float on deep water.
 */
public final class ReedsFeature extends Feature<ReedsFeature.Config> {
    /** LOTR: the water under a reed patch spot is under 5 deep. */
    public static final int MAX_DEPTH = 4;

    public record Config(BlockState block, int tries) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("block").forGetter(Config::block),
                Codec.INT.optionalFieldOf("tries", 16).forGetter(Config::tries)).apply(i, Config::new));
    }

    public ReedsFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockState reed = context.config().block();
        if (!(reed.getBlock() instanceof ReedBlock block)) {
            return false;
        }
        boolean placed = false;
        for (int t = 0; t < context.config().tries(); t++) {
            BlockPos pos = origin.offset(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4),
                    random.nextInt(8) - random.nextInt(8));
            if (!level.isEmptyBlock(pos) || !still(level, pos.below())) {
                continue;
            }
            BlockPos bed = pos.below();
            int depth = 0;
            while (still(level, bed) && depth <= MAX_DEPTH) {
                bed = bed.below();
                depth++;
            }
            if (depth > MAX_DEPTH || !level.getBlockState(bed).isFaceSturdy(level, bed, Direction.UP)
                    || !level.getBiome(pos).value().warmEnoughToRain(pos)) {
                continue;
            }
            int above = 1 + random.nextInt(ReedBlock.MAX_ABOVE_WATER);
            BlockPos.MutableBlockPos at = bed.above().mutable();
            for (int y = 0; y < depth + above && (y < depth || level.isEmptyBlock(at)); y++, at.move(0, 1, 0)) {
                level.setBlock(at, reed.setValue(ReedBlock.WATERLOGGED, y < depth), Block.UPDATE_CLIENTS);
            }
            for (BlockPos p = bed.above(); p.getY() < at.getY(); p = p.above()) {
                level.setBlock(p, block.withPart(level.getBlockState(p), level, p), Block.UPDATE_CLIENTS);
            }
            placed = true;
        }
        return placed;
    }

    private static boolean still(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSourceOfType(Fluids.WATER);
    }
}
