package com.porcupinestudios.ringbearer.harness.physics;

/**
 * The pull of one hitched animal on its vehicle, as one implicit (backward Euler) spring-damper along the traces. Pure
 * maths, no game types, so tests run without the game.
 *
 * <p>The spring is set by a natural frequency {@code omega} (rad/s) and damping ratio {@code zeta} against the vehicle's
 * effective mass at the hitch along the traces ({@code mEff}, from Sable's mass data), so a light cart and a loaded wagon
 * answer the same way: the same response time and no overshoot at {@code zeta} 1. Solving it implicitly keeps it stable
 * at any physics step, so it can't feed the substep jitter that an explicit stiff spring turns into vibration.
 */
public final class TraceCoupling {
    private TraceCoupling() {}

    /**
     * The impulse (mass x blocks/s) to put on the vehicle at the hitch, along the traces toward the animal, for one
     * step of {@code h} seconds: positive pulls, negative pushes.
     *
     * @param stretch how far the traces are longer than their rest length (blocks; negative = closer than rest)
     * @param stretchRate how fast that grows (blocks/s): the animal's velocity minus the hitch's, along the traces
     */
    public static double impulse(double stretch, double stretchRate, double mEff, double omega, double zeta, double h) {
        double w2 = omega * omega;
        double c = 2 * zeta * omega;
        // P = h (k C + (h k + c') Cdot) / (1 + h c'/m + h^2 k/m), with k = m w^2 and c' = m c
        return mEff * h * (w2 * stretch + (h * w2 + c) * stretchRate) / (1 + h * c + h * h * w2);
    }

    /**
     * {@link #impulse} limited by what the animal can do in one step: it pulls at most {@code maxPull} (force) and holds
     * back at most {@code maxPush}, scaled by {@code pushAlign} (0..1: how squarely it stands ahead of the hitch; an
     * animal off to the side can't brake the vehicle through its traces).
     */
    public static double limited(double impulse, double maxPull, double maxPush, double pushAlign, double h) {
        double hi = maxPull * h;
        double lo = -maxPush * Math.max(0, Math.min(1, pushAlign)) * h;
        return Math.max(lo, Math.min(hi, impulse));
    }
}
