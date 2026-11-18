package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Feature {@code lotr_port:heart_of_the_mountain} (Aaron 2026-11-17): one block, exactly one per mountain, deep in
 * the mountain's general centre with a lot of randomness. Where it is is a pure function of the world seed and the
 * map, so every chunk agrees without saved state and it is placed only by the chunk that holds it:
 * <ul>
 * <li>a <b>mountain</b> of a range is one {@code cell}-block square of a seeded grid whose highest sampled ground
 * ({@code samples} x {@code samples}) reaches {@code min_ground}; its heart is near that summit, moved up to
 * {@code jitter} x cell sideways;</li>
 * <li>each of LOTR's single peaks (Erebor...; not volcanoes) is a mountain too, its heart moved up to
 * {@code jitter} x its range from the peak.</li>
 * </ul>
 * Its height is {@code depth_min}..{@code depth_max} of the way from {@code base} up to the ground over it, and at
 * least {@code cover} blocks under it.
 */
public final class HeartOfTheMountainFeature extends Feature<HeartOfTheMountainFeature.Config> {
    public record Config(BlockStateProvider block, int cell, int samples, int minGround, double jitter, int base,
            double depthMin, double depthMax, int cover) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("block").forGetter(Config::block),
                Codec.INT.fieldOf("cell").forGetter(Config::cell),
                Codec.INT.fieldOf("samples").forGetter(Config::samples),
                Codec.INT.fieldOf("min_ground").forGetter(Config::minGround),
                Codec.DOUBLE.fieldOf("jitter").forGetter(Config::jitter),
                Codec.INT.fieldOf("base").forGetter(Config::base),
                Codec.DOUBLE.fieldOf("depth_min").forGetter(Config::depthMin),
                Codec.DOUBLE.fieldOf("depth_max").forGetter(Config::depthMax),
                Codec.INT.fieldOf("cover").forGetter(Config::cover)).apply(i, Config::new));
    }

    /** A cell with no mountain. */
    private static final long NONE = Long.MIN_VALUE;
    private static final ConcurrentHashMap<Long, Long> CELLS = new ConcurrentHashMap<>();
    private static volatile long cachedFor;

    public HeartOfTheMountainFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config c = context.config();
        WorldGenLevel level = context.level();
        long seed = level.getSeed();
        MiddleEarthMap map = MiddleEarthMap.get();
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        boolean placed = false;
        int cx0 = Math.floorDiv(x0, c.cell());
        int cz0 = Math.floorDiv(z0, c.cell());
        // the heart may be moved out of its own cell: look in the neighbours too
        int reach = (int) Math.ceil(c.jitter()) + 1;
        for (int cx = cx0 - reach; cx <= cx0 + reach; cx++) {
            for (int cz = cz0 - reach; cz <= cz0 + reach; cz++) {
                long heart = cellHeart(map, c, seed, cx, cz);
                if (heart != NONE) {
                    placed |= placeIfHere(context, BlockPos.of(heart), x0, z0);
                }
            }
        }
        List<MiddleEarthMap.Mountain> peaks = map.mountains();
        for (int i = 0; i < peaks.size(); i++) {
            MiddleEarthMap.Mountain m = peaks.get(i);
            if (m.crater() > 0 || Math.abs(m.x() - x0) > m.range() + 16 || Math.abs(m.z() - z0) > m.range() + 16) {
                continue;
            }
            long h = hash(seed, i, 0x5045414BL);
            double off = c.jitter() * m.range();
            int x = m.x() + (int) Math.round((unit(h, 1) * 2 - 1) * off);
            int z = m.z() + (int) Math.round((unit(h, 2) * 2 - 1) * off);
            BlockPos pos = depth(map, c, x, z, unit(h, 3));
            if (pos != null) {
                placed |= placeIfHere(context, pos, x0, z0);
            }
        }
        return placed;
    }

    private static boolean placeIfHere(FeaturePlaceContext<Config> context, BlockPos pos, int x0, int z0) {
        if (pos.getX() < x0 || pos.getX() > x0 + 15 || pos.getZ() < z0 || pos.getZ() > z0 + 15) {
            return false;
        }
        WorldGenLevel level = context.level();
        if (level.getBlockState(pos).is(Blocks.BEDROCK)) {
            return false;
        }
        BlockState state = context.config().block().getState(context.random(), pos);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        return true;
    }

    /** The heart of one grid cell (a packed BlockPos), or NONE when the cell holds no mountain. Cached per seed. */
    private static long cellHeart(MiddleEarthMap map, Config c, long seed, int cx, int cz) {
        if (cachedFor != seed) {
            CELLS.clear();
            cachedFor = seed;
        }
        if (CELLS.size() > 8192) {
            CELLS.clear();
        }
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Long known = CELLS.get(key);
        if (known != null) {
            return known;
        }
        long h = hash(seed, cx, cz);
        int n = Math.max(2, c.samples());
        double best = Double.NEGATIVE_INFINITY;
        int bx = 0;
        int bz = 0;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < n; k++) {
                int x = cx * c.cell() + (int) ((i + 0.5) * c.cell() / n);
                int z = cz * c.cell() + (int) ((k + 0.5) * c.cell() / n);
                double g = map.heightAt(x, z);
                if (g > best) {
                    best = g;
                    bx = x;
                    bz = z;
                }
            }
        }
        long result = NONE;
        if (best >= c.minGround()) {
            double off = c.jitter() * c.cell();
            int x = bx + (int) Math.round((unit(h, 1) * 2 - 1) * off);
            int z = bz + (int) Math.round((unit(h, 2) * 2 - 1) * off);
            BlockPos pos = depth(map, c, x, z, unit(h, 3));
            if (pos != null) {
                result = pos.asLong();
            }
        }
        CELLS.put(key, result);
        return result;
    }

    private static BlockPos depth(MiddleEarthMap map, Config c, int x, int z, double t) {
        double ground = map.heightAt(x, z);
        if (ground - c.cover() <= c.base()) {
            return null;
        }
        double f = c.depthMin() + (c.depthMax() - c.depthMin()) * t;
        int y = (int) Math.min(ground - c.cover(), c.base() + f * (ground - c.base()));
        return new BlockPos(x, y, z);
    }

    private static long hash(long seed, long a, long b) {
        long h = seed ^ 0x48454152544DL;
        h = mix(h + a * 0x9E3779B97F4A7C15L);
        return mix(h + b * 0xC2B2AE3D27D4EB4FL);
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    private static double unit(long h, int n) {
        return (mix(h + n * 0x632BE59BD9B4E019L) >>> 11) * 0x1.0p-53;
    }
}
