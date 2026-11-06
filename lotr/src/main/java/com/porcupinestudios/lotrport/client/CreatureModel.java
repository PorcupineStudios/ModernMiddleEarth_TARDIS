package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * One of LOTR's creature bodies (warg, troll, spider, ent, Balrog...), built from {@code npc_model/<name>.json}
 * ({@code converter/entity_models.py}: LOTR's boxes, pivots and rest angles). It moves with LOTR's own animation,
 * converted from the model's setRotationAngles ({@link CreatureAnim}). A model without one (none today) falls back to
 * moving parts by name: the head looks, legs walk or scuttle, arms swing, tails and wings sway.
 */
public class CreatureModel extends EntityModel<LotrNpc> {
    private final ModelPart root;
    private final Map<String, ModelPart> parts = new LinkedHashMap<>();
    private final Map<ModelPart, PartPose> rest = new LinkedHashMap<>();
    private final List<ModelPart> heads = new ArrayList<>();
    private final List<ModelPart> legs = new ArrayList<>();
    private final List<String> legNames = new ArrayList<>();
    private final List<ModelPart> arms = new ArrayList<>();
    private final List<String> armNames = new ArrayList<>();
    private final List<ModelPart> tails = new ArrayList<>();
    private final List<ModelPart> wings = new ArrayList<>();
    private final List<String> wingNames = new ArrayList<>();
    /** Every named part (roots and named children), for LOTR's animation. */
    private final Map<String, ModelPart> named = new LinkedHashMap<>();
    private final Map<String, List<String>> paths = new LinkedHashMap<>();
    private final CreatureAnim anim;
    private final CreatureAnim.Ctx ctx = new CreatureAnim.Ctx();

    public CreatureModel(JsonObject def) {
        MeshDefinition mesh = new MeshDefinition();
        JsonObject json = def.getAsJsonObject("parts");
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            add(mesh.getRoot(), e.getKey(), e.getValue().getAsJsonObject(), List.of(e.getKey()), paths);
        }
        JsonArray size = def.getAsJsonArray("texture");
        root = LayerDefinition.create(mesh, size.get(0).getAsInt(), size.get(1).getAsInt()).bakeRoot();
        Set<String> hidden = new HashSet<>();
        if (def.has("hidden")) {
            def.getAsJsonArray("hidden").forEach(h -> hidden.add(h.getAsString()));
        }
        for (String name : json.keySet()) {
            ModelPart p = root.getChild(name);
            parts.put(name, p);
            p.visible = !hidden.contains(name);
            String n = name.toLowerCase();
            if (n.contains("head") && !n.contains("hurt") || n.equals("neck")) {
                heads.add(p);
            } else if (n.contains("leg") || n.contains("thigh")) {
                legs.add(p);
                legNames.add(n);
            } else if (n.contains("arm") || n.contains("shoulder")) {
                arms.add(p);
                armNames.add(n);
            } else if (n.contains("tail")) {
                tails.add(p);
            } else if (n.contains("wing")) {
                wings.add(p);
                wingNames.add(n);
            }
        }
        root.getAllParts().forEach(p -> rest.put(p, p.storePose()));
        paths.forEach((name, path) -> {
            ModelPart p = root;
            for (String step : path) {
                p = p.getChild(step);
            }
            named.put(name, p);
        });
        anim = def.has("anim") ? new CreatureAnim(def.getAsJsonObject("anim"), named) : null;
        ctx.parts = named;
        ctx.rest = rest;
    }

    private static void add(PartDefinition parent, String name, JsonObject part, List<String> path,
            Map<String, List<String>> paths) {
        paths.put(part.has("name") ? part.get("name").getAsString() : name, path);
        CubeListBuilder cubes = CubeListBuilder.create();
        for (JsonElement c : part.getAsJsonArray("cubes")) {
            JsonObject cube = c.getAsJsonObject();
            JsonArray o = cube.getAsJsonArray("origin");
            JsonArray s = cube.getAsJsonArray("size");
            JsonArray uv = cube.getAsJsonArray("uv");
            cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).mirror(cube.get("mirror").getAsBoolean())
                    .addBox(o.get(0).getAsFloat(), o.get(1).getAsFloat(), o.get(2).getAsFloat(),
                            s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat(),
                            new CubeDeformation(cube.get("inflate").getAsFloat()));
        }
        JsonArray p = part.getAsJsonArray("pivot");
        JsonArray r = part.getAsJsonArray("rotation");
        PartDefinition def = parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(
                p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat(),
                r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat()));
        int n = 0;
        for (JsonElement child : part.getAsJsonArray("children")) {
            String childName = "child_" + n++;
            List<String> childPath = new ArrayList<>(path);
            childPath.add(childName);
            add(def, childName, child.getAsJsonObject(), childPath, paths);
        }
    }

    @Override
    public void setupAnim(LotrNpc npc, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch) {
        rest.forEach(ModelPart::loadPose);
        if (anim != null) {
            // LOTR's own setRotationAngles for this model (converter/model_anim.py)
            ctx.npc = npc;
            ctx.limb = limbSwing;
            ctx.amount = limbSwingAmount;
            ctx.age = ageInTicks;
            ctx.yaw = netHeadYaw;
            ctx.pitch = headPitch;
            ctx.swing = attackTime;
            if (anim.biped) {
                vanillaBiped(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            }
            anim.apply(ctx);
            return;
        }
        for (ModelPart head : heads) {
            head.yRot += netHeadYaw * Mth.DEG_TO_RAD;
            head.xRot += headPitch * Mth.DEG_TO_RAD;
        }
        float walk = limbSwing * 0.6662f;
        boolean scuttle = legs.size() >= 6;
        for (int i = 0; i < legs.size(); i++) {
            float phase = legPhase(legNames.get(i), i, legs.size());
            if (scuttle) {
                float side = legNames.get(i).matches(".*[1357]$") ? 1f : -1f;
                legs.get(i).yRot += Mth.cos(walk * 2f + phase) * 0.4f * limbSwingAmount;
                legs.get(i).zRot += side * Math.abs(Mth.sin(walk + phase)) * 0.4f * limbSwingAmount;
            } else {
                legs.get(i).xRot += Mth.cos(walk + phase) * 1.4f * limbSwingAmount;
            }
        }
        for (int i = 0; i < arms.size(); i++) {
            float phase = armNames.get(i).contains("left") ? 0f : Mth.PI;
            arms.get(i).xRot += Mth.cos(walk + phase) * limbSwingAmount;
        }
        if (attackTime > 0) {
            for (int i = 0; i < arms.size(); i++) {
                if (armNames.get(i).contains("right")) {
                    arms.get(i).xRot -= Mth.sin(attackTime * Mth.PI) * 1.2f;
                }
            }
        }
        for (ModelPart tail : tails) {
            tail.yRot += Mth.cos(ageInTicks * 0.1f) * 0.15f;
            tail.xRot += Mth.cos(walk) * 0.3f * limbSwingAmount;
        }
        for (int i = 0; i < wings.size(); i++) {
            float side = wingNames.get(i).contains("left") ? 1f : -1f;
            wings.get(i).zRot += side * Mth.cos(ageInTicks * 0.15f) * 0.15f;
        }
    }

    /**
     * Vanilla's humanoid animation (1.7.10 ModelBiped, with ModelZombie's raised arms), which LOTR's models built on
     * vanilla's call first: head look, legs and arms swinging, arms held out.
     */
    private void vanillaBiped(float limb, float amount, float age, float yaw, float pitch) {
        ModelPart head = named.get("head");
        ModelPart hat = named.get("hat");
        ModelPart rightArm = named.get("right_arm");
        ModelPart leftArm = named.get("left_arm");
        ModelPart rightLeg = named.get("right_leg");
        ModelPart leftLeg = named.get("left_leg");
        if (head != null) {
            head.yRot = yaw * Mth.DEG_TO_RAD;
            head.xRot = pitch * Mth.DEG_TO_RAD;
            if (hat != null) {
                hat.yRot = head.yRot;
                hat.xRot = head.xRot;
            }
        }
        float walk = limb * 0.6662f;
        if (rightLeg != null && leftLeg != null) {
            rightLeg.xRot = Mth.cos(walk) * 1.4f * amount;
            leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.4f * amount;
        }
        if (rightArm != null && leftArm != null) {
            float swing = Mth.sin(attackTime * Mth.PI);
            float lift = Mth.sin((1f - (1f - attackTime) * (1f - attackTime)) * Mth.PI);
            rightArm.zRot = 0f;
            leftArm.zRot = 0f;
            rightArm.yRot = -(0.1f - swing * 0.6f);
            leftArm.yRot = 0.1f - swing * 0.6f;
            rightArm.xRot = -Mth.HALF_PI - (swing * 1.2f - lift * 0.4f);
            leftArm.xRot = -Mth.HALF_PI - (swing * 1.2f - lift * 0.4f);
            rightArm.zRot += Mth.cos(age * 0.09f) * 0.05f + 0.05f;
            leftArm.zRot -= Mth.cos(age * 0.09f) * 0.05f + 0.05f;
            rightArm.xRot += Mth.sin(age * 0.067f) * 0.05f;
            leftArm.xRot -= Mth.sin(age * 0.067f) * 0.05f;
        }
    }

    /** Right legs and odd legs on one beat, left and even on the other; four legs walk diagonally. */
    private static float legPhase(String name, int index, int count) {
        if (name.contains("right")) {
            return 0f;
        }
        if (name.contains("left")) {
            return Mth.PI;
        }
        float phase = index % 2 == 0 ? 0f : Mth.PI;
        return count == 4 && index >= 2 ? phase + Mth.PI : phase;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        root.render(pose, buffer, light, overlay, color);
    }
}
