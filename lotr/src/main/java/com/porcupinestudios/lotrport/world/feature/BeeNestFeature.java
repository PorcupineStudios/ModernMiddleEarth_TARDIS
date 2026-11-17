package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Feature {@code lotr_port:bee_nest}: a wild bee nest on a tree, as vanilla's beehive tree decorator puts on its own
 * trees (LOTR's trees are templates without decorators). From the top of the canopy (the placement's heightmap) it
 * looks down for the highest log with open air beside it under the leaves, and hangs a nest there facing out, with
 * {@code min_bees}..{@code max_bees} bees inside. Numbers: the configured feature JSON (converter/land.py).
 */
public final class BeeNestFeature extends Feature<BeeNestFeature.Config> {
    public record Config(int depth, int minBees, int maxBees) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("depth", 24).forGetter(Config::depth),
                Codec.INT.optionalFieldOf("min_bees", 2).forGetter(Config::minBees),
                Codec.INT.optionalFieldOf("max_bees", 3).forGetter(Config::maxBees)).apply(i, Config::new));
    }

    public BeeNestFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos.MutableBlockPos pos = context.origin().mutable();
        boolean underLeaves = false;
        for (int i = 0; i < config.depth(); i++, pos.move(Direction.DOWN)) {
            var state = level.getBlockState(pos);
            if (state.is(BlockTags.LEAVES)) {
                underLeaves = true;
                continue;
            }
            if (!state.is(BlockTags.LOGS)) {
                if (!state.isAir()) {
                    return false; // ground or something else: no tree here
                }
                continue;
            }
            if (!underLeaves) {
                continue;
            }
            Direction start = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            for (int d = 0; d < 4; d++) {
                Direction face = Direction.from2DDataValue((start.get2DDataValue() + d) & 3);
                BlockPos at = pos.relative(face);
                if (level.isEmptyBlock(at) && level.isEmptyBlock(at.below())) {
                    level.setBlock(at, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, face), 2);
                    level.getBlockEntity(at, BlockEntityType.BEEHIVE).ifPresent(hive -> {
                        int bees = config.minBees() + random.nextInt(Math.max(1, config.maxBees() - config.minBees() + 1));
                        for (int b = 0; b < bees; b++) {
                            hive.storeBee(BeehiveBlockEntity.Occupant.create(random.nextInt(599)));
                        }
                    });
                    return true;
                }
            }
        }
        return false;
    }
}
