package com.porcupinestudios.ringbearer.harness.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Measures how a tow feels from a recording of physics steps: too stiff (kicks, jerk), too soft (stretch, ringing after
 * a stop), vibrating (fast force wobble, pull/push chatter, pitch and roll shake), and what it costs. Pure maths.
 *
 * <p>"Moving" means the animal's phases accelerating, cruising, turning and slowing; "stopped" is after it has halted.
 */
public final class TowMetrics {
    /** Phases of a scripted run. */
    public static final int SETTLE = 0, ACCEL = 1, CRUISE = 2, TURN = 3, DECEL = 4, STOPPED = 5;

    /**
     * One physics step. Angles in degrees, rates in degrees/s; the vehicle's local axes: x right, y up, z forward.
     *
     * @param stretch traces minus rest length (blocks)
     * @param force pull along the traces (positive) or push, in force units
     * @param speed the vehicle's speed along its own forward (blocks/s)
     * @param animalSpeed the animal's horizontal speed (blocks/s)
     * @param pitchRate the vehicle's spin about its own right axis
     * @param yawRate about its own up axis
     * @param rollRate about its own forward axis
     * @param traceAngle between the vehicle's forward and the traces (in its own plane)
     * @param micros time spent in the harness code this step
     */
    public record Sample(double t, int phase, double stretch, double force, boolean saturated, double speed,
                         double animalSpeed, double pitch, double roll, double pitchRate, double yawRate,
                         double rollRate, double traceAngle, double micros) {}

    public record Result(double stretchMax, double stretchRms, double forcePeak, double forceRms, double vibration,
                         double shakeHz, double chatterPerS, double accelPeak, double animalAccelPeak, double jerkRms,
                         double wobbleRms, double wobblePeak, double yawShakeRms, double traceAngleMax,
                         double settleS, int rings, double pitchMax, double rollMax, double saturatedShare,
                         double microsMean, double microsMax, double distance) {
        public String line() {
            return String.format(Locale.ROOT, "stretch max %.2f rms %.2f | force peak %.0f rms %.0f | vibration %.3f"
                            + " shake %.1f Hz chatter %.2f/s | accel peak %.2f (animal %.2f) jerk rms %.1f | wobble rms %.2f peak %.1f"
                            + " yaw-shake %.2f deg/s | trace angle max %.0f | stop settle %.2fs rings %d | tilt pitch"
                            + " %.1f roll %.1f | saturated %.0f%% | cost %.1f us (max %.1f) | moved %.1f",
                    stretchMax, stretchRms, forcePeak, forceRms, vibration, shakeHz, chatterPerS, accelPeak, animalAccelPeak,
                    jerkRms, wobbleRms, wobblePeak, yawShakeRms, traceAngleMax, settleS, rings, pitchMax, rollMax,
                    saturatedShare * 100, microsMean, microsMax, distance);
        }
    }

    private TowMetrics() {}

    /**
     * @param samples the steps in order, at a fixed step
     * @param stepsPerTick physics substeps per game tick (accelerations are taken per tick, which is how players see it)
     * @param forceScale a force that counts as "small" for this vehicle (e.g. 2% of its weight): the floor under the
     *                   vibration ratio and the dead band for chatter
     */
    public static Result measure(List<Sample> samples, int stepsPerTick, double forceScale) {
        int n = samples.size();
        if (n < 4) {
            throw new IllegalArgumentException("too few samples: " + n);
        }
        double dt = samples.get(1).t() - samples.get(0).t();
        boolean[] moving = new boolean[n];
        for (int i = 0; i < n; i++) {
            int p = samples.get(i).phase();
            moving[i] = p >= ACCEL && p <= DECEL;
        }

        double stretchMax = 0, stretchSq = 0, forcePeak = 0, forceSq = 0, traceMax = 0, pitchMax = 0, rollMax = 0;
        double micros = 0, microsMax = 0;
        int count = 0, saturated = 0;
        for (int i = 0; i < n; i++) {
            Sample s = samples.get(i);
            micros += s.micros();
            microsMax = Math.max(microsMax, s.micros());
            if (!moving[i]) {
                continue;
            }
            count++;
            stretchMax = Math.max(stretchMax, Math.abs(s.stretch()));
            stretchSq += s.stretch() * s.stretch();
            forcePeak = Math.max(forcePeak, Math.abs(s.force()));
            forceSq += s.force() * s.force();
            traceMax = Math.max(traceMax, Math.abs(s.traceAngle()));
            pitchMax = Math.max(pitchMax, Math.abs(s.pitch()));
            rollMax = Math.max(rollMax, Math.abs(s.roll()));
            if (s.saturated()) {
                saturated++;
            }
        }
        count = Math.max(count, 1);
        double forceRms = Math.sqrt(forceSq / count);

        // vibration: what's left of the force after a centred 0.3 s average, against its size (or a small floor)
        int half = Math.max(1, (int) Math.round(0.15 / dt));
        double[] force = new double[n];
        double[] pitchRate = new double[n];
        double[] rollRate = new double[n];
        double[] yawRate = new double[n];
        for (int i = 0; i < n; i++) {
            Sample s = samples.get(i);
            force[i] = s.force();
            pitchRate[i] = s.pitchRate();
            rollRate[i] = s.rollRate();
            yawRate[i] = s.yawRate();
        }
        double[] fHigh = highPass(force, half);
        double[] pHigh = highPass(pitchRate, half);
        double[] rHigh = highPass(rollRate, half);
        double[] yHigh = highPass(yawRate, half);
        double hfSq = 0, wobbleSq = 0, wobblePeak = 0, yawSq = 0;
        for (int i = 0; i < n; i++) {
            if (!moving[i]) {
                continue;
            }
            hfSq += fHigh[i] * fHigh[i];
            double w = Math.hypot(pHigh[i], rHigh[i]);
            wobbleSq += w * w;
            wobblePeak = Math.max(wobblePeak, w);
            yawSq += yHigh[i] * yHigh[i];
        }
        double vibration = Math.sqrt(hfSq / count) / Math.max(forceRms, forceScale);
        // shake: how often the fast part of the force swings from one side to the other (beyond 2.5 x the small force),
        // as a frequency. A bump answered once is a cycle or two; vibration keeps going at several Hz.
        int crossings = 0, side = 0;
        for (int i = 0; i < n; i++) {
            if (!moving[i]) {
                continue;
            }
            int sg = fHigh[i] > 2.5 * forceScale ? 1 : fHigh[i] < -2.5 * forceScale ? -1 : 0;
            if (sg != 0 && side != 0 && sg != side) {
                crossings++;
            }
            if (sg != 0) {
                side = sg;
            }
        }
        double shakeHz = crossings / 2.0 / (count * dt);

        // chatter: pull <-> push flips beyond the dead band, per second of moving
        int flips = 0, sign = 0;
        for (int i = 0; i < n; i++) {
            if (!moving[i]) {
                continue;
            }
            int sg = force[i] > forceScale ? 1 : force[i] < -forceScale ? -1 : 0;
            if (sg != 0 && sign != 0 && sg != sign) {
                flips++;
            }
            if (sg != 0) {
                sign = sg;
            }
        }
        double chatter = flips / (count * dt);

        // accelerations per tick, from the mean speed of each tick
        List<double[]> ticks = new ArrayList<>(); // {speed, animalSpeed, moving}
        for (int i = 0; i + stepsPerTick <= n; i += stepsPerTick) {
            double v = 0, a = 0;
            boolean mv = false;
            for (int j = i; j < i + stepsPerTick; j++) {
                v += samples.get(j).speed();
                a += samples.get(j).animalSpeed();
                mv |= moving[j];
            }
            ticks.add(new double[] {v / stepsPerTick, a / stepsPerTick, mv ? 1 : 0});
        }
        double tickDt = dt * stepsPerTick;
        double accelPeak = 0, animalPeak = 0, jerkSq = 0, distance = 0;
        int jerkCount = 0;
        double lastAccel = Double.NaN;
        for (int k = 1; k < ticks.size(); k++) {
            distance += ticks.get(k)[0] * tickDt;
            if (ticks.get(k)[2] == 0) {
                lastAccel = Double.NaN;
                continue;
            }
            double acc = (ticks.get(k)[0] - ticks.get(k - 1)[0]) / tickDt;
            double animal = (ticks.get(k)[1] - ticks.get(k - 1)[1]) / tickDt;
            accelPeak = Math.max(accelPeak, Math.abs(acc));
            animalPeak = Math.max(animalPeak, Math.abs(animal));
            if (!Double.isNaN(lastAccel)) {
                double jerk = (acc - lastAccel) / tickDt;
                jerkSq += jerk * jerk;
                jerkCount++;
            }
            lastAccel = acc;
        }

        // after the stop: how long until the stretch stays within 0.05 of where it ends, and how often it swings
        int stop = -1;
        for (int i = 0; i < n; i++) {
            if (samples.get(i).phase() == STOPPED) {
                stop = i;
                break;
            }
        }
        double settle = 0;
        int rings = 0;
        if (stop >= 0) {
            double end = samples.get(n - 1).stretch();
            int last = stop;
            for (int i = stop; i < n; i++) {
                if (Math.abs(samples.get(i).stretch() - end) > 0.05) {
                    last = i;
                }
            }
            settle = (last - stop) * dt;
            // swings: turns of direction more than 0.05 blocks from the last turn (the same band as settling: smaller
            // wiggles are settled)
            double extreme = samples.get(stop).stretch();
            int dir = 0;
            for (int i = stop + 1; i < n; i++) {
                double c = samples.get(i).stretch();
                if (dir >= 0 && c < extreme - 0.05) {
                    if (dir > 0) {
                        rings++;
                    }
                    dir = -1;
                    extreme = c;
                } else if (dir <= 0 && c > extreme + 0.05) {
                    if (dir < 0) {
                        rings++;
                    }
                    dir = 1;
                    extreme = c;
                } else if ((dir > 0 && c > extreme) || (dir < 0 && c < extreme)) {
                    extreme = c;
                }
            }
        }

        return new Result(stretchMax, Math.sqrt(stretchSq / count), forcePeak, forceRms, vibration, shakeHz, chatter,
                accelPeak, animalPeak, jerkCount == 0 ? 0 : Math.sqrt(jerkSq / jerkCount),
                Math.sqrt(wobbleSq / count), wobblePeak, Math.sqrt(yawSq / count), traceMax, settle, rings, pitchMax,
                rollMax, (double) saturated / count, micros / n, microsMax, distance);
    }

    /** The signal minus its centred moving average over 2 half + 1 samples. */
    static double[] highPass(double[] x, int half) {
        int n = x.length;
        double[] prefix = new double[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + x[i];
        }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - half);
            int b = Math.min(n, i + half + 1);
            out[i] = x[i] - (prefix[b] - prefix[a]) / (b - a);
        }
        return out;
    }
}
