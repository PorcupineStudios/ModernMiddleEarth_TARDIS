package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Feature {@code lotr_port:forest_floor}: LOTR's forest litter after the trees (LOTRBiomeDecorator): {@code fallen}
 * patches of fallen leaves of a nearby tree's leaves (LOTRWorldGenFallenLeaves), {@code bushes} bushes of a nearby
 * tree's leaves (LOTRWorldGenBushes) and {@code logs} rotten logs, lying or upright (LOTRWorldGenLogs). Fractional
 * counts are a chance of one more. Placed once per chunk, from its corner.
 */
public final class ForestFloorFeature extends Feature<ForestFloorFeature.Config> {
    public record Config(float fallen, float bushes, int logs) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("fallen", 0f).forGetter(Config::fallen),
                Codec.FLOAT.optionalFieldOf("bushes", 0f).forGetter(Config::bushes),
                Codec.INT.optionalFieldOf("logs", 0).forGetter(Config::logs)).apply(i, Config::new));
    }

    public ForestFloorFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        for (int n = count(context.config().fallen(), random); n > 0; n--) {
            fallenLeaves(level, random, ground(level, x0 + random.nextInt(16), z0 + random.nextInt(16)));
        }
        for (int n = count(context.config().bushes(), random); n > 0; n--) {
            bush(level, random, ground(level, x0 + random.nextInt(16), z0 + random.nextInt(16)));
        }
        for (int n = context.config().logs(); n > 0; n--) {
            log(level, random, ground(level, x0 + random.nextInt(16), z0 + random.nextInt(16)));
        }
        return true;
    }

    private static int count(float f, RandomSource random) {
        return (int) f + (random.nextFloat() < f - (int) f ? 1 : 0);
    }

    private static BlockPos ground(WorldGenLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Leaves within 5 blocks sideways and 12 up (LOTR's 40 tries), or null. */
    private static BlockState nearbyLeaves(WorldGenLevel level, RandomSource random, BlockPos at) {
        for (int i = 0; i < 40; i++) {
            BlockState s = level.getBlockState(at.offset(random.nextInt(6) - random.nextInt(6), random.nextInt(12),
                    random.nextInt(6) - random.nextInt(6)));
            if (s.is(BlockTags.LEAVES)) {
                return s;
            }
        }
        return null;
    }

    private static boolean free(WorldGenLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.canBeReplaced() && s.getFluidState().isEmpty();
    }

    private static void fallenLeaves(WorldGenLevel level, RandomSource random, BlockPos at) {
        BlockState leaves = nearbyLeaves(level, random, at);
        if (leaves == null) {
            return;
        }
        // lotr_port:fallen_<wood>_leaves for <any ns>:<wood>_leaves
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(leaves.getBlock());
        Block fallen = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID,
                "fallen_" + id.getPath()));
        if (fallen == Blocks.AIR) {
            return;
        }
        for (int i = 0; i < 64; i++) {
            BlockPos pos = at.offset(random.nextInt(5) - random.nextInt(5), random.nextInt(3) - random.nextInt(3),
                    random.nextInt(5) - random.nextInt(5));
            if (free(level, pos) && fallen.defaultBlockState().canSurvive(level, pos)) {
                level.setBlock(pos, fallen.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void bush(WorldGenLevel level, RandomSource random, BlockPos at) {
        BlockState leaves = nearbyLeaves(level, random, at);
        if (leaves == null || !level.getBlockState(at.below()).is(BlockTags.DIRT)) {
            return;
        }
        if (leaves.hasProperty(BlockStateProperties.PERSISTENT)) {
            leaves = leaves.setValue(BlockStateProperties.PERSISTENT, true);
        }
        int size = random.nextInt(3) == 0 ? 1 : 0;
        for (int dx = -size; dx <= size; dx++) {
            for (int dz = -size; dz <= size; dz++) {
                BlockPos pos = at.offset(dx, 0, dz);
                boolean corner = size != 0 && Math.abs(dx) == size && Math.abs(dz) == size;
                if ((!corner || random.nextInt(3) == 0) && free(level, pos)) {
                    level.setBlock(pos, leaves, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static boolean logFits(WorldGenLevel level, BlockPos pos) {
        return free(level, pos) && level.getBlockState(pos.below()).is(BlockTags.DIRT);
    }

    private static void log(WorldGenLevel level, RandomSource random, BlockPos at) {
        Block rotten = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID,
                "rotten_wood"));
        if (rotten == Blocks.AIR || !logFits(level, at)) {
            return;
        }
        int kind = random.nextInt(5); // LOTR: 1 in 5 along x, 1 in 5 along z, else a stump
        if (kind > 1) {
            level.setBlock(at, rotten.defaultBlockState(), Block.UPDATE_CLIENTS);
            return;
        }
        Direction along = kind == 0 ? Direction.EAST : Direction.SOUTH;
        BlockState state = rotten.defaultBlockState().setValue(BlockStateProperties.AXIS, along.getAxis());
        BlockPos pos = at;
        for (int n = 2 + random.nextInt(6); n > 0 && logFits(level, pos); n--) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            if (level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) {
                level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            pos = pos.relative(along);
        }
    }
}
