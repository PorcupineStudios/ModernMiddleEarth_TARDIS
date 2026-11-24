package com.porcupinestudios.ringbearer.harness.gametest;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.gametest.TowRun.Scenario;
import com.porcupinestudios.ringbearer.harness.gametest.TowRun.Surface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A horse pulling a real Aeronautics wagon (Sable physics, Offroad wheels) on each surface, straight and turning at
 * two rates, plus a canter and a downhill hold-back. Each logs its measurements ({@code [harness-metrics]}) and writes
 * them to {@code harness/run/harness-metrics/}. {@code -Dringbearer_harness.metricsOnly=true} reports without failing.
 */
@GameTestHolder(HarnessMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HarnessTests {
    private static final String ARENA = "arena";
    private static final int TIMEOUT = 700;
    private static final double TROT = 4, CANTER = 8, GENTLE = 20, TIGHT = 45;

    private HarnessTests() {}

    private static void run(GameTestHelper h, String name, Surface s, double speed, double yawRate, double seconds) {
        TowRun.start(h, new Scenario(name, s, speed, yawRate, seconds));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void flatStraight(GameTestHelper h) {
        run(h, "flat_straight", Surface.FLAT, TROT, 0, 3);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void flatGentleTurn(GameTestHelper h) {
        run(h, "flat_gentle_turn", Surface.FLAT, TROT, GENTLE, 4);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void flatTightTurn(GameTestHelper h) {
        run(h, "flat_tight_turn", Surface.FLAT, TROT, TIGHT, 4);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void flatCanter(GameTestHelper h) {
        run(h, "flat_canter", Surface.FLAT, CANTER, 0, 1.5);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void uphillStraight(GameTestHelper h) {
        run(h, "uphill_straight", Surface.UPHILL, TROT, 0, 3);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void uphillGentleTurn(GameTestHelper h) {
        run(h, "uphill_gentle_turn", Surface.UPHILL, TROT, GENTLE, 4);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void uphillTightTurn(GameTestHelper h) {
        run(h, "uphill_tight_turn", Surface.UPHILL, TROT, TIGHT, 4);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void slabRoadUphill(GameTestHelper h) {
        run(h, "slab_road_uphill", Surface.UPHILL_SLABS, TROT, 0, 3);
    }

    /** 27 iron blocks on the deck: the horse pulls at its limit up the hill and is held back by its traces. */
    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void overloadedUphill(GameTestHelper h) {
        TowRun.start(h, new Scenario("overloaded_uphill", Surface.UPHILL, TROT, 0, 3, null, 27));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void downhillStraight(GameTestHelper h) {
        run(h, "downhill_straight", Surface.DOWNHILL, TROT, 0, 3);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void sidehillStraight(GameTestHelper h) {
        run(h, "sidehill_straight", Surface.SIDEHILL, TROT, 0, 3);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void sidehillGentleTurn(GameTestHelper h) {
        run(h, "sidehill_gentle_turn", Surface.SIDEHILL, TROT, GENTLE, 4);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void sidehillTightTurn(GameTestHelper h) {
        run(h, "sidehill_tight_turn", Surface.SIDEHILL, TROT, TIGHT, 4);
    }

    // --- calibration sweeps (-Dringbearer_harness.sweeps=true): a grid of trace settings on the runs that tell them
    // apart; they only report, in summary.txt -------------------------------------------------------------------------

    private static final double[] SWEEP_HZ = {0.8, 1.0, 1.2};
    private static final double[] SWEEP_DAMPING = {0.5, 0.7, 1.0};

    @GameTestGenerator
    public static Collection<TestFunction> sweeps() {
        List<TestFunction> out = new ArrayList<>();
        if (!Boolean.getBoolean("ringbearer_harness.sweeps")) {
            return out;
        }
        for (double hz : SWEEP_HZ) {
            for (double zeta : SWEEP_DAMPING) {
                String tag = String.format(Locale.ROOT, "_f%02d_d%02d", Math.round(hz * 10), Math.round(zeta * 10));
                double[] traces = {hz, zeta, 4};
                sweep(out, "sweep_uphill" + tag, new Scenario("sweep_uphill" + tag, Surface.UPHILL, TROT, 0, 3, traces));
                sweep(out, "sweep_canter" + tag, new Scenario("sweep_canter" + tag, Surface.FLAT, CANTER, 0, 1.5,
                        traces));
                sweep(out, "sweep_tight" + tag, new Scenario("sweep_tight" + tag, Surface.FLAT, TROT, TIGHT, 4,
                        traces));
                sweep(out, "sweep_downhill" + tag, new Scenario("sweep_downhill" + tag, Surface.DOWNHILL, TROT, 0, 3,
                        traces));
            }
        }
        return out;
    }

    private static void sweep(List<TestFunction> out, String name, Scenario sc) {
        out.add(new TestFunction("defaultBatch", name, HarnessMod.MOD_ID + ":" + ARENA, TIMEOUT, 0L, false,
                h -> TowRun.start(h, sc)));
    }
}
