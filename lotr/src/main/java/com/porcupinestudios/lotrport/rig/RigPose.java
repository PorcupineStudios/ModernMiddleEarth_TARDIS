package com.porcupinestudios.lotrport.rig;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Poses a skeleton: body lift and tilt, the look bone, sways and lagging chains by forward kinematics, then the legs by
 * {@link Ik} to their feet. The result is one matrix per bone, bone space (origin at its pivot, rest orientation) to
 * rig space. Used by the server for hitboxes and by the client to draw. Pure maths; unit-tested.
 */
public final class RigPose {
    public final Matrix4f[] bones;
    private final RigSkeleton skeleton;
    private final Ik.Result solved = new Ik.Result();

    public RigPose(RigSkeleton skeleton) {
        this.skeleton = skeleton;
        bones = new Matrix4f[skeleton.count];
        for (int i = 0; i < bones.length; i++) {
            bones[i] = new Matrix4f();
        }
    }

    /**
     * Poses the rig for one frame.
     *
     * @param body the entity, positions interpolated for this frame
     * @param partial how far into the tick (0 on the server)
     * @param ik whether legs reach for their feet (false far away: legs keep their rest pose)
     */
    public RigPose pose(RigState state, RigState.Body body, float partial, boolean ik) {
        RigSkeleton sk = skeleton;
        float lift = RigState.lerp(partial, state.prevLift, state.lift);
        float pitch = RigState.lerp(partial, state.prevPitch, state.pitch);
        float roll = RigState.lerp(partial, state.prevRoll, state.roll);
        float walk = RigState.lerp(partial, state.prevWalk, state.walk);
        float h = body.hide;
        float awake = 1 - h;
        Vector3f c = sk.tiltCentre;
        Matrix4f bodyFrame = new Matrix4f().translation(0, lift - sk.hideDrop * h, 0).translate(c).rotateX(-pitch).rotateZ(roll)
                .translate(-c.x, -c.y, -c.z);
        Quaternionf bodyTurn = bodyFrame.getNormalizedRotation(new Quaternionf());

        Quaternionf[] anim = new Quaternionf[sk.count];
        if (sk.look >= 0 && awake > 0) {
            anim[sk.look] = new Quaternionf().rotateY((float) Math.toRadians(-body.headYaw * awake))
                    .rotateX((float) Math.toRadians(body.headPitch * awake));
        }
        for (RigSkeleton.Sway s : sk.sways) {
            float amount = s.amount() * (1 + (s.walk() - 1) * walk) * awake;
            float a = (float) Math.sin(body.age * s.speed() + s.phase()) * amount;
            Quaternionf q = switch (s.axis()) {
                case 1 -> new Quaternionf().rotationY(-a);
                case 2 -> new Quaternionf().rotationZ(-a);
                default -> new Quaternionf().rotationX(a);
            };
            anim[s.bone()] = anim[s.bone()] == null ? q : anim[s.bone()].mul(q);
        }
        for (int ch = 0; ch < sk.chains.length; ch++) {
            RigSkeleton.Chain chain = sk.chains[ch];
            for (int i = 0; i < chain.bones().length; i++) {
                float yaw = RigState.lerp(partial, state.prevChainYaw[ch][i], state.chainYaw[ch][i]) * awake;
                Quaternionf q = new Quaternionf().rotationY(yaw).rotateX(chain.droop() * walk);
                int b = chain.bones()[i];
                anim[b] = anim[b] == null ? q : anim[b].mul(q);
            }
        }

        if (h > 0) {
            for (int b = 0; b < sk.count; b++) {
                if (sk.hidePose[b] != null) {
                    Quaternionf q = new Quaternionf().slerp(sk.hidePose[b], h);
                    anim[b] = anim[b] == null ? q : anim[b].mul(q);
                }
            }
        }

        // feet in rig space
        Vector3f[] feet = new Vector3f[sk.legs.length];
        int[] legOfUpper = new int[sk.count];
        int[] legOfLower = new int[sk.count];
        java.util.Arrays.fill(legOfUpper, -1);
        java.util.Arrays.fill(legOfLower, -1);
        if (ik && sk.legs.length > 0) {
            Matrix4d inv = RigState.toWorld(body).invert();
            for (int i = 0; i < sk.legs.length; i++) {
                Vector3d w = state.foot(i, partial, body.scale);
                Vector3d r = inv.transformPosition(w);
                feet[i] = new Vector3f((float) r.x, (float) r.y, (float) r.z);
                legOfUpper[sk.legs[i].upper()] = i;
                legOfLower[sk.legs[i].lower()] = i;
            }
        }

        Vector3f[] knees = new Vector3f[sk.legs.length];
        Quaternionf[] lowerTurn = new Quaternionf[sk.legs.length];
        for (int b = 0; b < sk.count; b++) {
            int p = sk.parent[b];
            Matrix4f m = bones[b];
            if (legOfLower[b] >= 0 && knees[legOfLower[b]] != null) {
                int li = legOfLower[b];
                m.translation(knees[li]).rotate(lowerTurn[li]).rotate(sk.legs[li].lowerRest());
                continue;
            }
            if (p < 0) {
                m.set(sk.body < 0 || sk.body == b ? bodyFrame : new Matrix4f()).translate(sk.pivot[b]);
            } else {
                m.set(bones[p]).translate(new Vector3f(sk.pivot[b]).sub(sk.pivot[p]));
            }
            m.rotate(sk.rest[b]);
            if (anim[b] != null) {
                m.rotate(anim[b]);
            }
            int li = legOfUpper[b];
            if (li >= 0) {
                RigSkeleton.Leg leg = sk.legs[li];
                Vector3f hip = m.getTranslation(new Vector3f());
                Vector3f pole = bodyTurn.transform(new Vector3f(leg.pole()));
                Ik.solve(hip, feet[li], leg.l1(), leg.l2(), pole, leg.hip(), leg.knee(), leg.foot(), leg.pole(),
                        solved);
                // the upper bone's rest orientation is its rest-world rotation; IK turns it from there
                m.translation(hip).rotate(new Quaternionf(solved.upper)).rotate(leg.upperRest());
                knees[li] = new Vector3f(solved.knee);
                lowerTurn[li] = new Quaternionf(solved.lower);
            }
        }
        return this;
    }

    /** A point given in a bone's space (offset from its pivot, rig blocks) in rig space now. */
    public Vector3f point(int bone, Vector3f local) {
        return bones[bone].transformPosition(new Vector3f(local));
    }
}
