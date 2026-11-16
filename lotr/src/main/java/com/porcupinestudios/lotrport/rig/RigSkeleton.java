package com.porcupinestudios.lotrport.rig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A {@link RigDef} compiled for posing: bones in parent-first order with their rest transforms in rig space.
 *
 * <p>Rig space: blocks at scale 1, origin on the ground under the entity, y up, +z the way it faces, x as the model's
 * x. A model pixel point {@code (x, y, z)} is {@code (x / 16, (24 - y) / 16, -z / 16)} there; this is exactly where
 * Minecraft's living renderer draws it (its flips and 180 degree turn). World = translate(entity) x rotateY(-yaw) x
 * scale x rig space.
 */
public final class RigSkeleton {
    public final RigDef def;
    public final int count;
    public final String[] names;
    public final int[] parent;
    /** Absolute pivots in rig space (unrotated, as written). */
    public final Vector3f[] pivot;
    /** Each bone's own rest rotation. */
    public final Quaternionf[] rest;
    /** Rest pose with no animation: bone space (origin at the pivot) to rig space. */
    public final Matrix4f[] restWorld;
    public final RigDef.Bone[] bones;
    private final Map<String, Integer> index = new HashMap<>();
    /** The bone that carries the body tilt; -1 = every root. */
    public final int body;
    public final int look;
    public final Leg[] legs;
    public final Sway[] sways;
    public final Chain[] chains;
    public final Part[] parts;
    /** Body tilt: centre of the hips, and which legs are front/back/left/right (for pitch and roll). */
    public final Vector3f tiltCentre = new Vector3f();
    public final float frontBackZ;
    public final float leftRightX;
    /** How far any bone or foot reaches from the origin at rest (rig blocks), for render culling. */
    public final float reach;

    /** A compiled leg; rest points in rig space. {@code home} is where the foot plants at rest (on the ground). */
    public record Leg(int upper, int lower, Vector3f hip, Vector3f knee, Vector3f foot, Vector3f footLocal, float l1,
            float l2, Vector3f pole, Quaternionf upperRest, Quaternionf lowerRest, int group, Vector3f home) {}

    public record Sway(int bone, int axis, float amount, float speed, float phase, float walk) {}

    public record Chain(int[] bones, float drag, float stiffness, float droop) {}

    /** A hitbox: {@code local} is its centre in its bone's space. */
    public record Part(String name, int bone, Vector3f local, float width, float height, float damage,
            boolean solid) {}

    public RigSkeleton(RigDef def) {
        this.def = def;
        List<String> order = order(def.bones());
        count = order.size();
        names = order.toArray(new String[0]);
        for (int i = 0; i < count; i++) {
            index.put(names[i], i);
        }
        parent = new int[count];
        pivot = new Vector3f[count];
        rest = new Quaternionf[count];
        restWorld = new Matrix4f[count];
        bones = new RigDef.Bone[count];
        for (int i = 0; i < count; i++) {
            RigDef.Bone b = def.bones().get(names[i]);
            bones[i] = b;
            int p = b.parent().map(index::get).orElse(-1);
            parent[i] = p < i ? p : -1;
            pivot[i] = point(b.pivot());
            rest[i] = rotation(b.rotation());
            Matrix4f m = parent[i] < 0 ? new Matrix4f() : new Matrix4f(restWorld[parent[i]]);
            Vector3f off = parent[i] < 0 ? new Vector3f(pivot[i]) : new Vector3f(pivot[i]).sub(pivot[parent[i]]);
            restWorld[i] = m.translate(off).rotate(rest[i]);
        }
        body = def.body().map(this::bone).orElse(-1);
        look = def.look().map(this::bone).orElse(-1);

        List<Leg> legList = new ArrayList<>();
        float reachMax = 0;
        for (RigDef.Leg l : def.legs()) {
            int u = bone(l.upper());
            int lo = bone(l.lower());
            if (u < 0 || lo < 0) {
                continue;
            }
            Vector3f hip = restWorld[u].getTranslation(new Vector3f());
            Vector3f knee = restWorld[lo].getTranslation(new Vector3f());
            Vector3f footLocal = dir(l.foot().get(0) - bones[lo].pivot().get(0),
                    l.foot().get(1) - bones[lo].pivot().get(1), l.foot().get(2) - bones[lo].pivot().get(2));
            Vector3f foot = restWorld[lo].transformPosition(new Vector3f(footLocal));
            Vector3f pole;
            if (l.bend().isPresent()) {
                List<Float> b = l.bend().get();
                pole = dir(b.get(0), b.get(1), b.get(2)).normalize();
            } else {
                pole = new Vector3f(knee).sub(new Vector3f(hip).add(foot).mul(0.5f));
                if (pole.lengthSquared() < 1e-6f) {
                    pole.set(0, 1, 0);
                }
                pole.normalize();
            }
            Quaternionf ur = restWorld[u].getNormalizedRotation(new Quaternionf());
            Quaternionf lr = restWorld[lo].getNormalizedRotation(new Quaternionf());
            Vector3f home = new Vector3f(hip.x + (foot.x - hip.x) * l.spread(), 0,
                    hip.z + (foot.z - hip.z) * l.spread());
            legList.add(new Leg(u, lo, hip, knee, foot, footLocal, hip.distance(knee), knee.distance(foot), pole, ur,
                    lr, l.group(), home));
            reachMax = Math.max(reachMax, Math.max(foot.length(), home.length()));
        }
        legs = legList.toArray(new Leg[0]);

        List<Sway> swayList = new ArrayList<>();
        for (RigDef.Sway s : def.sways()) {
            int b = bone(s.bone());
            if (b >= 0) {
                int axis = switch (s.axis()) {
                    case "y" -> 1;
                    case "z" -> 2;
                    default -> 0;
                };
                swayList.add(new Sway(b, axis, (float) Math.toRadians(s.amount()), s.speed(), s.phase(), s.walk()));
            }
        }
        sways = swayList.toArray(new Sway[0]);

        List<Chain> chainList = new ArrayList<>();
        for (RigDef.Chain c : def.chains()) {
            int[] ids = c.bones().stream().mapToInt(this::bone).filter(b -> b >= 0).toArray();
            if (ids.length > 0) {
                chainList.add(new Chain(ids, c.drag(), c.stiffness(), (float) Math.toRadians(c.droop())));
            }
        }
        chains = chainList.toArray(new Chain[0]);

        List<Part> partList = new ArrayList<>();
        for (RigDef.Part p : def.parts()) {
            int b = bone(p.bone());
            if (b < 0) {
                continue;
            }
            Vector3f local = dir(p.center().get(0) - bones[b].pivot().get(0),
                    p.center().get(1) - bones[b].pivot().get(1), p.center().get(2) - bones[b].pivot().get(2));
            partList.add(new Part(p.name(), b, local, p.width(), p.height(), p.damage(), p.solid()));
            reachMax = Math.max(reachMax, restWorld[b].transformPosition(new Vector3f(local)).length()
                    + Math.max(p.width(), p.height()));
        }
        parts = partList.toArray(new Part[0]);

        float fz = 0;
        float lx = 0;
        if (legs.length > 0) {
            float minZ = Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            for (Leg l : legs) {
                tiltCentre.add(l.hip);
                minZ = Math.min(minZ, l.foot.z);
                maxZ = Math.max(maxZ, l.foot.z);
                minX = Math.min(minX, l.foot.x);
                maxX = Math.max(maxX, l.foot.x);
            }
            tiltCentre.div(legs.length);
            fz = maxZ - minZ;
            lx = maxX - minX;
        } else if (body >= 0) {
            tiltCentre.set(pivot[body]);
        }
        frontBackZ = fz;
        leftRightX = lx;
        for (int i = 0; i < count; i++) {
            reachMax = Math.max(reachMax, restWorld[i].getTranslation(new Vector3f()).length() + 1);
        }
        reach = reachMax;
    }

    /** A bone's index, -1 if there is none of that name. */
    public int bone(String name) {
        Integer i = index.get(name);
        return i == null ? -1 : i;
    }

    /** Model pixels (absolute) to rig space. */
    public static Vector3f point(List<Float> p) {
        return new Vector3f(p.get(0) / 16f, (24f - p.get(1)) / 16f, -p.get(2) / 16f);
    }

    /** A model pixel offset to rig space. */
    public static Vector3f dir(float x, float y, float z) {
        return new Vector3f(x / 16f, -y / 16f, -z / 16f);
    }

    /** A model rotation (degrees, Minecraft's z-y-x order) in rig space: the y and z turns flip with the axes. */
    public static Quaternionf rotation(List<Float> deg) {
        return new Quaternionf().rotationZYX((float) Math.toRadians(-deg.get(2)), (float) Math.toRadians(-deg.get(1)),
                (float) Math.toRadians(deg.get(0)));
    }

    /** Bone names with every parent before its children; bones with a missing parent become roots. */
    private static List<String> order(Map<String, RigDef.Bone> bones) {
        List<String> out = new ArrayList<>();
        List<String> left = new ArrayList<>(bones.keySet());
        left.sort(String::compareTo); // a stable order whatever map the codec made
        while (!left.isEmpty()) {
            boolean moved = false;
            for (int i = 0; i < left.size(); i++) {
                String n = left.get(i);
                String p = bones.get(n).parent().orElse(null);
                if (p == null || !bones.containsKey(p) || out.contains(p)) {
                    out.add(n);
                    left.remove(i--);
                    moved = true;
                }
            }
            if (!moved) { // a loop: break it
                out.add(left.remove(0));
            }
        }
        return out;
    }

    /** Whether a bone's parent was named but missing or looped (it was made a root). */
    public boolean orphan(int b) {
        return parent[b] < 0 && bones[b].parent().isPresent();
    }
}
