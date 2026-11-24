package com.porcupinestudios.ringbearer.harness.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.WagonWheelBlock;
import com.porcupinestudios.ringbearer.harness.content.WagonWheelBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Draws a wagon wheel round its block's centre: riding its suspension, steered, rolled. */
public class WagonWheelRenderer implements BlockEntityRenderer<WagonWheelBlockEntity> {
    private static final ResourceLocation TEXTURE = HarnessMod.id("textures/entity/wagon_wheel.png");
    private final ModelPart wheel;

    public WagonWheelRenderer(BlockEntityRendererProvider.Context context) {
        this.wheel = context.bakeLayer(WagonWheelModel.LAYER);
    }

    @Override
    public void render(WagonWheelBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Direction out = be.getBlockState().getValue(WagonWheelBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0.5 + Mth.lerp(partialTick, be.hubO, be.hub), 0.5);
        // the model's axle is along x: turn it to the block's axle, then steer about up, then roll about the axle
        pose.mulPose(Axis.YP.rotationDegrees(-out.toYRot() - 90));
        // negated: the physics turns the rolling direction from +x toward +z for a positive angle
        // (WagonWheelBlockEntity.rollLocal), a positive YP rotation turns +x toward -z
        pose.mulPose(Axis.YP.rotationDegrees((float) -Mth.lerp(partialTick, be.clientSteerO, be.clientSteer)));
        pose.mulPose(Axis.XP.rotation((float) Mth.lerp(partialTick, be.rollO, be.roll)));
        wheel.render(pose, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light, overlay);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(WagonWheelBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.0);
    }
}
