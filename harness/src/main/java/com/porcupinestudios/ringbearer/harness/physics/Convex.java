package com.porcupinestudios.ringbearer.harness.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A convex solid for contact tests: its corners, its face normals and its edge directions (what a separating-axis test
 * needs), and the directions it never pushes another solid along ({@code blocked}: a slope's faces that sit against its
 * neighbours in a ramp, so a box crossing the seam is pushed along the slope, not off a hidden corner). Boxes and slope
 * wedges; {@link #map} carries one into another frame (a vehicle's pose). Pure maths.
 */
public record Convex(List<Vector3dc> vertices, List<Vector3dc> normals, List<Vector3dc> edges,
                     List<Vector3dc> blocked) {
    public Convex(List<Vector3dc> vertices, List<Vector3dc> normals, List<Vector3dc> edges) {
        this(vertices, normals, edges, List.of());
    }

    private static Vector3d v(double x, double y, double z) {
        return new Vector3d(x, y, z);
    }

    /** An axis-aligned box. */
    public static Convex box(double x0, double y0, double z0, double x1, double y1, double z1) {
        List<Vector3dc> vs = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            vs.add(v((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1));
        }
        List<Vector3dc> axes = List.of(v(1, 0, 0), v(0, 1, 0), v(0, 0, 1));
        return new Convex(vs, axes, axes);
    }

    /** A 45-degree slope wedge: {@link #prism} rising from 0 to 1. */
    public static Convex wedge(double bx, double by, double bz, int fx, int fz, boolean top) {
        return prism(bx, by, bz, fx, fz, top, 0, 1);
    }

    /**
     * A slope filling the unit block at (bx, by, bz), its sloped face rising from height {@code h0} at the near edge to
     * {@code h1} at the far edge. {@code fx, fz}: its facing (unit, horizontal): the face climbs from the near edge
     * (against the facing) to the far edge. Right side up ({@code top} false): solid under the face. Upside down:
     * mirrored top to bottom, a full top over an underside that rises toward the near edge.
     */
    public static Convex prism(double bx, double by, double bz, int fx, int fz, boolean top, double h0, double h1) {
        // block-local frame: u along the facing (0 near .. 1 far), w across it (0 .. 1), y up (0 .. 1)
        double cx = bx + 0.5, cz = bz + 0.5;
        int wx = -fz, wz = fx;
        List<double[]> uy = new ArrayList<>();
        uy.add(new double[] {0, 0});
        uy.add(new double[] {1, 0});
        uy.add(new double[] {1, h1});
        if (h0 > 1e-9) {
            uy.add(new double[] {0, h0});
        }
        if (top) {
            uy.replaceAll(p -> new double[] {p[0], 1 - p[1]});
        }
        List<Vector3dc> vs = new ArrayList<>(8);
        for (double w : new double[] {-0.5, 0.5}) {
            for (double[] p : uy) {
                double u = p[0] - 0.5;
                vs.add(v(cx + fx * u + wx * w, by + p[1], cz + fz * u + wz * w));
            }
        }
        double rise = h1 - h0, len = Math.sqrt(1 + rise * rise);
        // outward normal of the sloped face: right side up (up - rise f), upside down -(up + rise f)
        Vector3d slope = top ? v(-fx * rise / len, -1 / len, -fz * rise / len) : v(-fx * rise / len, 1 / len, -fz * rise / len);
        List<Vector3dc> normals = List.of(v(0, 1, 0), v(fx, 0, fz), v(wx, 0, wz), slope);
        Vector3d slopeEdge = v(fx / len, (top ? -rise : rise) / len, fz / len);
        List<Vector3dc> edges = List.of(v(wx, 0, wz), v(fx, 0, fz), v(0, 1, 0), slopeEdge);
        // never push out through its back (against the next block of a ramp or a sled's deck), nor right side up
        // straight up off a corner (it has no top face: a box crossing a ramp's seam would be bumped up). Upside down it
        // keeps pushing down: a sled nose's bottom edge rests on the ground
        List<Vector3dc> blocked = new ArrayList<>(List.of(v(fx, 0, fz)));
        if (!top) {
            blocked.add(v(0, 1, 0));
        }
        if (h0 > 1e-9) {
            // a gentle slope's high half has a short front face, hidden behind its low half in a ramp or a nose:
            // pushing off it pushed sleds straight back, a wall
            blocked.add(v(-fx, 0, -fz));
        }
        return new Convex(vs, normals, edges, blocked);
    }

    /** This solid with its points mapped by {@code point} and its directions by {@code dir}. */
    public Convex map(UnaryOperator<Vector3d> point, UnaryOperator<Vector3d> dir) {
        return new Convex(vertices.stream().map(p -> (Vector3dc) point.apply(new Vector3d(p))).toList(),
                normals.stream().map(n -> (Vector3dc) dir.apply(new Vector3d(n))).toList(),
                edges.stream().map(e -> (Vector3dc) dir.apply(new Vector3d(e))).toList(),
                blocked.stream().map(e -> (Vector3dc) dir.apply(new Vector3d(e))).toList());
    }

    public Vector3d centre() {
        Vector3d c = new Vector3d();
        vertices.forEach(c::add);
        return c.div(vertices.size());
    }

    /** Lowest and highest corner along {@code axis}. */
    double[] project(Vector3dc axis) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (Vector3dc p : vertices) {
            double d = p.dot(axis);
            lo = Math.min(lo, d);
            hi = Math.max(hi, d);
        }
        return new double[] {lo, hi};
    }

    /** {min x, min y, min z, max x, max y, max z} of the corners. */
    public double[] bounds() {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
                -Double.MAX_VALUE};
        for (Vector3dc p : vertices) {
            b[0] = Math.min(b[0], p.x());
            b[1] = Math.min(b[1], p.y());
            b[2] = Math.min(b[2], p.z());
            b[3] = Math.max(b[3], p.x());
            b[4] = Math.max(b[4], p.y());
            b[5] = Math.max(b[5], p.z());
        }
        return b;
    }
}
