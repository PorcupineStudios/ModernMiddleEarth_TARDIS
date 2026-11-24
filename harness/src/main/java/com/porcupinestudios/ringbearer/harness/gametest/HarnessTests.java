package com.porcupinestudios.ringbearer.harness.gametest;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.dev.TestWagon.Kind;
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
    public static final double TROT = 4, CANTER = 8, GENTLE = 20, TIGHT = 45;

    private HarnessTests() {}

    /** The required runs, also played by the dev movie recorder ({@code dev/Movies}). */
    public static final List<Scenario> SCENARIOS = List.of(
            new Scenario("flat_straight", Surface.FLAT, TROT, 0, 3),
            new Scenario("flat_gentle_turn", Surface.FLAT, TROT, GENTLE, 4),
            new Scenario("flat_tight_turn", Surface.FLAT, TROT, TIGHT, 4),
            new Scenario("flat_canter", Surface.FLAT, CANTER, 0, 1.5),
            new Scenario("uphill_straight", Surface.UPHILL, TROT, 0, 3),
            new Scenario("uphill_gentle_turn", Surface.UPHILL, TROT, GENTLE, 4),
            new Scenario("uphill_tight_turn", Surface.UPHILL, TROT, TIGHT, 4),
            new Scenario("slab_road_uphill", Surface.UPHILL_SLABS, TROT, 0, 3),
            new Scenario("downhill_straight", Surface.DOWNHILL, TROT, 0, 3),
            new Scenario("sidehill_straight", Surface.SIDEHILL, TROT, 0, 3),
            new Scenario("sidehill_gentle_turn", Surface.SIDEHILL, TROT, GENTLE, 4),
            new Scenario("sidehill_tight_turn", Surface.SIDEHILL, TROT, TIGHT, 4),
            // wagon wheels (smooth terrain contact), slope-block ramps, and sleds
            new Scenario("wagon_flat", Surface.FLAT, TROT, 0, 3).on(Kind.WAGON_WHEELS),
            new Scenario("wagon_tight_turn", Surface.FLAT, TROT, TIGHT, 4).on(Kind.WAGON_WHEELS),
            new Scenario("wagon_uphill", Surface.UPHILL, TROT, 0, 3).on(Kind.WAGON_WHEELS),
            new Scenario("wagon_slope_ramp", Surface.UPHILL_SLOPES, TROT, 0, 3).on(Kind.WAGON_WHEELS),
            new Scenario("wagon_downhill", Surface.DOWNHILL, TROT, 0, 3).on(Kind.WAGON_WHEELS),
            new Scenario("wagon_sidehill_turn", Surface.SIDEHILL, TROT, GENTLE, 4).on(Kind.WAGON_WHEELS),
            new Scenario("offroad_slope_ramp", Surface.UPHILL_SLOPES, TROT, 0, 3),
            new Scenario("sled_flat", Surface.FLAT, TROT, 0, 3).on(Kind.SLED),
            new Scenario("sled_ramp", Surface.STEP_SLOPE, TROT, 0, 3).on(Kind.SLED),
            new Scenario("wagon_gentle_ramp", Surface.UPHILL_GENTLE, TROT, 0, 3).on(Kind.WAGON_WHEELS),
            new Scenario("wagon_gentle_step_once", Surface.STEP_GENTLE, TROT, 0, 3).on(Kind.WAGON_WHEELS));

    /**
     * Reported, not required: runs at the edge of one horse's strength; sleds on the every-4-blocks staircase (a 5-block sled always bridges two steps, rocking
     * over each edge into the next), and the 45-degree nose against a bare step.
     */
    public static final List<Scenario> HARD = List.of(
            // 27 iron blocks, beyond the horse: whether the heavy stop swings once or a few times varies by run
            new Scenario("overloaded_uphill", Surface.UPHILL, TROT, 0, 3, null, 27),
            // a 15-block sled up one block on a gentle nose or ramp: smooth climbs, but right at one horse's pull, so
            // whether it reaches its slack and is held back varies by run
            new Scenario("sled_gentle_step", Surface.STEP, TROT, 0, 3).on(Kind.SLED_GENTLE),
            new Scenario("sled_gentle_ramp_once", Surface.STEP_GENTLE, TROT, 0, 3).on(Kind.SLED_GENTLE),
            // a 45-degree nose against a bare step: lifting this sled straight up 45 degrees plus its sliding friction
            // needs more pull than one horse has (the gentle nose gets up: sled_gentle_step)
            new Scenario("sled_step", Surface.STEP, TROT, 0, 3).on(Kind.SLED),
            new Scenario("sled_slope_ramp", Surface.UPHILL_SLOPES, TROT, 0, 3).on(Kind.SLED),
            new Scenario("sled_uphill_steps", Surface.UPHILL, TROT, 0, 3).on(Kind.SLED),
            new Scenario("sled_gentle_ramp", Surface.UPHILL_GENTLE, TROT, 0, 3).on(Kind.SLED_GENTLE),
            new Scenario("sled_gentle_steps", Surface.UPHILL, TROT, 0, 3).on(Kind.SLED_GENTLE));

    public static Scenario scenario(String name) {
        return java.util.stream.Stream.concat(SCENARIOS.stream(), HARD.stream()).filter(s -> s.name().equals(name))
                .findFirst().orElseThrow();
    }


    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void flatStraight(GameTestHelper h) {
        TowRun.start(h, scenario("flat_straight"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void flatGentleTurn(GameTestHelper h) {
        TowRun.start(h, scenario("flat_gentle_turn"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void flatTightTurn(GameTestHelper h) {
        TowRun.start(h, scenario("flat_tight_turn"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void flatCanter(GameTestHelper h) {
        TowRun.start(h, scenario("flat_canter"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void uphillStraight(GameTestHelper h) {
        TowRun.start(h, scenario("uphill_straight"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void uphillGentleTurn(GameTestHelper h) {
        TowRun.start(h, scenario("uphill_gentle_turn"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void uphillTightTurn(GameTestHelper h) {
        TowRun.start(h, scenario("uphill_tight_turn"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void slabRoadUphill(GameTestHelper h) {
        TowRun.start(h, scenario("slab_road_uphill"));
    }


    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void downhillStraight(GameTestHelper h) {
        TowRun.start(h, scenario("downhill_straight"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void sidehillStraight(GameTestHelper h) {
        TowRun.start(h, scenario("sidehill_straight"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void sidehillGentleTurn(GameTestHelper h) {
        TowRun.start(h, scenario("sidehill_gentle_turn"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void sidehillTightTurn(GameTestHelper h) {
        TowRun.start(h, scenario("sidehill_tight_turn"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wagonFlat(GameTestHelper h) {
        TowRun.start(h, scenario("wagon_flat"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wagonTightTurn(GameTestHelper h) {
        TowRun.start(h, scenario("wagon_tight_turn"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wagonUphill(GameTestHelper h) {
        TowRun.start(h, scenario("wagon_uphill"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wagonSlopeRamp(GameTestHelper h) {
        TowRun.start(h, scenario("wagon_slope_ramp"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wagonDownhill(GameTestHelper h) {
        TowRun.start(h, scenario("wagon_downhill"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wagonSidehillTurn(GameTestHelper h) {
        TowRun.start(h, scenario("wagon_sidehill_turn"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void offroadSlopeRamp(GameTestHelper h) {
        TowRun.start(h, scenario("offroad_slope_ramp"));
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void sledFlat(GameTestHelper h) {
        TowRun.start(h, scenario("sled_flat"));
    }



    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wagonGentleRamp(GameTestHelper h) {
        TowRun.start(h, scenario("wagon_gentle_ramp"));
    }





    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void sledRamp(GameTestHelper h) {
        TowRun.start(h, scenario("sled_ramp"));
    }


    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void wagonGentleStepOnce(GameTestHelper h) {
        TowRun.start(h, scenario("wagon_gentle_step_once"));
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

    /** The sleds-on-a-staircase runs: always generated, never required (they report how rough it is). */
    @GameTestGenerator
    public static Collection<TestFunction> hard() {
        List<TestFunction> out = new ArrayList<>();
        for (Scenario sc : HARD) {
            sweep(out, sc.name(), new Scenario(sc.name(), sc.surface(), sc.speed(), sc.yawRate(), sc.turnSeconds(),
                    sc.traces(), sc.cargo(), sc.kind()));
        }
        return out;
    }

    private static void sweep(List<TestFunction> out, String name, Scenario sc) {
        out.add(new TestFunction("defaultBatch", name, HarnessMod.MOD_ID + ":" + ARENA, TIMEOUT, 0L, false,
                h -> TowRun.start(h, sc)));
    }
}
