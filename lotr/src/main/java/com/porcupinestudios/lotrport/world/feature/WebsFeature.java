package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Feature {@code lotr_port:webs}: the spiders' nests of Mirkwood (The Hobbit's "Flies and Spiders": webs slung from
 * tree to tree, bundles hanging from the boughs). Placed once per chunk; {@code tries} random columns each:
 * <ul>
 * <li>under the lowest leaves of the column a curtain of {@code web} {@code min_hang}-{@code max_hang} blocks long
 * hangs down through the air (stops at anything solid);</li>
 * <li>with {@code cocoon_chance} a {@code cocoon} bundle (two blocks) hangs there instead;</li>
 * <li>with {@code ground_chance} a web is spun at the foot of a trunk next to the column (on the ground beside a
 * log).</li>
 * </ul>
 */
public final class WebsFeature extends Feature<WebsFeature.Config> {
    public record Config(BlockState web, int tries, int minHang, int maxHang, Optional<BlockState> cocoon,
            float cocoonChance, float groundChance) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("web").forGetter(Config::web),
                Codec.intRange(0, 256).fieldOf("tries").forGetter(Config::tries),
                Codec.intRange(0, 16).optionalFieldOf("min_hang", 1).forGetter(Config::minHang),
                Codec.intRange(0, 16).optionalFieldOf("max_hang", 4).forGetter(Config::maxHang),
                BlockState.CODEC.optionalFieldOf("cocoon").forGetter(Config::cocoon),
                Codec.floatRange(0, 1).optionalFieldOf("cocoon_chance", 0f).forGetter(Config::cocoonChance),
                Codec.floatRange(0, 1).optionalFieldOf("ground_chance", 0.3f).forGetter(Config::groundChance))
                .apply(i, Config::new));
    }

    public WebsFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        Config config = context.config();
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        boolean placed = false;
        for (int n = 0; n < config.tries(); n++) {
            int x = x0 + random.nextInt(16);
            int z = z0 + random.nextInt(16);
            // WORLD_SURFACE: the _WG heightmaps aren't kept up while features are placed (they stop under the trees)
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x,
                    level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
            boolean inLeaves = false;
            while (p.getY() > level.getMinBuildHeight()) { // down through the crown to the air under it
                BlockState s = level.getBlockState(p);
                if (s.is(BlockTags.LEAVES)) {
                    inLeaves = true;
                } else if (s.isAir()) {
                    if (inLeaves) {
                        break;
                    }
                } else if (!inLeaves || !(s.is(BlockTags.LOGS) || s.getCollisionShape(level, p).isEmpty())) {
                    // ground, or something solid that isn't the crown's own trunk; vines hanging in and under the
                    // crown (Mirkwood's) are passed through (2026-11-25 preview: they stopped 9 in 10 webs)
                    inLeaves = false;
                    break;
                }
                p.move(Direction.DOWN);
            }
            if (inLeaves) {
                if (config.cocoon().isPresent() && random.nextFloat() < config.cocoonChance()
                        && level.isEmptyBlock(p.below())) {
                    level.setBlock(p, config.cocoon().get(), Block.UPDATE_CLIENTS);
                    level.setBlock(p.below(), config.cocoon().get(), Block.UPDATE_CLIENTS);
                } else {
                    int hang = config.minHang() + random.nextInt(Math.max(1, config.maxHang() - config.minHang() + 1));
                    for (int k = 0; k < hang && level.isEmptyBlock(p); k++) {
                        level.setBlock(p, config.web(), Block.UPDATE_CLIENTS);
                        p.move(Direction.DOWN);
                    }
                }
                placed = true;
            }
            if (random.nextFloat() < config.groundChance()) {
                placed |= trunkWeb(level, config, x, z, random);
            }
        }
        return placed;
    }

    /** A web on the ground beside a trunk near the column, if there is one. */
    private static boolean trunkWeb(WorldGenLevel level, Config config, int x, int z, RandomSource random) {
        BlockPos at = new BlockPos(x, EnclosureFeature.ground(level, x, z) + 1, z);
        if (!level.isEmptyBlock(at)) {
            return false;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(at.relative(d)).is(BlockTags.LOGS)) {
                level.setBlock(at, config.web(), Block.UPDATE_CLIENTS);
                if (random.nextBoolean() && level.isEmptyBlock(at.above())) {
                    level.setBlock(at.above(), config.web(), Block.UPDATE_CLIENTS);
                }
                return true;
            }
        }
        return false;
    }
}
