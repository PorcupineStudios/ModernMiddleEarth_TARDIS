package com.porcupinestudios.tardisrb.compat.sable;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Pure maths for turning a ship's up toward a wanted direction: a damped spring about the axis {@code up x want}. No
 * Sable types here, so tests run without it.
 */
public final class ShipAlign {
    private ShipAlign() {}

    /**
     * The angular velocity to add this step (rad/s, world axes): {@code (strength * angle - damping * w) * dt} about
     * the turn axis, where w is the current spin about that axis. Spin about other axes (yaw round {@code want}) is
     * left alone.
     */
    public static Vector3d deltaOmega(Vector3dc up, Vector3dc want, Vector3dc omega, double strength, double damping,
                                      double dt) {
        Vector3d axis = up.cross(want, new Vector3d());
        double sin = axis.length();
        double angle = Math.atan2(sin, up.dot(want));
        if (sin < 1e-9) {
            if (angle < 1e-6) {
                return new Vector3d(); // aligned; nothing to turn about
            }
            axis = up.cross(Math.abs(up.x()) < 0.9 ? new Vector3d(1, 0, 0) : new Vector3d(0, 0, 1), new Vector3d());
        }
        axis.normalize();
        double w = omega.dot(axis);
        return axis.mul((strength * angle - damping * w) * dt);
    }
}
