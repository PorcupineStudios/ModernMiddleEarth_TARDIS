package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Feature {@code lotr_port:old_walls} (seeded by the world): the tumbled dry-stone field walls of Arnor's lost farms (Eriador "was once
 * well-peopled"; after Arnor fell its fields went back to the wild). Walls run along a grid of fields roughly
 * {@code field} blocks across (each line shifted by its own offset), and only inside the biome variants listed in
 * {@code variants}. Each stretch between two crossings stands with chance {@code keep}; along a standing stretch, a
 * smooth noise knocks gaps out of it ({@code ruin}: share fallen) and makes it one or two blocks high. Blocks are drawn
 * from {@code blocks} by weight. Placed once per chunk; it never builds on water, leaves or anything that isn't ground.
 */
public final class OldWallsFeature extends Feature<OldWallsFeature.Config> {
    public record Config(SimpleWeightedRandomList<BlockState> blocks, int field, float keep, float ruin,
            float tall, List<String> variants) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                SimpleWeightedRandomList.wrappedCodec(BlockState.CODEC).fieldOf("blocks").forGetter(Config::blocks),
                Codec.intRange(8, 256).fieldOf("field").forGetter(Config::field),
                Codec.floatRange(0, 1).fieldOf("keep").forGetter(Config::keep),
                Codec.floatRange(0, 1).fieldOf("ruin").forGetter(Config::ruin),
                Codec.floatRange(0, 1).optionalFieldOf("tall", 0.3f).forGetter(Config::tall),
                Codec.STRING.listOf().fieldOf("variants").forGetter(Config::variants))
                .apply(i, Config::new));
    }

    public OldWallsFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        Config config = context.config();
        MiddleEarthMap map = MiddleEarthMap.get();
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        boolean placed = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                // a wall along x (at a z line) or along z (at an x line)
                int along = onLine(z, config.field(), 0x5A) ? x : onLine(x, config.field(), 0x58) ? z : Integer.MIN_VALUE;
                if (along == Integer.MIN_VALUE) {
                    continue;
                }
                boolean alongX = onLine(z, config.field(), 0x5A);
                int line = alongX ? lineIndex(z, config.field(), 0x5A) : lineIndex(x, config.field(), 0x58);
                int stretch = Math.floorDiv(along, config.field());
                if (unit(hash(line, stretch, alongX ? 1 : 2)) >= config.keep()) {
                    continue;
                }
                double n = noise(along / 9.0, line * 7.13 + (alongX ? 0 : 500));
                if (n < config.ruin()) {
                    continue;
                }
                String v = map.variantAt(x, z);
                if (!config.variants().contains(v == null ? "plain" : v)) {
                    continue;
                }
                int y = EnclosureFeature.ground(level, x, z);
                BlockPos ground = new BlockPos(x, y, z);
                BlockState top = level.getBlockState(ground);
                if (!top.getFluidState().isEmpty() || !top.isSolidRender(level, ground)) {
                    continue;
                }
                int height = n > 1 - config.tall() * (1 - config.ruin()) ? 2 : 1;
                for (int k = 1; k <= height; k++) {
                    BlockPos p = ground.above(k);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir() && !s.canBeReplaced()) {
                        break;
                    }
                    BlockState block = config.blocks().getRandomValue(context.random()).orElse(null);
                    if (block != null) {
                        level.setBlock(p, block, Block.UPDATE_CLIENTS);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    /** Whether a coordinate lies on one of the grid's lines (each line shifted by up to a third of a field). */
    private static boolean onLine(int c, int field, long salt) {
        int i = Math.floorDiv(c, field);
        for (int k = i - 1; k <= i + 1; k++) {
            if (c == linePos(k, field, salt)) {
                return true;
            }
        }
        return false;
    }

    private static int lineIndex(int c, int field, long salt) {
        int i = Math.floorDiv(c, field);
        for (int k = i - 1; k <= i + 1; k++) {
            if (c == linePos(k, field, salt)) {
                return k;
            }
        }
        return i;
    }

    private static int linePos(int k, int field, long salt) {
        return k * field + (int) (unit(hash(k, 0, salt)) * field / 3);
    }

    /** Smooth 2D value noise in [0, 1). */
    private static double noise(double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = x - x0;
        double tz = z - z0;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = unit(hash(x0, z0, 77));
        double b = unit(hash(x0 + 1, z0, 77));
        double c = unit(hash(x0, z0 + 1, 77));
        double d = unit(hash(x0 + 1, z0 + 1, 77));
        double top = a + (b - a) * tx;
        return top + (c + (d - c) * tx - top) * tz;
    }

    private static long hash(int a, int b, long salt) {
        // seeded: each world's old fields lie differently (Aaron 2026-11-25)
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L
                ^ MiddleEarthMap.seed() * 0x27D4EB2F165667C5L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }
}
