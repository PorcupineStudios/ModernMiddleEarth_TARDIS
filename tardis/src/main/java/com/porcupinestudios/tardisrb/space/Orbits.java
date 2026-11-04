package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.gravity.GravityScale;
import com.porcupinestudios.tardisrb.gravity.gc.api.GravityChangerAPI;
import com.porcupinestudios.tardisrb.gravity.gc.util.RotationUtil;
import com.porcupinestudios.tardisrb.progression.Tuning;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Gravity and orbits over a planet (Aaron, 2026-11-04; Gravity Changer as the reference for the later tilting). Pure
 * maths plus one step function, shared by the server (everything but players) and the client (its own player, whose
 * movement Minecraft runs there).
 *
 * <p>In a planet's orbit layer down is the planet. Gravity is {@code space.gravity} of normal just above the clouds and
 * falls with the square of the distance from the planet's centre (full-size altitude, {@link Planet#altitude}). Going
 * sideways cancels some of it, as on a real orbit: at the orbit speed for your height you stop falling. Vanilla
 * gravity is switched off in space ({@code SpaceTravel.setGravity}) and vanilla air drag is undone after each tick, so
 * things keep their speed; speed is capped below the server's "moved too quickly" limit.
 */
public final class Orbits {
    /** Normal gravity, blocks a tick squared (living things). */
    public static final double G = 0.08;
    /** Vanilla's air drag on living things: sideways and up/down. */
    private static final double AIR_SIDE = 0.91;
    private static final double AIR_UP = 0.98;
    /** Other entities (items, projectiles) mostly lose 2% a tick. */
    private static final double OTHER = 0.98;

    private Orbits() {}

    /** How strongly the planet pulls at this height (blocks a tick squared). */
    public static double gravity(Planet p, double spaceY, Tuning.SpaceTuning t) {
        double r = p.radius() / (p.radius() + p.altitude(spaceY));
        return G * t.gravity() * r * r;
    }

    /** The sideways speed (blocks a tick in space) that keeps you up at this height. */
    public static double orbitSpeed(Planet p, double spaceY, Tuning.SpaceTuning t) {
        return t.motion().orbitSpeed() * Math.sqrt(p.radius() / (p.radius() + p.altitude(spaceY)));
    }

    /**
     * The pull down at this height for this velocity: gravity less what going sideways cancels. Faster than orbit
     * speed you'd rise, but never faster than gravity would pull you down (the layer is flat, so this is the cheat).
     */
    public static double down(Planet p, double spaceY, Vec3 velocity, Tuning.SpaceTuning t) {
        double g = gravity(p, spaceY, t);
        double v = orbitSpeed(p, spaceY, t);
        double sideways = velocity.x * velocity.x + velocity.z * velocity.z;
        double cancelled = v > 0 ? Math.min(2, sideways / (v * v)) : 0;
        return g * (1 - cancelled);
    }

    /** Whether this entity moves by our physics in space (not creative flyers, riders or vehicles with riders). */
    public static boolean moves(Entity e) {
        if (e instanceof Player player && (player.getAbilities().flying || player.isSpectator())) {
            return false;
        }
        return e.isAlive() && !e.isPassenger() && !e.isNoGravity();
    }

    /**
     * One tick of space physics over a planet, after vanilla's own tick: drag undone (in the air), the planet's pull
     * (world down, with orbits) and any thrust added, speed capped. {@code thrust} is -1, 0 or 1 (sneak, nothing,
     * jump).
     */
    public static void step(Entity e, Planet p, Tuning.SpaceTuning t, int thrust) {
        apply(e, t, thrust, v -> new Vec3(0, -down(p, e.getY(), v, t) * GravityScale.of(e), 0));
    }

    /** The same between the planets of a solar system: every ball and the sun pull ({@link StarSystem#gravity}). */
    public static void stepSystem(Entity e, StarSystem s, Tuning.SpaceTuning t, int thrust) {
        apply(e, t, thrust, v -> s.gravity(e.position(), t).scale(GravityScale.of(e)));
    }

    private static void apply(Entity e, Tuning.SpaceTuning t, int thrust,
            java.util.function.UnaryOperator<Vec3> pull) {
        Vec3 v = e.getDeltaMovement();
        Tuning.Motion m = t.motion();
        boolean living = e instanceof LivingEntity;
        if (m.dragFree() && !e.onGround()) {
            if (living) {
                boolean gliding = ((LivingEntity) e).isFallFlying();
                if (!gliding) {
                    v = new Vec3(v.x / AIR_SIDE, v.y / AIR_UP, v.z / AIR_SIDE);
                }
            } else {
                v = v.scale(1 / OTHER).add(0, e.getGravity(), 0);
            }
        } else if (!living) {
            v = v.add(0, e.getGravity(), 0);
        }
        // Drag and the entity's own gravity act in its frame (turned with its gravity direction); the pull is in
        // world directions; thrust pushes along the entity's own up.
        net.minecraft.core.Direction g = GravityChangerAPI.getGravityDirection(e);
        v = RotationUtil.vecPlayerToWorld(v, g);
        v = v.add(pull.apply(v)).add(RotationUtil.vecPlayerToWorld(0.0, thrust * m.thrust(), 0.0, g));
        double speed = v.length();
        if (speed > m.maxSpeed()) {
            v = v.scale(m.maxSpeed() / speed);
        }
        e.setDeltaMovement(RotationUtil.vecWorldToPlayer(v, g));
        e.fallDistance = 0;
    }
}
