package com.porcupinestudios.ringbearer.harness.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Slope wedges, the separating-axis contact and the wheel envelope. */
class GeometryTest {
    @Test
    void boxSlidingOntoASlopeIsPushedOffTheSlopeFace() {
        // a slope rising toward +z in the block at the origin; a box sitting a little into its middle
        Convex slope = Convex.wedge(0, 0, 0, 0, 1, false);
        Convex box = Convex.box(0.2, 0.45, 0.4, 0.8, 1.45, 0.6);
        Sat.Contact c = Sat.test(slope, box);
        assertNotNull(c);
        double s = Math.sqrt(0.5);
        assertEquals(0, c.normal().x, 1e-6);
        assertEquals(s, c.normal().y, 1e-6, "not pushed off the slope face: " + c.normal());
        assertEquals(-s, c.normal().z, 1e-6, "not pushed off the slope face: " + c.normal());
        assertTrue(c.depth() > 0 && c.depth() < 0.2, "depth " + c.depth());
    }

    @Test
    void nothingAboveTheSlope() {
        Convex slope = Convex.wedge(0, 0, 0, 0, 1, false);
        // above the low end: z 0..0.2 the slope is under 0.2 high
        assertNull(Sat.test(slope, Convex.box(0, 0.3, 0, 1, 1, 0.2)));
    }

    @Test
    void upsideDownSlopeRidesOverABlockEdge() {
        // a sled nose: upside-down slope facing -z (thin edge toward +z, the way it travels), its underside meeting the
        // top front edge of a block ahead of it
        Convex nose = Convex.wedge(0, 0.6, 0, 0, -1, true);
        Convex step = Convex.box(-1, -1, 0.7, 2, 1.35, 2); // underside is at 1.3 over z 0.7
        Sat.Contact c = Sat.test(step, nose);
        assertNotNull(c);
        assertTrue(c.normal().y > 0.6 && c.normal().z < -0.6, "should lift it up and back: " + c.normal());
    }

    @Test
    void wheelClimbsAStepAlongAnArcNotAJump() {
        // flat ground at 0 up to s = 0, a step of 1 from s = 0; an elliptic probe reaching 3, 1.5 high
        double r = 1.5, last = Double.NaN, maxRise = 0;
        for (double c = -5; c <= 1; c += 0.01) {
            double h = Math.max(WheelEnvelope.overEllipse(c, 3, r, -10, 0, 0, 0),
                    WheelEnvelope.overEllipse(c, 3, r, 0, 10, 1, 1));
            if (!Double.isNaN(last)) {
                maxRise = Math.max(maxRise, (h - last) / 0.01);
            }
            last = h;
        }
        assertEquals(r + 1, last, 1e-9);
        assertTrue(maxRise < 1.5, "the climb is too steep somewhere: slope " + maxRise);
    }

    @Test
    void wheelRollsSmoothlyOverASlope() {
        // a 45-degree slope from (0, 0) to (1, 1) between flat ground: the centre stays within the circle's reach
        double r = 1.0, last = Double.NaN;
        for (double c = -2; c <= 3; c += 0.01) {
            double h = Math.max(Math.max(WheelEnvelope.over(c, r, -10, 0, 0, 0), WheelEnvelope.over(c, r, 0, 1, 0, 1)),
                    WheelEnvelope.over(c, r, 1, 10, 1, 1));
            if (!Double.isNaN(last)) {
                assertTrue(Math.abs(h - last) < 0.0101, "a jump at " + c + ": " + (h - last));
            }
            last = h;
        }
    }

    @Test
    void gentleSlopeHalvesMeetFlush() {
        // low half (0 -> 0.5) in block z 0, high half (0.5 -> 1) in block z 1, both climbing +z: a box resting across
        // the seam on the face is pushed off the shared slope normal, not off either block's edge
        Convex low = Convex.prism(0, 0, 0, 0, 1, false, 0, 0.5);
        Convex high = Convex.prism(0, 0, 1, 0, 1, false, 0.5, 1);
        double n = 1 / Math.sqrt(1.25);
        for (Convex c : new Convex[] {low, high}) {
            Sat.Contact hit = Sat.test(c, Convex.box(0.2, 0.47, 0.9, 0.8, 1.47, 1.1));
            assertNotNull(hit);
            assertEquals(n, hit.normal().y, 1e-6, "not off the 27-degree face: " + hit.normal());
            assertEquals(-0.5 * n, hit.normal().z, 1e-6, "not off the 27-degree face: " + hit.normal());
        }
    }
}
