package com.porcupinestudios.ringbearer.harness.physics;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Where an animal's traces start, smoothed for the physics. The animal moves 20 times a second; the vehicle is stepped
 * several times per tick. Between ticks the raw point is interpolated (a straight line), so its velocity jumps at every
 * tick boundary, and a step-up or a jump moves it a whole block in one tick. Fed straight into a stiff spring, those
 * jumps are kicks that rattle the vehicle.
 *
 * <p>This is a critically damped tracker with the target's own velocity fed forward:
 * {@code a = w^2 (target - x) + 2w (targetVelocity - v)}, stepped with backward Euler (stable for any step). With the
 * velocity fed forward it doesn't lag behind an animal at a steady pace (a plain smoother lags speed x time constant:
 * almost a block at a gallop, pulling the vehicle onto the animal); it only rounds off changes. Pure maths.
 */
public final class AnchorTracker {
    private final Vector3d pos = new Vector3d();
    private final Vector3d vel = new Vector3d();
    private boolean primed;

    public void reset(Vector3dc at, Vector3dc velocity) {
        pos.set(at);
        vel.set(velocity);
        primed = true;
    }

    public boolean primed() {
        return primed;
    }

    public Vector3dc position() {
        return pos;
    }

    public Vector3dc velocity() {
        return vel;
    }

    /**
     * One step of {@code dt} seconds toward {@code target} moving at {@code targetVelocity}. {@code omega} (rad/s) sets
     * how quickly it follows; 0 or less follows exactly. A target more than {@code snap} blocks away is jumped to (a
     * teleport, not a step).
     */
    public void step(Vector3dc target, Vector3dc targetVelocity, double omega, double dt, double snap) {
        if (!primed || omega <= 0 || pos.distanceSquared(target) > snap * snap) {
            reset(target, targetVelocity);
            return;
        }
        double w2dt = omega * omega * dt;
        double denom = 1 + dt * w2dt + 2 * omega * dt;
        // v1 (1 + dt^2 w^2 + 2 w dt) = v + dt w^2 (xt - x) + 2 w dt vt;  x1 = x + dt v1
        vel.set(
                (vel.x + w2dt * (target.x() - pos.x) + 2 * omega * dt * targetVelocity.x()) / denom,
                (vel.y + w2dt * (target.y() - pos.y) + 2 * omega * dt * targetVelocity.y()) / denom,
                (vel.z + w2dt * (target.z() - pos.z) + 2 * omega * dt * targetVelocity.z()) / denom);
        pos.fma(dt, vel);
    }
}
