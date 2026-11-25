package com.porcupinestudios.ringbearer.harness.physics;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Separating-axis contact between two convex solids: if they overlap, the face normal of least overlap (pointing from
 * {@code a} to {@code b}), how deep, and a contact point between the deepest corners. Overlap is decided on both
 * solids' face normals and the crosses of their edges; the push is along a face normal only (edge crosses flip about
 * under deep contact), so sliding over a slope pushes straight off its surface, never off a step. Pure maths.
 */
public final class Sat {
    /** @param normal unit, from {@code a} toward {@code b} */
    public record Contact(Vector3d normal, double depth, Vector3d point) {}

    private Sat() {}

    @Nullable
    public static Contact test(Convex a, Convex b) {
        List<Vector3dc> axes = new ArrayList<>(a.normals());
        axes.addAll(b.normals());
        int faces = axes.size(); // pushes go along face normals; edge crosses only rule out overlaps (they flip)
        for (Vector3dc ea : a.edges()) {
            for (Vector3dc eb : b.edges()) {
                Vector3d c = ea.cross(eb, new Vector3d());
                if (c.lengthSquared() > 1e-8) {
                    axes.add(c.normalize());
                }
            }
        }
        Vector3d ca = a.centre(), cb = b.centre();
        Vector3d ab = cb.sub(ca, new Vector3d());
        double best = Double.MAX_VALUE;
        Vector3d bestAxis = null;
        for (int i = 0; i < axes.size(); i++) {
            Vector3d axis = new Vector3d(axes.get(i)).normalize();
            double[] pa = a.project(axis), pb = b.project(axis);
            double overlap = Math.min(pa[1] - pb[0], pb[1] - pa[0]);
            if (overlap <= 0) {
                return null; // separated: every axis counts for this
            }
            Vector3d out = axis.dot(ab) < 0 ? axis.negate(new Vector3d()) : axis; // from a toward b
            if (i >= faces || blocked(a, out) || blocked(b, out.negate(new Vector3d()))) {
                continue; // a direction this pair never pushes along
            }
            // prefer face normals on near ties (edge axes of nearly parallel edges are noisy)
            if (overlap < best - 1e-9) {
                best = overlap;
                bestAxis = new Vector3d(out);
            }
        }
        if (bestAxis == null) {
            return null;
        }
        // b's corners deepest into a (lowest along the axis) and a's deepest into b (highest): the contact lies between
        Vector3d deepB = deepest(b, bestAxis, -1), deepA = deepest(a, bestAxis, 1);
        return new Contact(bestAxis, best, deepA.add(deepB).mul(0.5));
    }

    /** Whether {@code c} never pushes along {@code dir}. */
    private static boolean blocked(Convex c, Vector3dc dir) {
        for (Vector3dc b : c.blocked()) {
            if (b.dot(dir) > 0.99) {
                return true;
            }
        }
        return false;
    }

    /** The average of the corners furthest along {@code sign * axis} (within a hair). */
    private static Vector3d deepest(Convex c, Vector3dc axis, int sign) {
        double extreme = -Double.MAX_VALUE;
        for (Vector3dc p : c.vertices()) {
            extreme = Math.max(extreme, sign * p.dot(axis));
        }
        Vector3d sum = new Vector3d();
        int n = 0;
        for (Vector3dc p : c.vertices()) {
            if (sign * p.dot(axis) >= extreme - 1e-3) {
                sum.add(p);
                n++;
            }
        }
        return sum.div(n);
    }
}
