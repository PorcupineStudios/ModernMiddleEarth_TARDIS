package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.world.map.Lairs;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Feature {@code lotr_port:spider_nest}: the core of a lair ({@link Lairs}: Mirkwood's spider nests, about 200 blocks
 * square round the lair's middle). Placed once per chunk; builds the part of its lair's core in the chunk:
 * <ul>
 * <li>{@code web} thick between the ground and {@code height} blocks up, chance {@code density} in the middle thinning
 * to the edge and upwards;</li>
 * <li>the floor strewn with {@code floor} (chance {@code floor_chance});</li>
 * <li>an egg sac ({@code sac}) on the ground at each of the lair's sac spots: break them to kill the nest
 * ({@code lair/LairLife});</li>
 * <li>{@code cocoons} silk-wrapped bundles ({@code cocoon}, two blocks) propped among the webs.</li>
 * </ul>
 * Rolls come from the lair's own seed, so the parts built in different chunks agree.
 */
public final class SpiderNestFeature extends Feature<SpiderNestFeature.Config> {
    public record Config(String lair, BlockState web, BlockState sac, float density, int height,
            Optional<BlockState> floor, float floorChance, Optional<BlockState> cocoon, int cocoons)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("lair").forGetter(Config::lair),
                BlockState.CODEC.fieldOf("web").forGetter(Config::web),
                BlockState.CODEC.fieldOf("sac").forGetter(Config::sac),
                Codec.floatRange(0, 1).fieldOf("density").forGetter(Config::density),
                Codec.intRange(1, 16).fieldOf("height").forGetter(Config::height),
                BlockState.CODEC.optionalFieldOf("floor").forGetter(Config::floor),
                Codec.floatRange(0, 1).optionalFieldOf("floor_chance", 0f).forGetter(Config::floorChance),
                BlockState.CODEC.optionalFieldOf("cocoon").forGetter(Config::cocoon),
                Codec.intRange(0, 32).optionalFieldOf("cocoons", 0).forGetter(Config::cocoons))
                .apply(i, Config::new));
    }

    public SpiderNestFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        Config config = context.config();
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        boolean placed = false;
        for (Lairs.Lair lair : MiddleEarthMap.get().lairsInCellsOf(x0, z0)) {
            int core = lair.kind().core();
            if (!lair.kind().name().equals(config.lair()) || lair.x() + core < x0 || lair.x() - core > x0 + 15
                    || lair.z() + core < z0 || lair.z() - core > z0 + 15) {
                continue;
            }
            RandomSource random = RandomSource.create(lair.seed() ^ (long) x0 * 341873128712L ^ (long) z0 * 132897987541L);
            for (int x = x0; x < x0 + 16; x++) {
                for (int z = z0; z < z0 + 16; z++) {
                    double d = lair.distance(x, z);
                    if (d > core) {
                        continue;
                    }
                    int y = EnclosureFeature.ground(level, x, z);
                    BlockPos ground = new BlockPos(x, y, z);
                    BlockState top = level.getBlockState(ground);
                    if (!top.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (config.floor().isPresent() && top.is(BlockTags.DIRT) && random.nextFloat() < config.floorChance()) {
                        level.setBlock(ground, config.floor().get(), Block.UPDATE_CLIENTS);
                    }
                    double middle = 1 - 0.7 * (d / core) * (d / core);
                    for (int k = 1; k <= config.height(); k++) {
                        BlockPos p = ground.above(k);
                        double up = 1 - 0.5 * (k - 1) / config.height();
                        if (open(level, p) && random.nextFloat() < config.density() * middle * up) {
                            level.setBlock(p, config.web(), Block.UPDATE_CLIENTS);
                            placed = true;
                        }
                    }
                }
            }
            for (int[] s : lair.sacs()) {
                if (inChunk(s[0], s[1], x0, z0)) {
                    BlockPos ground = new BlockPos(s[0], EnclosureFeature.ground(level, s[0], s[1]), s[1]);
                    if (level.getBlockState(ground).getFluidState().isEmpty() && open(level, ground.above())) {
                        level.setBlock(ground.above(), config.sac(), Block.UPDATE_CLIENTS);
                        placed = true;
                    }
                }
            }
            if (config.cocoon().isPresent()) {
                for (int n = 0; n < config.cocoons(); n++) {
                    double a = Math.PI * 2 * lair.roll(2 * n);
                    double dist = core * (0.3 + 0.6 * lair.roll(2 * n + 1));
                    int x = lair.x() + (int) Math.round(Math.cos(a) * dist);
                    int z = lair.z() + (int) Math.round(Math.sin(a) * dist);
                    if (!inChunk(x, z, x0, z0)) {
                        continue;
                    }
                    BlockPos ground = new BlockPos(x, EnclosureFeature.ground(level, x, z), z);
                    if (level.getBlockState(ground).getFluidState().isEmpty() && open(level, ground.above())
                            && open(level, ground.above(2))) {
                        level.setBlock(ground.above(), config.cocoon().get(), Block.UPDATE_CLIENTS);
                        level.setBlock(ground.above(2), config.cocoon().get(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return placed;
    }

    private static boolean inChunk(int x, int z, int x0, int z0) {
        return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
    }

    /** Air, or a plant or web that may give way. */
    private static boolean open(WorldGenLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()
                || s.is(net.minecraft.world.level.block.Blocks.COBWEB);
    }
}
