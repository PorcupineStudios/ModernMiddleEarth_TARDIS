package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.ExteriorBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the police box from Aaron's model ({@link PoliceBoxModel}) on the lower half's block entity: the texture of its
 * look (bare wood showing on the wreck, the original paint once repaired), the lit windows from
 * {@code glow_from_look} on, and the two doors swinging open and shut on their hinges when the doors open or close.
 */
public final class ExteriorRenderer implements BlockEntityRenderer<ExteriorBlockEntity> {
    private static final ResourceLocation[] LOOKS = new ResourceLocation[4];
    private static final ResourceLocation GLOW = TardisRB.id("textures/entity/police_box/glow.png");

    static {
        for (int i = 0; i < LOOKS.length; i++) {
            LOOKS[i] = TardisRB.id("textures/entity/police_box/look_" + i + ".png");
        }
    }

    public ExteriorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ExteriorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ExteriorBlock)) {
            return;
        }
        PoliceBoxModel model = PoliceBoxModel.get();
        int look = Math.max(0, Math.min(LOOKS.length - 1, state.getValue(ExteriorBlock.LOOK)));
        float open = doorProgress(be,
                state.getValue(ExteriorBlock.OPEN) && !state.getValue(ExteriorBlock.CLOSING), partialTick, model.swingTicks);
        // ease in and out
        float angle = model.swingDegrees * open * open * (3 - 2 * open);
        Direction facing = state.getValue(ExteriorBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        drawAll(model, angle, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(LOOKS[look])), light);
        if (look >= model.glowFromLook) {
            // a full-bright cutout layer, not RenderType.eyes: Immersive Portals doesn't clip the eyes shader, so the
            // glowing lantern showed as a white box in the view through the doorway (Aaron 2026-11-04)
            drawAll(model, angle, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GLOW)),
                    LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }

    private static void drawAll(PoliceBoxModel model, float angle, PoseStack pose, VertexConsumer vc, int light) {
        draw(model.body, pose, vc, light);
        for (PoliceBoxModel.Part door : model.doors.values()) {
            pose.pushPose();
            pose.translate(door.hinge()[0], 0, door.hinge()[2]);
            pose.mulPose(Axis.YP.rotationDegrees(angle * door.openSign()));
            pose.translate(-door.hinge()[0], 0, -door.hinge()[2]);
            draw(door, pose, vc, light);
            pose.popPose();
        }
    }

    private static void draw(PoliceBoxModel.Part part, PoseStack pose, VertexConsumer vc, int light) {
        PoseStack.Pose p = pose.last();
        for (PoliceBoxModel.Tri t : part.tris()) {
            // the render types take quads: the third corner twice makes a triangle
            for (int i = 0; i < 4; i++) {
                float[] v = t.v()[Math.min(i, 2)];
                vc.addVertex(p, v[0], v[1], v[2]).setColor(255, 255, 255, 255).setUv(v[3], v[4])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(p, t.n()[0], t.n()[1], t.n()[2]);
            }
        }
    }

    /** How far open the doors are drawn (0 shut, 1 open), moving toward the block's open state over the swing time. */
    private static float doorProgress(ExteriorBlockEntity be, boolean open, float partialTick, float swingTicks) {
        float now = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + partialTick;
        float delta = be.doorAnimTime < 0 ? swingTicks : Math.max(0, Math.min(swingTicks, now - be.doorAnimTime));
        be.doorAnimTime = now;
        float step = delta / swingTicks;
        be.doorAnim = open ? Math.min(1, be.doorAnim + step) : Math.max(0, be.doorAnim - step);
        return be.doorAnim;
    }

    @Override
    public AABB getRenderBoundingBox(ExteriorBlockEntity be) {
        // two blocks tall, and the open doors reach out in front
        return new AABB(be.getBlockPos()).expandTowards(0, 1.3, 0).inflate(0.6, 0, 0.6);
    }
}
