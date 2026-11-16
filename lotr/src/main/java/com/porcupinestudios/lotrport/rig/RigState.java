package com.porcupinestudios.lotrport.rig;

import org.joml.Matrix4d;
import org.joml.Vector3d;

/**
 * One creature's moving rig: where each foot stands, which feet are mid-step, and the body's height and tilt. Ticked
 * on both sides from the same inputs (server: hitboxes and stomps; client: drawing), so both agree closely without
 * syncing feet. Pure maths over a {@link Ground} query, no game classes; unit-tested.
 */
public final class RigState {
    /** Ground height under a column: the top of the highest solid surface between {@code top} and {@code bottom}. */
    public interface Ground {
        /** NaN when there is no ground in that span. */
        double height(double x, double z, double top, double bottom);
    }

    /** Footfalls (sounds, dust, shake, stomp damage). */
    public interface Footfall {
        void landed(int leg, double x, double y, double z);
    }

    /** What the rig needs from its entity each tick (and each frame, with positions interpolated). */
    public static final class Body {
        public double x;
        public double y;
        public double z;
        /** Body yaw, Minecraft degrees. */
        public float yaw;
        /** Movement this tick, blocks. */
        public double vx;
        public double vz;
        public float scale = 1;
        /** 0 standing still, 1 walking at full stride. */
        public float walk;
        /** Head turn against the body, degrees. */
        public float headYaw;
        public float headPitch;
        public float age;
        /** How far into hiding (0 up, 1 crouched into its rock pose), eased. */
        public float hide;
        /** Off the ground (flying, falling, swimming): feet hang instead of stepping. */
        public boolean airborne;
    }

    public final class LegState {
        public final Vector3d foot = new Vector3d();
        final Vector3d from = new Vector3d();
        final Vector3d to = new Vector3d();
        public boolean stepping;
        float t;
        float dt;
        boolean placed;
    }

    public final RigSkeleton skeleton;
    public final LegState[] legs;
    public float lift;
    public float prevLift;
    public float pitch;
    public float prevPitch;
    public float roll;
    public float prevRoll;
    /** Per chain, per bone: yaw bend (radians), this tick and the last. */
    public final float[][] chainYaw;
    public final float[][] prevChainYaw;
    public float walk;
    public float prevWalk;
    private float lastYaw = Float.NaN;
    private int lastGroup = -1;
    private int stillTicks;

    public RigState(RigSkeleton skeleton) {
        this.skeleton = skeleton;
        legs = new LegState[skeleton.legs.length];
        for (int i = 0; i < legs.length; i++) {
            legs[i] = new LegState();
        }
        chainYaw = new float[skeleton.chains.length][];
        prevChainYaw = new float[skeleton.chains.length][];
        for (int i = 0; i < chainYaw.length; i++) {
            chainYaw[i] = new float[skeleton.chains[i].bones().length];
            prevChainYaw[i] = new float[chainYaw[i].length];
        }
    }

    /** Rig space to world: translate to the entity, turn to its yaw, scale. */
    public static Matrix4d toWorld(Body b) {
        return new Matrix4d().translation(b.x, b.y, b.z).rotateY(Math.toRadians(-b.yaw)).scale(b.scale);
    }

    public void tick(Body body, Ground ground, Footfall footfall) {
        RigDef.Gait g = skeleton.def.gait();
        float s = body.scale;
        prevLift = lift;
        prevPitch = pitch;
        prevRoll = roll;
        prevWalk = walk;
        for (int c = 0; c < chainYaw.length; c++) {
            System.arraycopy(chainYaw[c], 0, prevChainYaw[c], 0, chainYaw[c].length);
        }
        walk += (body.walk - walk) * 0.3f;
        stillTicks = body.walk < 0.05f ? stillTicks + 1 : 0;

        Matrix4d world = toWorld(body);
        Vector3d[] targets = new Vector3d[legs.length];
        double stepDist = g.stepDistance() * s;
        for (int i = 0; i < legs.length; i++) {
            RigSkeleton.Leg leg = skeleton.legs[i];
            Vector3d home = world.transformPosition(new Vector3d(leg.home().x, 0, leg.home().z));
            home.x += body.vx * g.lead();
            home.z += body.vz * g.lead();
            double top = body.y + (leg.hip().y + g.reachUp()) * s;
            double bottom = body.y - g.reachDown() * s;
            double h = body.airborne ? Double.NaN : ground.height(home.x, home.z, top, bottom);
            home.y = Double.isNaN(h) ? body.y : h;
            targets[i] = home;
        }

        for (int i = 0; i < legs.length; i++) {
            LegState l = legs[i];
            Vector3d t = targets[i];
            if (!l.placed || horizontal(l.foot, t) > stepDist * 6 || Math.abs(l.foot.y - t.y) > stepDist * 6
                    || body.airborne) {
                l.foot.set(t);
                l.stepping = false;
                l.placed = true;
            }
        }
        if (body.airborne) {
            settleBody(body, world, g);
            chains(body);
            return;
        }

        // feet mid-step move on; landing ones plant
        int active = -1;
        float least = 1;
        for (int i = 0; i < legs.length; i++) {
            LegState l = legs[i];
            if (!l.stepping) {
                continue;
            }
            l.to.set(targets[i]);
            l.t += l.dt;
            if (l.t >= 1) {
                l.stepping = false;
                l.foot.set(l.to);
                if (footfall != null) {
                    footfall.landed(i, l.foot.x, l.foot.y, l.foot.z);
                }
            } else {
                l.foot.set(position(l, 0, g.stepHeight() * s));
                active = skeleton.legs[i].group();
                least = Math.min(least, l.t);
            }
        }

        // which group steps next: the one whose feet lag most, taking turns; with overlap, before the last one lands
        double speed = Math.sqrt(body.vx * body.vx + body.vz * body.vz);
        if (active < 0 || least >= g.overlap()) {
            double best = 0;
            int bestGroup = -1;
            for (int i = 0; i < legs.length; i++) {
                double need = need(i, targets[i], stepDist);
                int grp = skeleton.legs[i].group();
                if (grp == active) {
                    continue;
                }
                double score = need + (grp == lastGroup ? -0.25 : 0);
                if (need > 1 && (bestGroup < 0 || score > best)) {
                    best = score;
                    bestGroup = grp;
                }
            }
            if (bestGroup >= 0) {
                lastGroup = bestGroup;
                for (int i = 0; i < legs.length; i++) {
                    if (skeleton.legs[i].group() == bestGroup && need(i, targets[i], stepDist) > 0.35) {
                        start(legs[i], targets[i], stepDist, speed, g);
                    }
                }
            }
        }
        settleBody(body, world, g);
        chains(body);
    }

    /** How overdue a foot is for a step: distance to its spot over the step distance (lower bar while standing). */
    private double need(int i, Vector3d target, double stepDist) {
        LegState l = legs[i];
        if (l.stepping) {
            return 0;
        }
        double d = Math.max(horizontal(l.foot, target), Math.abs(l.foot.y - target.y) * 0.75);
        double bar = stillTicks > 10 ? stepDist * 0.25 : stepDist;
        return d / bar;
    }

    private void start(LegState l, Vector3d target, double stepDist, double speed, RigDef.Gait g) {
        l.from.set(l.foot);
        l.to.set(target);
        double d = horizontal(l.foot, target);
        // long strides take a little longer, short shuffles are quick; moving fast, every step is quicker so the
        // feet keep up (a foot may be off the ground while the body covers at most half a step)
        double ticks = g.stepTicks() * Math.min(1.5, Math.max(0.5, d / stepDist));
        if (speed > 1e-3) {
            ticks = Math.min(ticks, 0.5 * stepDist / speed);
        }
        ticks = Math.max(2, ticks);
        l.dt = (float) (1.0 / ticks);
        l.t = 0;
        l.stepping = true;
    }

    private static double horizontal(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A stepping foot part way: eased along, lifted in an arc (higher when climbing). */
    private static Vector3d position(LegState l, float extra, double height) {
        float t = Math.min(1, l.t + extra);
        double e = t * t * (3 - 2 * t);
        Vector3d p = new Vector3d(l.from).lerp(l.to, e);
        double climb = Math.max(0, l.to.y - l.from.y);
        p.y += Math.sin(Math.PI * t) * (height + climb * 0.5);
        return p;
    }

    /** Where a foot is drawn this frame. */
    public Vector3d foot(int i, float partial, float scale) {
        LegState l = legs[i];
        if (!l.stepping) {
            return new Vector3d(l.foot);
        }
        return position(l, l.dt * partial, skeleton.def.gait().stepHeight() * scale);
    }

    /** Body height and tilt follow the planted feet, on a spring. */
    private void settleBody(Body body, Matrix4d world, RigDef.Gait g) {
        int n = legs.length;
        if (n == 0) {
            return;
        }
        Matrix4d inv = new Matrix4d(world).invert();
        float sum = 0;
        float front = 0;
        float back = 0;
        float left = 0;
        float right = 0;
        int nf = 0;
        int nb = 0;
        int nl = 0;
        int nr = 0;
        RigSkeleton sk = skeleton;
        for (int i = 0; i < n; i++) {
            Vector3d p = inv.transformPosition(new Vector3d(legs[i].stepping ? legs[i].to : legs[i].foot));
            RigSkeleton.Leg leg = sk.legs[i];
            float dy = (float) p.y; // ground under that foot against the ground under the entity
            sum += dy;
            if (leg.home().z > sk.tiltCentre.z + 0.01f) {
                front += dy;
                nf++;
            } else if (leg.home().z < sk.tiltCentre.z - 0.01f) {
                back += dy;
                nb++;
            }
            if (leg.home().x > sk.tiltCentre.x + 0.01f) {
                left += dy;
                nl++;
            } else if (leg.home().x < sk.tiltCentre.x - 0.01f) {
                right += dy;
                nr++;
            }
        }
        float reach = g.reachDown();
        float wantLift = Math.max(-reach, Math.min(g.reachUp(), sum / n));
        float max = (float) Math.toRadians(g.maxTilt());
        float wantPitch = 0;
        if (nf > 0 && nb > 0 && sk.frontBackZ > 0.1f) {
            wantPitch = (float) Math.atan2(front / nf - back / nb, sk.frontBackZ);
        }
        float wantRoll = 0;
        if (nl > 0 && nr > 0 && sk.leftRightX > 0.1f) {
            wantRoll = (float) Math.atan2(left / nl - right / nr, sk.leftRightX);
        }
        float k = g.bodySpring();
        lift += (wantLift - lift) * k;
        pitch += (clamp(wantPitch, max) - pitch) * k;
        roll += (clamp(wantRoll, max) - roll) * k;
    }

    private void chains(Body body) {
        if (Float.isNaN(lastYaw)) {
            lastYaw = body.yaw;
        }
        float turn = (float) Math.toRadians(wrap(body.yaw - lastYaw));
        lastYaw = body.yaw;
        for (int c = 0; c < chainYaw.length; c++) {
            RigSkeleton.Chain ch = skeleton.chains[c];
            float[] a = chainYaw[c];
            for (int i = 0; i < a.length; i++) {
                a[i] = a[i] * (1 - ch.stiffness()) - turn * ch.drag() * (i + 1) / a.length;
                a[i] = clamp(a[i], 1.2f);
            }
        }
    }

    static float clamp(float v, float max) {
        return Math.max(-max, Math.min(max, v));
    }

    static float wrap(float deg) {
        float d = deg % 360f;
        if (d >= 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    public static float lerp(float partial, float a, float b) {
        return a + (b - a) * partial;
    }
}
