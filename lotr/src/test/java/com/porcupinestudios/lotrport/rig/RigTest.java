package com.porcupinestudios.lotrport.rig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** The rig maths: IK lengths and reach, rest pose, stepping in turns, and the body following the ground. */
class RigTest {
    /** An eight-legged body like LOTR's spider: hips at y 17, femur 10 px out, tibia 7 px down to the ground. */
    static RigDef spider() {
        Map<String, RigDef.Bone> bones = new LinkedHashMap<>();
        List<RigDef.Leg> legs = new ArrayList<>();
        bones.put("thorax", new RigDef.Bone(Optional.empty(), List.of(0f, 17f, 0f), List.of(0f, 0f, 0f), List.of()));
        float[] spread = { 45f, 22.5f, -22.5f, -45f };
        float[] z = { 2f, 1f, 0f, -1f };
        for (int i = 0; i < 4; i++) {
            for (int side = 0; side < 2; side++) {
                int n = i * 2 + side + 1;
                float sx = side == 0 ? -1 : 1;
                String up = "leg" + n;
                String lo = "leg" + n + "_lower";
                bones.put(up, new RigDef.Bone(Optional.of("thorax"), List.of(4 * sx, 17f, z[i]),
                        List.of(0f, spread[i] * sx, 0f), List.of()));
                bones.put(lo, new RigDef.Bone(Optional.of(up), List.of(14 * sx, 17f, z[i]), List.of(0f, 0f, 0f),
                        List.of()));
                // alternating tetrapods: L1 R2 L3 R4 / R1 L2 R3 L4
                legs.add(new RigDef.Leg(up, lo, List.of(14 * sx, 24f, z[i]), (i + side) % 2, Optional.empty(), 1f));
            }
        }
        return new RigDef(List.of(), List.of(64, 32), 1f, Optional.empty(), bones, Optional.of("thorax"),
                Optional.empty(), legs, RigDef.Gait.DEFAULT, List.of(), List.of(), List.of(), Optional.empty());
    }

    static final RigState.Ground FLAT = (x, z, top, bottom) -> 0 <= top && 0 >= bottom ? 0 : Double.NaN;

    @Test
    void ikReachesAndKeepsBoneLengths() {
        Vector3f hip = new Vector3f(0, 1, 0);
        Vector3f restKnee = new Vector3f(0.6f, 1, 0);
        Vector3f restFoot = new Vector3f(0.6f, 0.5f, 0);
        Vector3f pole = new Vector3f(0, 1, 0);
        Ik.Result r = Ik.solve(hip, new Vector3f(0.5f, 0.2f, 0.3f), 0.6f, 0.5f, pole, hip, restKnee, restFoot, pole,
                new Ik.Result());
        assertEquals(0.6f, r.knee.distance(hip), 1e-4f);
        assertEquals(0.5f, r.foot.distance(r.knee), 1e-4f);
        assertEquals(0f, r.foot.distance(new Vector3f(0.5f, 0.2f, 0.3f)), 1e-4f);
        Vector3f mid = new Vector3f(hip).add(r.foot).mul(0.5f);
        assertTrue(new Vector3f(r.knee).sub(mid).dot(pole) > 0, "knee bends towards the pole (up)");
        // the turns carry each rest bone onto its new direction
        Vector3f d1 = r.upper.transform(new Vector3f(restKnee).sub(hip));
        assertEquals(0f, d1.distance(new Vector3f(r.knee).sub(hip)), 1e-4f);
        Vector3f d2 = r.lower.transform(new Vector3f(restFoot).sub(restKnee));
        assertEquals(0f, d2.distance(new Vector3f(r.foot).sub(r.knee)), 1e-4f);
    }

    @Test
    void ikStretchesTowardsTargetsOutOfReach() {
        Vector3f hip = new Vector3f();
        Vector3f pole = new Vector3f(0, 1, 0);
        Ik.Result r = Ik.solve(hip, new Vector3f(5, 0, 0), 1, 1, pole, hip, new Vector3f(1, 0, 0),
                new Vector3f(1, -1, 0), pole, new Ik.Result());
        assertEquals(2f, r.foot.length(), 1e-2f);
        assertEquals(1f, r.foot.normalize().x, 1e-3f);
    }

    @Test
    void restPoseMatchesTheModel() {
        RigSkeleton sk = new RigSkeleton(spider());
        RigState state = new RigState(sk);
        RigState.Body body = new RigState.Body();
        state.tick(body, FLAT, null);
        RigPose pose = new RigPose(sk).pose(state, body, 0, true);
        for (RigSkeleton.Leg leg : sk.legs) {
            Vector3f foot = pose.point(leg.lower(), leg.footLocal());
            assertEquals(0f, foot.distance(leg.foot()), 2e-3f, "foot on its rest spot");
            Vector3f knee = pose.bones[leg.lower()].getTranslation(new Vector3f());
            assertEquals(0f, knee.distance(leg.knee()), 2e-3f, "knee at rest");
            assertEquals(0f, leg.foot().y, 1e-6f);
        }
        assertEquals(1, sk.parent[sk.bone("leg1_lower")] == sk.bone("leg1") ? 1 : 0);
    }

    @Test
    void walkingStepsInTurnsAndKeepsUp() {
        RigSkeleton sk = new RigSkeleton(spider());
        RigState state = new RigState(sk);
        RigState.Body body = new RigState.Body();
        body.walk = 1;
        int[] landings = new int[1];
        RigState.Footfall count = (leg, x, y, z) -> landings[0]++;
        for (int t = 0; t < 200; t++) {
            body.vz = 0.12;
            body.z += body.vz;
            body.age = t;
            state.tick(body, FLAT, count);
            int group = -1;
            for (int i = 0; i < state.legs.length; i++) {
                if (state.legs[i].stepping) {
                    int g = sk.legs[i].group();
                    assertTrue(group < 0 || group == g, "only one group steps at a time (tick " + t + ")");
                    group = g;
                }
            }
            Matrix4d world = RigState.toWorld(body);
            for (int i = 0; i < state.legs.length; i++) {
                Vector3d home = world.transformPosition(new Vector3d(sk.legs[i].home().x, 0, sk.legs[i].home().z));
                double dx = state.legs[i].foot.x - home.x;
                double dz = state.legs[i].foot.z - home.z;
                assertTrue(Math.sqrt(dx * dx + dz * dz) < 3 * sk.def.gait().stepDistance(),
                        "foot " + i + " keeps up at tick " + t);
            }
        }
        assertTrue(landings[0] > 20, "feet land as it walks: " + landings[0]);
        // and the posed feet reach the planner's feet
        RigPose pose = new RigPose(sk).pose(state, body, 0.5f, true);
        Matrix4d inv = RigState.toWorld(body).invert();
        for (int i = 0; i < sk.legs.length; i++) {
            Vector3d want = inv.transformPosition(state.foot(i, 0.5f, 1));
            Vector3f got = pose.point(sk.legs[i].lower(), sk.legs[i].footLocal());
            double reach = sk.legs[i].l1() + sk.legs[i].l2();
            Vector3f hip = pose.bones[sk.legs[i].upper()].getTranslation(new Vector3f());
            if (hip.distance((float) want.x, (float) want.y, (float) want.z) < reach - 0.01) {
                assertEquals(0, got.distance((float) want.x, (float) want.y, (float) want.z), 2e-3);
            }
        }
    }

    @Test
    void bodyTiltsUpASlope() {
        RigSkeleton sk = new RigSkeleton(spider());
        RigState state = new RigState(sk);
        RigState.Body body = new RigState.Body();
        // ground rises 1 block per block towards +z (where it faces)
        RigState.Ground slope = (x, z, top, bottom) -> {
            double h = Math.max(-1, Math.min(1, z * 0.5));
            return h <= top && h >= bottom ? h : Double.NaN;
        };
        for (int t = 0; t < 60; t++) {
            state.tick(body, slope, null);
        }
        assertTrue(state.pitch > 0.1f, "nose up a rising slope: " + state.pitch);
        assertEquals(0f, state.roll, 1e-3f);
    }

    @Test
    void chainsLagBehindTurns() {
        RigDef d = spider();
        RigDef withTail = new RigDef(d.npcTypes(), d.texture(), d.scale(), d.scaleState(), d.bones(), d.body(),
                d.look(), d.legs(), d.gait(), d.sways(), List.of(new RigDef.Chain(List.of("thorax"), 0.5f, 0.2f, 0)),
                d.parts(), d.hide());
        RigState state = new RigState(new RigSkeleton(withTail));
        RigState.Body body = new RigState.Body();
        state.tick(body, FLAT, null);
        body.yaw = 20;
        state.tick(body, FLAT, null);
        assertTrue(state.chainYaw[0][0] < 0, "turning one way bends the chain the other way");
        for (int t = 0; t < 80; t++) {
            state.tick(body, FLAT, null);
        }
        assertEquals(0f, state.chainYaw[0][0], 1e-3f, "and it settles");
    }

    /** Every shipped rig file parses, names only real bones, and walks without NaNs or lost feet. */
    @Test
    void shippedRigsWalk() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Path.of("src/main/resources/data/lotr_port/rig");
        List<java.nio.file.Path> files;
        try (var list = java.nio.file.Files.list(dir)) {
            files = list.filter(f -> f.toString().endsWith(".json")).toList();
        }
        assertTrue(!files.isEmpty(), "rig files in " + dir.toAbsolutePath());
        for (java.nio.file.Path f : files) {
            com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f));
            RigDef def = RigDef.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            RigSkeleton sk = new RigSkeleton(def);
            assertEquals(def.legs().size(), sk.legs.length, f + ": every leg names real bones");
            assertEquals(def.parts().size(), sk.parts.length, f + ": every part names a real bone");
            for (int b = 0; b < sk.count; b++) {
                assertTrue(!sk.orphan(b), f + ": bone " + sk.names[b] + " has a real parent");
            }
            RigState state = new RigState(sk);
            RigPose pose = new RigPose(sk);
            RigState.Body body = new RigState.Body();
            body.scale = def.scale();
            body.walk = 1;
            for (int t = 0; t < 120; t++) {
                body.vz = 0.1 * def.scale();
                body.z += body.vz;
                body.yaw = t < 60 ? 0 : (t - 60) * 2;
                body.age = t;
                state.tick(body, FLAT, null);
                pose.pose(state, body, 0.5f, true);
                for (org.joml.Matrix4f m : pose.bones) {
                    assertTrue(m.isFinite(), f + ": finite pose at tick " + t);
                }
            }
        }
    }
}
