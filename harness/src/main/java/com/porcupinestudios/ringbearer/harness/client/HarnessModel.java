package com.porcupinestudios.ringbearer.harness.client;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The harness as a horse-shaped model: the vanilla horse's parts with the same pivots (so it moves with the body, neck
 * and head) but only harness boxes in them. Units are model pixels in each part's own space; the horse's body box is
 * x -5..5, y -8..2, z -17..5 (front at -z), its neck x -2..2, y -6..6, z -2..5, its muzzle z -7..-2. Bands round
 * the body or neck are boxes just bigger than it, so the animal hides their insides.
 * Texture {@code textures/entity/harness.png} (64x32, from {@code harness/tools/textures.py}): leather at (0,0), brass
 * at (32,0), collar padding at (0,16).
 */
public final class HarnessModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HarnessMod.id("harness"), "main");
    private static final int LEATHER_U = 0, LEATHER_V = 0, BRASS_U = 32, BRASS_V = 0, PAD_U = 0, PAD_V = 16;

    private HarnessModel() {}

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder none = CubeListBuilder.create();

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                // backpad on the withers
                .texOffs(LEATHER_U, LEATHER_V).addBox(-4.0F, -9.2F, -12.0F, 8.0F, 1.2F, 5.0F)
                // girth round the chest behind the forelegs
                .texOffs(LEATHER_U, LEATHER_V).addBox(-5.6F, -8.6F, -10.5F, 11.2F, 11.2F, 1.4F)
                // hip strap over the croup, down to the breeching
                .texOffs(LEATHER_U, LEATHER_V).addBox(-5.6F, -8.6F, 0.5F, 11.2F, 7.8F, 1.0F)
                // breeching round the hindquarters
                .texOffs(LEATHER_U, LEATHER_V).addBox(-5.6F, -5.4F, 1.5F, 11.2F, 1.2F, 4.3F)
                // side straps along the flanks, chest to breeching (the traces run beside them to the tugs)
                .texOffs(LEATHER_U, LEATHER_V).addBox(5.05F, -5.0F, -17.0F, 0.6F, 0.8F, 18.5F)
                .texOffs(LEATHER_U, LEATHER_V).addBox(-5.65F, -5.0F, -17.0F, 0.6F, 0.8F, 18.5F)
                // brass tug rings where the ropes from the hitch end
                .texOffs(BRASS_U, BRASS_V).addBox(5.2F, -5.3F, -9.8F, 1.0F, 1.4F, 1.6F)
                .texOffs(BRASS_U, BRASS_V).addBox(-6.2F, -5.3F, -9.8F, 1.0F, 1.4F, 1.6F),
                PartPose.offset(0.0F, 11.0F, 5.0F));
        body.addOrReplaceChild("tail", none, PartPose.ZERO);
        body.addOrReplaceChild("saddle", none, PartPose.ZERO);

        PartDefinition head = root.addOrReplaceChild("head_parts", CubeListBuilder.create()
                // the collar: a thick padded ring round the lower neck (and the mane), clear of the shoulders (lower
                // down the body hid it, 2026-11-24 screenshots)
                .texOffs(PAD_U, PAD_V).addBox(-2.8F, 0.2F, -2.8F, 5.6F, 3.2F, 10.6F)
                // hames: brass bars snug along the collar's sides, a knob on top of each
                .texOffs(BRASS_U, BRASS_V).addBox(2.75F, -0.6F, -1.2F, 0.5F, 4.2F, 0.9F)
                .texOffs(BRASS_U, BRASS_V).addBox(-3.25F, -0.6F, -1.2F, 0.5F, 4.2F, 0.9F)
                .texOffs(BRASS_U, BRASS_V).addBox(2.65F, -1.2F, -1.3F, 0.7F, 0.6F, 1.1F)
                .texOffs(BRASS_U, BRASS_V).addBox(-3.35F, -1.2F, -1.3F, 0.7F, 0.6F, 1.1F)
                // bridle: noseband and crownpiece rings, cheek straps, bit rings, blinkers
                .texOffs(LEATHER_U, LEATHER_V).addBox(-2.3F, -11.3F, -5.0F, 4.6F, 5.6F, 0.9F)
                .texOffs(LEATHER_U, LEATHER_V).addBox(-3.3F, -11.3F, 2.4F, 6.6F, 5.6F, 0.9F)
                .texOffs(LEATHER_U, LEATHER_V).addBox(3.0F, -8.9F, -2.0F, 0.4F, 0.6F, 4.4F)
                .texOffs(LEATHER_U, LEATHER_V).addBox(-3.4F, -8.9F, -2.0F, 0.4F, 0.6F, 4.4F)
                .texOffs(LEATHER_U, LEATHER_V).addBox(2.0F, -8.9F, -4.1F, 0.4F, 0.6F, 2.1F)
                .texOffs(LEATHER_U, LEATHER_V).addBox(-2.4F, -8.9F, -4.1F, 0.4F, 0.6F, 2.1F)
                .texOffs(BRASS_U, BRASS_V).addBox(2.0F, -7.6F, -6.0F, 0.8F, 1.2F, 1.2F)
                .texOffs(BRASS_U, BRASS_V).addBox(-2.8F, -7.6F, -6.0F, 0.8F, 1.2F, 1.2F)
                .texOffs(LEATHER_U, LEATHER_V).addBox(3.1F, -10.6F, -1.0F, 0.4F, 2.6F, 2.8F)
                .texOffs(LEATHER_U, LEATHER_V).addBox(-3.5F, -10.6F, -1.0F, 0.4F, 2.6F, 2.8F),
                PartPose.offsetAndRotation(0.0F, 4.0F, -12.0F, (float) (Math.PI / 6), 0.0F, 0.0F));
        for (String part : new String[] {"left_saddle_mouth", "right_saddle_mouth", "left_saddle_line",
                "right_saddle_line", "head_saddle", "mouth_saddle_wrap"}) {
            head.addOrReplaceChild(part, none, PartPose.ZERO);
        }
        for (String leg : new String[] {"left_hind_leg", "right_hind_leg", "left_front_leg", "right_front_leg",
                "left_hind_baby_leg", "right_hind_baby_leg", "left_front_baby_leg", "right_front_baby_leg"}) {
            root.addOrReplaceChild(leg, none, PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 32);
    }
}
