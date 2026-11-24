package com.porcupinestudios.ringbearer.harness.physics;

/**
 * Where a wheel's centre must be to rest on the ground under it: a circle of radius {@code r} rolling along a line,
 * over the tops of the solids it crosses. Each top along the line is a segment, flat (a block, a slab) or sloped (a
 * slope block); the circle touches a segment at the point that pushes it highest, so passing a block edge lifts the
 * wheel along an arc, never in a jump. Pure maths.
 */
public final class WheelEnvelope {
    private WheelEnvelope() {}

    /**
     * {@link #over} for an ellipse {@code reach} long (half, along the line) and {@code height} high (half): scaling the
     * line by height / reach makes it a circle of radius {@code height}. A long, low probe starts a climb further
     * ahead and spreads it: over a 1-block step a circle of radius 1.6 still rises at up to 2.4 per block, an ellipse
     * reaching 3 and 1.5 high at under 1.5.
     */
    public static double overEllipse(double c, double reach, double height, double s0, double s1, double h0,
                                     double h1) {
        double k = height / reach;
        return over(c * k, height, s0 * k, s1 * k, h0, h1);
    }

    /**
     * The lowest centre height (same units as the heights) for a circle at {@code c} along the line that clears the
     * segment from {@code s0} to {@code s1} whose top is {@code h0} at s0 and {@code h1} at s1. Minus infinity if the
     * segment is out of reach.
     */
    public static double over(double c, double r, double s0, double s1, double h0, double h1) {
        if (s1 < s0) {
            double t = s0; s0 = s1; s1 = t;
            t = h0; h0 = h1; h1 = t;
        }
        if (s1 < c - r || s0 > c + r) {
            return Double.NEGATIVE_INFINITY;
        }
        double b = s1 - s0 < 1e-9 ? 0 : (h1 - h0) / (s1 - s0);
        // h(s) + sqrt(r^2 - (s - c)^2) is highest where (s - c) = b r / sqrt(1 + b^2)
        double s = c + b * r / Math.sqrt(1 + b * b);
        s = Math.max(Math.max(s0, c - r), Math.min(Math.min(s1, c + r), s));
        double h = h0 + b * (s - s0);
        double d = s - c;
        return h + Math.sqrt(Math.max(0, r * r - d * d));
    }
}
