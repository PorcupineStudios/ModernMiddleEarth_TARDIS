package com.porcupinestudios.ringbearer.harness.physics;

import org.joml.Vector3d;

/**
 * The hold-back's rule, pure maths shared by the server (animals it moves) and a rider's client (a ridden animal is
 * moved by its rider's client, which the server can't correct): an animal whose tow point got further than rest + slack
 * from the hitch (horizontally), closer than rest - push slack, or further round than the pole's max angle from straight
 * ahead, is moved back onto those limits: half the overshoot a tick, that correction changing by at most
 * {@link #MAX_CHANGE} a tick (a one-tick snap read as a yank); past {@link #HARD} blocks over, all at once.
 */
public final class Restraint {
    public static final double GAIN = 0.5, HARD = 0.5, MAX_CHANGE = 0.02;

    private Restraint() {}

    /**
     * How far to move the animal back this tick (world, horizontal; subtract it), easing from {@code last} (the last
     * tick's correction, updated in place); zero when it's within its limits.
     *
     * @param forward the hitch's forward, world, horizontal
     */
    public static Vector3d correction(Vector3d hitch, Vector3d forward, Vector3d tow, double rest, double slack,
                                      double pushSlack, double maxAngleDeg, Vector3d last) {
        Vector3d dw = new Vector3d(tow).sub(hitch);
        dw.y = 0;
        double dist = dw.length();
        if (dist < 1e-6) {
            last.zero();
            return new Vector3d();
        }
        double ahead = Math.atan2(forward.z, forward.x);
        double angle = wrapDegrees(Math.toDegrees(Math.atan2(dw.z, dw.x) - ahead));
        double maxAngle = Math.max(0, Math.min(180, maxAngleDeg));
        double keep = Math.max(Math.max(0.05, rest - pushSlack), Math.min(dist, rest + slack));
        if (keep == dist && Math.abs(angle) <= maxAngle) {
            last.zero();
            return new Vector3d();
        }
        double a = ahead + Math.toRadians(Math.max(-maxAngle, Math.min(maxAngle, angle)));
        Vector3d back = dw.sub(Math.cos(a) * keep, 0, Math.sin(a) * keep, new Vector3d());
        double over = back.length();
        if (over > HARD) {
            back.mul((over - HARD * GAIN) / over);
        } else {
            back.mul(GAIN);
            Vector3d change = back.sub(last, new Vector3d());
            if (change.length() > MAX_CHANGE) {
                change.mul(MAX_CHANGE / change.length());
            }
            back.set(last).add(change);
        }
        last.set(back);
        return back;
    }

    static double wrapDegrees(double d) {
        d %= 360;
        if (d >= 180) {
            d -= 360;
        }
        if (d < -180) {
            d += 360;
        }
        return d;
    }
}
