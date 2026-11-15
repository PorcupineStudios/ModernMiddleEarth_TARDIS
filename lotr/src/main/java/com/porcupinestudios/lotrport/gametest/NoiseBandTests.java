package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.lotrport.world.map.NoiseTop;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The cheap terrain (2026-11-15, Aaron: fast chunk loading): the hills' noise is worked out only within a band of the
 * ground, and chunks are filled only up to the band's top (converter/world.py NOISE_BAND_*, {@link NoiseTop}).
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NoiseBandTests {
    private static final String EMPTY = "empty";
    private static final int SAMPLES = 400_000;
    private static final int COLUMNS = 3000;

    private NoiseBandTests() {}

    /**
     * The band must cover the noise's largest swing (measured, with a quarter more to spare): above the ground the
     * noise adds at most {@code up} x hilliness, below it takes at most {@code down} x hilliness against ground 5x
     * steeper; and every cell that holds the surface lies wholly inside (8 blocks, at hilliness 0: 4 x 2).
     */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void noiseBandCoversTheNoise(GameTestHelper helper) {
        MiddleEarthTests.Terrain terrain = MiddleEarthTests.middleEarth(helper);
        NormalNoise hills = terrain.random().getOrCreateNoise(
                ResourceKey.create(Registries.NOISE, LotrPort.id("terrain")));
        NormalNoise jagged = terrain.random().getOrCreateNoise(Noises.JAGGED);
        BlendedNoise blended = BlendedNoise.createUnseeded(1.0, 1.0, 400.0, 160.0, 8.0)
                .withNewRandom(RandomSource.create(777L));
        RandomSource r = RandomSource.create(1L);
        double[] max = new double[4];
        double[] min = new double[4];
        for (int i = 0; i < SAMPLES; i++) {
            double x = r.nextInt(400_000) - 200_000;
            double z = r.nextInt(400_000) - 200_000;
            int y = r.nextInt(704) - 64;
            double[] v = {
                blended.compute(new DensityFunction.SinglePointContext((int) x, y, (int) z)),
                hills.getValue(x, 0, z),
                jagged.getValue(x * 1500, 0, z * 1500),
                hills.getValue(x * 0.5, 0, z * 0.5)};
            for (int k = 0; k < 4; k++) {
                max[k] = Math.max(max[k], v[k]);
                min[k] = Math.min(min[k], v[k]);
            }
        }
        // as world.py builds it: 2 x blended x min(h, 20) + 2.5 h (quarter_negative(hills) - 0.1)
        //   + max(h - 10, 0) (2 x half_negative(jagged) + 1.2 x hills at half frequency)
        double up = 2 * max[0] + 2.5 * Math.max(0, max[1] - 0.1) + 2 * max[2] + 1.2 * max[3];
        double down = 2 * -min[0] + 2.5 * (-min[1] / 4 + 0.1) + 2 * (-min[2] / 2) + 1.2 * -min[3];
        double[] band = bandOf(helper);
        double needAbove = Math.max(1.25 * up, 4);
        double needBelow = Math.max(1.25 * down / 5, 4);
        LotrPort.LOG.info("[noiseband] blended {}..{}, hills {}..{}, jagged {}..{}, ridges {}..{}: noise up {} x "
                + "hilliness, down {}; band above {} (needs {}), below {} (needs {})", fmt(min[0]), fmt(max[0]),
                fmt(min[1]), fmt(max[1]), fmt(min[2]), fmt(max[2]), fmt(min[3]), fmt(max[3]), fmt(up), fmt(down),
                band[0], fmt(needAbove), band[1], fmt(needBelow));
        helper.assertTrue(band[0] >= needAbove, "noise band above " + band[0] + " < " + fmt(needAbove));
        helper.assertTrue(band[1] >= needBelow, "noise band below " + band[1] + " < " + fmt(needBelow));
        helper.succeed();
    }

    /**
     * Columns made with the band and the top cut against the full formula at full height, over the map and the
     * mountain ranges: their ground and water must match.
     */
    @GameTest(template = EMPTY, timeoutTicks = 2400)
    public static void cutsMatchFullFormula(GameTestHelper helper) {
        MiddleEarthTests.Terrain terrain = MiddleEarthTests.middleEarth(helper);
        RandomSource r = RandomSource.create(2L);
        int differ = 0;
        int worst = 0;
        String example = "";
        long cutNanos = 0;
        long fullNanos = 0;
        for (int i = 0; i < COLUMNS; i++) {
            // a third in the Misty Mountains, the rest anywhere on the map
            int x = i % 3 == 0 ? 47000 + r.nextInt(9000) : r.nextInt(240_000) - 100_000;
            int z = i % 3 == 0 ? -20000 + r.nextInt(48000) : r.nextInt(200_000) - 90_000;
            long t0 = System.nanoTime();
            NoiseColumn cut = terrain.generator().getBaseColumn(x, z, terrain.heights(), terrain.random());
            long t1 = System.nanoTime();
            NoiseColumn[] full = new NoiseColumn[1];
            NoiseTop.withoutCuts(() -> full[0] = terrain.generator().getBaseColumn(x, z, terrain.heights(),
                    terrain.random()));
            long t2 = System.nanoTime();
            cutNanos += t1 - t0;
            fullNanos += t2 - t1;
            int d = Math.max(Math.abs(top(cut, false) - top(full[0], false)),
                    Math.abs(top(cut, true) - top(full[0], true)));
            if (d > 0) {
                differ++;
                if (d > worst) {
                    worst = d;
                    example = x + ", " + z + ": ground " + top(cut, true) + " vs " + top(full[0], true)
                            + ", surface " + top(cut, false) + " vs " + top(full[0], false);
                }
            }
        }
        LotrPort.LOG.info("[noiseband] {} columns: {} differ (worst {} blocks{}); base column {} us with the cuts, "
                + "{} us without", COLUMNS, differ, worst, example.isEmpty() ? "" : " at " + example,
                cutNanos / 1000 / COLUMNS, fullNanos / 1000 / COLUMNS);
        helper.assertTrue(worst <= 1 && differ <= COLUMNS / 100,
                differ + " of " + COLUMNS + " columns differ from the full formula, worst " + worst + " at " + example);
        helper.succeed();
    }

    /** Highest y that is solid ({@code solid}) or not air (water too). */
    private static int top(NoiseColumn column, boolean solid) {
        for (int y = 639; y >= -64; y--) {
            BlockState s = column.getBlock(y);
            if (solid ? s.blocksMotion() : !s.isAir()) {
                return y;
            }
        }
        return -65;
    }

    private static double[] bandOf(GameTestHelper helper) {
        double[] band = MiddleEarthMap.get().noiseBand();
        helper.assertTrue(band != null, "map.json has no noise_band");
        return band;
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
