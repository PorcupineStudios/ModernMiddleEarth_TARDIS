package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.world.map.Wilds;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Feature {@code lotr_port:boulders} (Aaron 2026-11-25: "boulder formations ... moss growth"), once per chunk:
 * clusters of 2-6 rounded boulders half sunk in the ground, and now and then a tor (a stack of flattened boulders, the
 * granite tors of the reference photos). Per chunk {@code per_chunk} clusters on average, more where a seeded patch
 * noise is high and up to {@code 1 + east} times as many at the east end ({@code east_from} -> {@code east_to}).
 * Tops are lichened in patches, faces towards the north (the shaded side) mossy; the gap under a boulder on a slope is
 * filled down to the ground so nothing floats. Everything stays inside the chunk and the 8 blocks round it.
 */
public final class BoulderFeature extends Feature<BoulderFeature.Config> {
    public record Config(BlockState rock, BlockState lichen, BlockState moss, float perChunk, float east, int eastFrom,
            int eastTo, float tor, float size) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("rock").forGetter(Config::rock),
                BlockState.CODEC.fieldOf("lichen").forGetter(Config::lichen),
                BlockState.CODEC.fieldOf("moss").forGetter(Config::moss),
                Codec.FLOAT.fieldOf("per_chunk").forGetter(Config::perChunk),
                Codec.FLOAT.fieldOf("east").forGetter(Config::east),
                Codec.INT.fieldOf("east_from").forGetter(Config::eastFrom),
                Codec.INT.fieldOf("east_to").forGetter(Config::eastTo),
                Codec.FLOAT.fieldOf("tor").forGetter(Config::tor),
                Codec.FLOAT.optionalFieldOf("size", 1f).forGetter(Config::size)).apply(i, Config::new));
    }

    public BoulderFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        Config c = context.config();
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        double eastward = c.eastTo() > c.eastFrom()
                ? Math.max(0, Math.min(1, (x0 + 8 - c.eastFrom()) / (double) (c.eastTo() - c.eastFrom()))) : 0;
        double patch = (Wilds.fbm(x0 + 8, z0 + 8, 90, 0x424F554CL) + 1) / 2;
        double rate = c.perChunk() * (1 + c.east() * eastward) * (0.3 + 1.4 * patch);
        int clusters = (int) rate + (random.nextDouble() < rate - (int) rate ? 1 : 0);
        boolean placed = false;
        for (int n = 0; n < clusters; n++) {
            int cx = x0 + 3 + random.nextInt(10);
            int cz = z0 + 3 + random.nextInt(10);
            int gy = EnclosureFeature.ground(level, cx, cz);
            BlockState ground = level.getBlockState(new BlockPos(cx, gy, cz));
            if (!ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES)) {
                continue;
            }
            if (random.nextDouble() < c.tor()) {
                int layers = 3 + random.nextInt(3);
                double r = (2.2 + random.nextDouble() * 1.2) * c.size();
                double y = gy;
                double ox = cx;
                double oz = cz;
                for (int k = 0; k < layers; k++) {
                    double ry = 0.9 + random.nextDouble() * 0.5;
                    placed |= boulder(level, c, random, ox, y + (k == 0 ? 0 : ry * 0.6), oz,
                            r * (0.9 + random.nextDouble() * 0.3), ry, r * (0.8 + random.nextDouble() * 0.3), k == 0);
                    y += ry * 1.5;
                    r *= 0.82;
                    ox += random.nextInt(3) - 1;
                    oz += random.nextInt(3) - 1;
                }
                continue;
            }
            int count = 2 + random.nextInt(5);
            for (int k = 0; k < count; k++) {
                // the first is the biggest; the rest lie round it, smaller
                double big = k == 0 ? 1 : 0.5 + random.nextDouble() * 0.4;
                double rx = (1.2 + random.nextDouble() * 1.6) * big * c.size();
                double rz = (1.2 + random.nextDouble() * 1.6) * big * c.size();
                double ry = Math.min(rx, rz) * (0.65 + random.nextDouble() * 0.35);
                int bx = cx + (k == 0 ? 0 : random.nextInt(9) - 4);
                int bz = cz + (k == 0 ? 0 : random.nextInt(9) - 4);
                int by = EnclosureFeature.ground(level, bx, bz);
                if (!level.getBlockState(new BlockPos(bx, by, bz)).getFluidState().isEmpty()) {
                    continue;
                }
                placed |= boulder(level, c, random, bx, by + 1 - ry * 0.4, bz, rx, ry, rz, true);
            }
        }
        return placed;
    }

    /** One ellipsoid of rock round (x, y, z); {@code fill}: the gap under it is filled down to the ground. */
    private static boolean boulder(WorldGenLevel level, Config c, RandomSource random, double x, double y, double z,
            double rx, double ry, double rz, boolean fill) {
        boolean placed = false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int ix = (int) Math.ceil(rx);
        int iy = (int) Math.ceil(ry);
        int iz = (int) Math.ceil(rz);
        double bump = 0.15 + random.nextDouble() * 0.2; // rough, not perfectly round
        for (int dx = -ix; dx <= ix; dx++) {
            for (int dz = -iz; dz <= iz; dz++) {
                int bx = (int) Math.floor(x) + dx;
                int bz = (int) Math.floor(z) + dz;
                int lowest = Integer.MAX_VALUE;
                for (int dy = -iy; dy <= iy; dy++) {
                    int by = (int) Math.floor(y) + dy;
                    if (!inside(bx, by, bz, x, y, z, rx, ry, rz, bump)) {
                        continue;
                    }
                    p.set(bx, by, bz);
                    if (!takes(level.getBlockState(p))) {
                        continue;
                    }
                    BlockState block = c.rock();
                    if (!inside(bx, by + 1, bz, x, y, z, rx, ry, rz, bump)
                            && Wilds.fbm(bx, bz, 3, 0x4C494348L) + Wilds.noise(by / 2.0, 0, 0x4C49L) * 0.3 > 0.05) {
                        block = c.lichen();
                    } else if (!inside(bx, by, bz - 1, x, y, z, rx, ry, rz, bump)
                            && Wilds.unit(bx, bz * 31 + by, 0x4D4F5353L) < 0.65) {
                        block = c.moss(); // the north side, out of the sun
                    }
                    level.setBlock(p, block, 2);
                    lowest = Math.min(lowest, by);
                    placed = true;
                }
                if (fill && lowest != Integer.MAX_VALUE) {
                    for (int k = 1; k <= 4; k++) {
                        p.set(bx, lowest - k, bz);
                        BlockState s = level.getBlockState(p);
                        if (!s.isAir() && !s.canBeReplaced() || !s.getFluidState().isEmpty()) {
                            break;
                        }
                        level.setBlock(p, c.rock(), 2);
                    }
                }
            }
        }
        return placed;
    }

    private static boolean inside(int bx, int by, int bz, double x, double y, double z, double rx, double ry,
            double rz, double bump) {
        double dx = (bx + 0.5 - x) / rx;
        double dy = (by + 0.5 - y) / ry;
        double dz = (bz + 0.5 - z) / rz;
        double noise = Wilds.unit(bx, bz * 7 + by, 0x42554D50L) * bump;
        return dx * dx + dy * dy + dz * dz <= 1 + noise - bump / 2;
    }

    /** What a boulder may replace: air, plants, snow, and the ground it sinks into; never water, wood or leaves. */
    private static boolean takes(BlockState s) {
        if (!s.getFluidState().isEmpty() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.hasBlockEntity()) {
            return false;
        }
        return s.isAir() || s.canBeReplaced() || s.is(BlockTags.DIRT) || s.is(Blocks.GRAVEL) || s.is(BlockTags.SAND)
                || s.is(BlockTags.BASE_STONE_OVERWORLD);
    }
}
