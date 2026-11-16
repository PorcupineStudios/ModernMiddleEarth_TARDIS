package com.porcupinestudios.lotrport.rig;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Inverse kinematics for two-bone limbs (hip, knee, foot). Pure maths, no game classes; unit-tested. */
public final class Ik {
    private static final float EPS = 1e-4f;

    private Ik() {}

    /** The answer for one limb: where the knee goes and how each bone turns from its rest pose. */
    public static final class Result {
        public final Vector3f knee = new Vector3f();
        /** Where the foot ends up (the target, or as close as the limb reaches). */
        public final Vector3f foot = new Vector3f();
        public final Quaternionf upper = new Quaternionf();
        public final Quaternionf lower = new Quaternionf();
    }

    /**
     * Solves a two-bone limb.
     *
     * @param hip the hip now
     * @param target where the foot should go
     * @param l1 hip to knee length
     * @param l2 knee to foot length
     * @param pole which way the knee should point now (any length; the part along hip-target is ignored)
     * @param restHip the hip at rest
     * @param restKnee the knee at rest
     * @param restFoot the foot at rest
     * @param restPole which way the knee pointed at rest (used only when the rest leg is straight)
     * @param out the knee, the reached foot, and the rotations taking each bone from its rest direction to its new one
     *        (rotate the bone's rest orientation by these)
     */
    public static Result solve(Vector3f hip, Vector3f target, float l1, float l2, Vector3f pole, Vector3f restHip,
            Vector3f restKnee, Vector3f restFoot, Vector3f restPole, Result out) {
        Vector3f d = new Vector3f(target).sub(hip);
        float dist = d.length();
        Vector3f u = dist < EPS ? new Vector3f(0, -1, 0) : new Vector3f(d).div(dist);
        float lo = Math.abs(l1 - l2) + 1e-3f;
        float hi = l1 + l2 - 1e-3f;
        dist = Math.max(lo, Math.min(hi, dist));
        Vector3f v = new Vector3f(pole).sub(new Vector3f(u).mul(pole.dot(u)));
        if (v.lengthSquared() < EPS * EPS) {
            v = anyPerpendicular(u);
        }
        v.normalize();
        float a = (l1 * l1 - l2 * l2 + dist * dist) / (2 * dist);
        float h = (float) Math.sqrt(Math.max(0, l1 * l1 - a * a));
        out.knee.set(hip).add(new Vector3f(u).mul(a)).add(new Vector3f(v).mul(h));
        out.foot.set(hip).add(new Vector3f(u).mul(dist));

        Vector3f d1 = new Vector3f(out.knee).sub(hip);
        Vector3f d2 = new Vector3f(out.foot).sub(out.knee);
        Vector3f n = bendNormal(d1, d2, v);
        Vector3f r1 = new Vector3f(restKnee).sub(restHip);
        Vector3f r2 = new Vector3f(restFoot).sub(restKnee);
        Vector3f rn = bendNormal(r1, r2, restPole);
        out.upper.set(turn(r1, rn, d1, n));
        out.lower.set(turn(r2, rn, d2, n));
        return out;
    }

    /** The bend plane's normal, d1 x d2; for a straight limb, d1 x pole. */
    static Vector3f bendNormal(Vector3f d1, Vector3f d2, Vector3f pole) {
        Vector3f n = new Vector3f(d1).cross(d2);
        if (n.lengthSquared() < 1e-8f * d1.lengthSquared() * Math.max(d2.lengthSquared(), 1e-8f)) {
            n = new Vector3f(d1).cross(pole);
            if (n.lengthSquared() < 1e-10f) {
                n = anyPerpendicular(new Vector3f(d1).normalize());
            }
        }
        return n.normalize();
    }

    /** The rotation taking direction {@code from} with side {@code fromN} onto {@code to} with side {@code toN}. */
    static Quaternionf turn(Vector3f from, Vector3f fromN, Vector3f to, Vector3f toN) {
        Matrix3f a = frame(from, fromN);
        Matrix3f b = frame(to, toN);
        Matrix3f m = b.mul(a.transpose());
        return new Quaternionf().setFromNormalized(m).normalize();
    }

    private static Matrix3f frame(Vector3f dir, Vector3f n) {
        Vector3f x = new Vector3f(dir).normalize();
        Vector3f y = new Vector3f(n).sub(new Vector3f(x).mul(n.dot(x))).normalize();
        Vector3f z = new Vector3f(x).cross(y);
        return new Matrix3f(x, y, z);
    }

    static Vector3f anyPerpendicular(Vector3f u) {
        Vector3f axis = Math.abs(u.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        return new Vector3f(axis).sub(new Vector3f(u).mul(axis.dot(u))).normalize();
    }
}
