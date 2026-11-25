package com.porcupinestudios.ringbearer.harness.client;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The drawn wagon wheel, in pixels round its centre with the axle along x: a hub, eight spokes, a wooden felloe in
 * sixteen pieces and an iron tyre over it. Radius 14.4 pixels (0.9 blocks, the data's default).
 * Texture {@code textures/entity/wagon_wheel.png}: wood at (0,0), iron at (16,0).
 */
public final class WagonWheelModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HarnessMod.id("wagon_wheel"), "main");
    public static final float RADIUS = 14.4F;

    private WagonWheelModel() {}

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hub", CubeListBuilder.create().texOffs(16, 0).addBox(-2.5F, -2.0F, -2.0F, 5, 4, 4),
                PartPose.ZERO);
        for (int i = 0; i < 8; i++) {
            root.addOrReplaceChild("spoke" + i, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-0.7F, 1.5F, -0.7F, 1.4F, RADIUS - 2.8F, 1.4F),
                    PartPose.rotation((float) (i * Math.PI / 4), 0, 0));
        }
        float piece = (float) (2 * Math.PI * RADIUS / 16 * 1.08);
        for (int i = 0; i < 16; i++) {
            root.addOrReplaceChild("felloe" + i, CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-1.1F, RADIUS - 2.2F, -piece / 2, 2.2F, 1.8F, piece)
                    .texOffs(16, 0).addBox(-1.3F, RADIUS - 0.5F, -piece / 2, 2.6F, 0.8F, piece),
                    PartPose.rotation((float) (i * 2 * Math.PI / 16), 0, 0));
        }
        return LayerDefinition.create(mesh, 32, 16);
    }
}
