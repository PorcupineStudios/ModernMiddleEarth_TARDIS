package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * LOTR's huorns are trees, drawn from blocks (LOTRModelHuorn): a trunk of logs with a crown of leaves, and a face
 * (the NPC's skin, LOTR's huorn face textures) on the front of the trunk. Dark huorns are dark oak. The tree turns
 * with the huorn and leans while it moves.
 */
final class HuornRenderer {
    private HuornRenderer() {}

    static void render(LotrNpc npc, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light, ResourceLocation face) {
        boolean dark = npc.typeId().getPath().contains("dark");
        BlockState log = (dark ? Blocks.DARK_OAK_LOG : Blocks.OAK_LOG).defaultBlockState();
        BlockState leaves = (dark ? Blocks.DARK_OAK_LEAVES : Blocks.OAK_LEAVES).defaultBlockState();
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        float lean = npc.walkAnimation.speed(partialTick) * 8f;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - npc.getViewYRot(partialTick)));
        pose.mulPose(Axis.XP.rotationDegrees(lean));
        pose.translate(-0.5, 0, -0.5);
        for (int y = 0; y < 3; y++) {
            pose.pushPose();
            pose.translate(0, y, 0);
            blocks.renderSingleBlock(log, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        for (int y = 2; y < 4; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (y == 2 && x == 0 && z == 0 || y == 3 && Math.abs(x) + Math.abs(z) == 2) {
                        continue;
                    }
                    pose.pushPose();
                    pose.translate(x, y, z);
                    blocks.renderSingleBlock(leaves, pose, buffers, light, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
            }
        }
        // the face, just in front of the middle log, facing forward
        Matrix4f m = pose.last().pose();
        VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(face));
        float z = -0.01f;
        quad(v, m, 0f, 0.5f, z, 1f, 1.5f, light);
        pose.popPose();
    }

    private static void quad(VertexConsumer v, Matrix4f m, float x0, float y0, float z, float x1, float y1,
            int light) {
        v.addVertex(m, x0, y0, z).setColor(-1).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(0, 0, -1);
        v.addVertex(m, x1, y0, z).setColor(-1).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(0, 0, -1);
        v.addVertex(m, x1, y1, z).setColor(-1).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(0, 0, -1);
        v.addVertex(m, x0, y1, z).setColor(-1).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(0, 0, -1);
    }
}
