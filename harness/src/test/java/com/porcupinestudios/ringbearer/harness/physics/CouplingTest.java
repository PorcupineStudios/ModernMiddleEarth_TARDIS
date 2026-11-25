package com.porcupinestudios.ringbearer.harness.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * The coupling on a one-dimensional cart: a mass on a line with rolling drag, pulled by an anchor the test moves. No
 * game, no Sable: these check the maths (stable, no overshoot, no lag, steps rounded off); the GameTests check the real
 * thing.
 */
class CouplingTest {
    private static final double H = 0.025; // Sable's default: 2 substeps a tick

    /** Runs the cart behind an anchor whose position is {@code anchor(t)}; returns the stretch at every step. */
    private static List<double[]> run(java.util.function.DoubleUnaryOperator anchor, double seconds, double mass,
                                      double omega, double zeta, double smoothOmega, double h) {
        double x = anchor.applyAsDouble(0) - 2; // rest length 2
        double v = 0;
        AnchorTracker tracker = new AnchorTracker();
        List<double[]> out = new ArrayList<>();
        int perTick = (int) Math.round(0.05 / h);
        for (int step = 0; step * h < seconds; step++) {
            // the animal moves once a tick; physics steps interpolate between its last two positions
            int tick = step / perTick;
            double partial = (step % perTick + 1) / (double) perTick;
            double prev = anchor.applyAsDouble((tick - 1) * 0.05);
            double now = anchor.applyAsDouble(tick * 0.05);
            double target = prev + (now - prev) * partial;
            tracker.step(new Vector3d(target, 0, 0), new Vector3d((now - prev) / 0.05, 0, 0), smoothOmega, h, 4);
            double ax = tracker.position().x();
            double stretch = (ax - x) - 2;
            double rate = tracker.velocity().x() - v;
            double p = TraceCoupling.impulse(stretch, rate, mass, omega, zeta, h);
            v += p / mass;
            v -= v * 0.5 * h; // rolling drag
            x += v * h;
            out.add(new double[] {step * h, stretch, p / h, v});
        }
        return out;
    }

    @Test
    void criticallyDampedStopsWithoutRinging() {
        double omega = 2 * Math.PI * 1.2;
        // walk at 4 blocks/s for 3 s, then stop dead
        List<double[]> r = run(t -> t < 3 ? 4 * Math.max(0, t) : 12, 8, 50, omega, 1.0, 2 * Math.PI * 4, H);
        double minAfterStop = Double.MAX_VALUE, maxAfterStop = -Double.MAX_VALUE;
        for (double[] s : r) {
            if (s[0] > 3.6) {
                minAfterStop = Math.min(minAfterStop, s[1]);
                maxAfterStop = Math.max(maxAfterStop, s[1]);
            }
        }
        // after the stop the cart rolls into the slack, then settles: it must not swing back past rest
        assertTrue(maxAfterStop < 0.05, "swung back out to " + maxAfterStop);
        assertEquals(0, r.get(r.size() - 1)[1], 0.05, "didn't settle at rest");
    }

    @Test
    void stableAtAnyStep() {
        // far stiffer than any animal, at one step a tick: an explicit spring would blow up here
        List<double[]> r = run(t -> 4 * Math.max(0, t), 5, 10, 2 * Math.PI * 20, 1.0, 0, 0.05);
        for (double[] s : r) {
            assertTrue(Math.abs(s[1]) < 3 && Double.isFinite(s[2]), "unstable: " + s[1]);
        }
    }

    @Test
    void trackerDoesNotLagASteadyPace() {
        AnchorTracker t = new AnchorTracker();
        double speed = 10;
        for (int i = 0; i < 200; i++) {
            double x = speed * i * H;
            t.step(new Vector3d(x, 0, 0), new Vector3d(speed, 0, 0), 2 * Math.PI * 4, H, 4);
        }
        assertEquals(speed * 199 * H, t.position().x(), 0.01, "lags a steady 10 blocks/s");
    }

    @Test
    void trackerRoundsOffAStepUp() {
        // the animal steps up a block in one tick (the raw point jumps 1 block over 0.05 s)
        AnchorTracker t = new AnchorTracker();
        t.reset(new Vector3d(), new Vector3d());
        double maxV = 0, maxY = 0;
        for (int i = 0; i < 40; i++) {
            double time = (i + 1) * H;
            double y = time <= 0.05 ? time / 0.05 : 1;
            double vy = time <= 0.05 ? 20 : 0;
            t.step(new Vector3d(0, y, 0), new Vector3d(0, vy, 0), 2 * Math.PI * 4, H, 4);
            maxV = Math.max(maxV, t.velocity().y());
            maxY = Math.max(maxY, t.position().y());
        }
        assertTrue(maxV < 20, "passed the step's full 20 blocks/s through: " + maxV);
        assertTrue(maxY < 1.25, "overshot the step: " + maxY);
        assertEquals(1, t.position().y(), 0.02);
    }

    @Test
    void pullAndPushAreLimited() {
        assertEquals(0.05 * 100, TraceCoupling.limited(1e9, 100, 50, 1, 0.05), 1e-9);
        assertEquals(-0.05 * 50, TraceCoupling.limited(-1e9, 100, 50, 1, 0.05), 1e-9);
        assertEquals(0, TraceCoupling.limited(-1e9, 100, 50, -0.5, 0.05), 1e-9, "pushing from beside the hitch");
    }

    @Test
    void metricsSeeVibration() {
        List<TowMetrics.Sample> calm = new ArrayList<>();
        List<TowMetrics.Sample> shaky = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            double t = i * H;
            double f = 100;
            double shake = 40 * Math.sin(2 * Math.PI * 8 * t);
            calm.add(new TowMetrics.Sample(t, TowMetrics.CRUISE, 0.1, f, false, 4, 4, 0, 0, 0, 0, 0, 0, 1));
            shaky.add(new TowMetrics.Sample(t, TowMetrics.CRUISE, 0.1, f + shake, false, 4, 4, 0, 0, shake, 0, 0, 0,
                    1));
        }
        TowMetrics.Result a = TowMetrics.measure(calm, 2, 5);
        TowMetrics.Result b = TowMetrics.measure(shaky, 2, 5);
        assertTrue(a.vibration() < 0.01, a.line());
        assertTrue(b.vibration() > 0.2, b.line());
        assertTrue(b.wobbleRms() > 10, b.line());
    }
}
