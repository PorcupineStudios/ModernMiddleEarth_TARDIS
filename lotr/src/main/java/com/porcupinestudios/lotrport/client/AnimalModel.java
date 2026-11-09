package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.lotrport.animal.AnimalType;
import com.porcupinestudios.lotrport.animal.LotrAnimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * A LOTR animal model from {@code animals.json} ({@code converter/animals.py}): LOTR's boxes in their resting pose;
 * each part swings with walking (angle and pivot, {@code amp * cos(k * limbSwing) * limbSwingAmount}, as LOTR's
 * setRotationAngles does) and turns with the head as much as LOTR's does. Male-only and adult-only parts (antlers,
 * horns) hide by the animal's sex and age.
 */
public class AnimalModel extends EntityModel<Mob> {
    private record Moving(ModelPart part, PartPose rest, float swing, float k, float[] move, float pitch, float yaw,
            boolean maleOnly, boolean adultOnly) {}

    private final ModelPart root;
    private final List<Moving> parts = new ArrayList<>();

    public AnimalModel(JsonObject def, AnimalType type) {
        MeshDefinition mesh = new MeshDefinition();
        List<JsonObject> order = new ArrayList<>();
        int n = 0;
        for (JsonElement p : def.getAsJsonArray("parts")) {
            n = add(mesh.getRoot(), p.getAsJsonObject(), n, order);
        }
        JsonArray size = def.getAsJsonArray("texture");
        root = LayerDefinition.create(mesh, size.get(0).getAsInt(), size.get(1).getAsInt()).bakeRoot();
        collect(root, order, type, new int[] {0}, false, false);
    }

    /** Adds the part as "p<n>" (so names never clash) and its children; order lists the parts as numbered. */
    private static int add(PartDefinition parent, JsonObject part, int n, List<JsonObject> order) {
        CubeListBuilder cubes = CubeListBuilder.create();
        for (JsonElement c : part.getAsJsonArray("cubes")) {
            JsonObject cube = c.getAsJsonObject();
            float[] o = floats(cube.getAsJsonArray("origin"));
            float[] s = floats(cube.getAsJsonArray("size"));
            float[] uv = floats(cube.getAsJsonArray("uv"));
            cubes.texOffs((int) uv[0], (int) uv[1]).mirror(cube.get("mirror").getAsBoolean())
                    .addBox(o[0], o[1], o[2], s[0], s[1], s[2], new CubeDeformation(cube.get("inflate").getAsFloat()));
        }
        float[] p = floats(part.getAsJsonArray("pivot"));
        float[] r = floats(part.getAsJsonArray("rotation"));
        PartDefinition def = parent.addOrReplaceChild("p" + n, cubes,
                PartPose.offsetAndRotation(p[0], p[1], p[2], r[0], r[1], r[2]));
        order.add(part);
        n++;
        for (JsonElement child : part.getAsJsonArray("children")) {
            n = add(def, child.getAsJsonObject(), n, order);
        }
        return n;
    }

    private void collect(ModelPart parent, List<JsonObject> order, AnimalType type, int[] n, boolean male,
            boolean adult) {
        while (parent.hasChild("p" + n[0])) {
            int i = n[0]++;
            ModelPart part = parent.getChild("p" + i);
            JsonObject j = order.get(i);
            String name = j.has("name") ? j.get("name").getAsString() : "";
            boolean m = male || type.maleParts().contains(name);
            boolean a = adult || type.adultParts().contains(name);
            float[] swing = j.has("swing") ? floats(j.getAsJsonArray("swing")) : new float[2];
            float[] look = j.has("look") ? floats(j.getAsJsonArray("look")) : new float[2];
            parts.add(new Moving(part, part.storePose(), swing[0], swing[1],
                    j.has("move") ? floats(j.getAsJsonArray("move")) : null, look[0], look[1], m, a));
            collect(part, order, type, n, m, a);
        }
    }

    @Override
    public void setupAnim(Mob entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch) {
        boolean male = !(entity instanceof LotrAnimal a) || a.isMale();
        for (Moving m : parts) {
            ModelPart p = m.part;
            p.loadPose(m.rest);
            float c = Mth.cos(limbSwing * m.k) * limbSwingAmount;
            p.xRot += m.swing * c + m.pitch * headPitch;
            p.yRot += m.yaw * netHeadYaw;
            if (m.move != null) {
                p.x += m.move[0] * c;
                p.y += m.move[1] * c;
                p.z += m.move[2] * c;
            }
            p.visible = !(m.maleOnly && !male) && !(m.adultOnly && entity.isBaby());
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        root.render(pose, buffer, light, overlay, color);
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }
}
