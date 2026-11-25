package com.porcupinestudios.ringbearer.harness.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** The hold-back rule the server and a rider's client share. */
class RestraintTest {
    private static final Vector3d HITCH = new Vector3d(0, 0, 0), SOUTH = new Vector3d(0, 0, 1);

    private static Vector3d back(double x, double z, Vector3d last) {
        return Restraint.correction(HITCH, SOUTH, new Vector3d(x, 1, z), 2.0, 1.0, 0.4, 60, last);
    }

    @Test
    void withinItsLimitsItIsLeftAlone() {
        assertEquals(0, back(0.3, 2.5, new Vector3d()).length(), 1e-9);
    }

    @Test
    void pastItsSlackItIsHeldBackAlongTheTraces() {
        Vector3d last = new Vector3d();
        Vector3d b = back(0, 3.2, last);
        assertTrue(b.z > 0 && Math.abs(b.x) < 1e-9, "held back toward the hitch: " + b);
        assertEquals(0.02, b.length(), 1e-9, "the first tick's correction is eased in");
        for (int i = 0; i < 20; i++) {
            b = back(0, 3.2, last);
        }
        assertEquals(0.1, b.length(), 1e-6, "builds to half the 0.2 overshoot");
    }

    @Test
    void farPastItIsTakenBackAtOnce() {
        Vector3d b = back(0, 4.0, new Vector3d());
        assertEquals(1.0 - 0.25, b.length(), 1e-9, "all but a quarter of the overshoot");
    }

    @Test
    void swungRoundPastThePoleItIsPutBackOnIt() {
        // 80 degrees round from straight ahead, at rest length: back to 60 degrees
        double a = Math.toRadians(80);
        Vector3d b = back(-Math.sin(a) * 2, Math.cos(a) * 2, new Vector3d());
        assertTrue(b.length() > 0, "not put back on the pole");
    }

    @Test
    void pushedInsideItsRestLessSlackItIsShovedOut() {
        Vector3d b = back(0, 1.0, new Vector3d());
        assertTrue(b.z < 0, "shoved away from the hitch: " + b);
    }
}
