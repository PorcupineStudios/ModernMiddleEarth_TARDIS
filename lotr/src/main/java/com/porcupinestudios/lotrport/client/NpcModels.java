package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * The bodies LOTR draws its people with, for 64x64 skins. LOTR keeps hair in a long layer at (0, 32) that hangs
 * below the head (16 px for men, 12 for hobbits, dwarves and elves), not in the vanilla hat at (32, 0): drawing the
 * vanilla hat left everyone bald. Hobbits add big feet, elves pointed ears. 64x32 skins keep the classic humanoid.
 */
public final class NpcModels {
    public static final List<String> KINDS = List.of("human", "hobbit", "dwarf", "elf", "orc");

    private NpcModels() {}

    public static ModelLayerLocation layer(String kind) {
        return new ModelLayerLocation(LotrPort.id("npc"), "tall_" + kind);
    }

    /** How far the hair layer at (0, 32) hangs from the top of the head: 16 for men, 12 otherwise, 0 for orcs (none). */
    public static int hairRows(String kind) {
        return "orc".equals(kind) ? 0 : "human".equals(kind) ? 16 : 12;
    }

    /**
     * Hides the race parts armour would cut through: hobbit feet under boots, elf ears under a helmet. Call before
     * each render of a tall model, since one baked model is shared by everyone drawn on it.
     */
    public static void fitArmor(HumanoidModel<?> model, LivingEntity entity) {
        boolean boots = !entity.getItemBySlot(EquipmentSlot.FEET).isEmpty();
        boolean helmet = !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        for (ModelPart leg : List.of(model.rightLeg, model.leftLeg)) {
            if (leg.hasChild("foot")) {
                leg.getChild("foot").visible = !boots;
            }
        }
        for (String ear : List.of("right_ear", "left_ear")) {
            if (model.head.hasChild(ear)) {
                model.head.getChild(ear).visible = !helmet;
            }
        }
    }

    public static LayerDefinition tall(String kind) {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
        PartDefinition root = mesh.getRoot();
        if (!"orc".equals(kind)) {
            int hair = hairRows(kind);
            root.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(0, 32)
                    .addBox(-4f, -8f, -4f, 8, hair, 8, new CubeDeformation(0.5f)), PartPose.ZERO);
        }
        if ("hobbit".equals(kind)) {
            float turn = (float) Math.toRadians(10);
            root.getChild("right_leg").addOrReplaceChild("foot", CubeListBuilder.create().texOffs(40, 32)
                    .addBox(-2f, 10f, -5f, 4, 2, 3), PartPose.rotation(0, turn, 0));
            root.getChild("left_leg").addOrReplaceChild("foot", CubeListBuilder.create().texOffs(40, 32)
                    .addBox(-2f, 10f, -5f, 4, 2, 3), PartPose.rotation(0, -turn, 0));
        }
        if ("elf".equals(kind)) {
            float tilt = (float) Math.toRadians(15);
            PartDefinition head = root.getChild("head");
            head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-4f, -6.5f, -1f, 1, 4, 2), PartPose.rotation(0, 0, -tilt));
            head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(0, 0).mirror()
                    .addBox(3f, -6.5f, -1f, 1, 4, 2), PartPose.rotation(0, 0, tilt));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }
}
