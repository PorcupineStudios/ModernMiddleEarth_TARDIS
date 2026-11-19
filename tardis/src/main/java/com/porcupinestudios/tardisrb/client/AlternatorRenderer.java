package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.tardisrb.compat.create.AlternatorBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Artron Alternator as Aaron drew it (2026-11-19): Create's cogwheel turning on top (Flywheel draws it when it can,
 * this renderer otherwise), four iron arms reaching out and down round the police box under it, and, while it charges,
 * lightning crackling from the arm tips onto the box. Without a box under it the arms hang short.
 */
public final class AlternatorRenderer extends KineticBlockEntityRenderer<AlternatorBlockEntity> {
    private static final float BEAM = 0.12F;
    /** How far out from the middle the arms go (half the box's width plus a gap). */
    private static final float OUT = 0.78F;

    public AlternatorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(AlternatorBlockEntity be, BlockState state) {
        return CachedBuffers.partial(AllPartialModels.SHAFTLESS_COGWHEEL, state);
    }

    @Override
    protected void renderSafe(AlternatorBlockEntity be, float partialTicks, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        super.renderSafe(be, partialTicks, pose, buffers, light, overlay);
        // the arms reach down to the middle of the box (its top half's floor + 0), or hang 0.6 without one
        float down = be.drop() > 0 ? be.drop() - 0.1F : 0.6F;
        for (int k = 0; k < 4; k++) {
            float dx = k == 0 ? 1 : k == 1 ? -1 : 0;
            float dz = k == 2 ? 1 : k == 3 ? -1 : 0;
            // across from the hub, then down
            beam(pose, buffers, light, overlay, 0.5F, 0.3F, 0.5F, 0.5F + dx * OUT, 0.3F, 0.5F + dz * OUT);
            beam(pose, buffers, light, overlay, 0.5F + dx * OUT, 0.3F, 0.5F + dz * OUT, 0.5F + dx * OUT,
                    0.3F - down, 0.5F + dz * OUT);
        }
        if (be.charging() && be.drop() > 0 && be.getLevel() != null) {
            long tick = be.getLevel().getGameTime();
            RandomSource random = RandomSource.create(be.getBlockPos().asLong() * 31 + tick / 2);
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            Matrix4f m = pose.last().pose();
            for (int k = 0; k < 4; k++) {
                if (random.nextFloat() < 0.35F) {
                    continue; // they flicker
                }
                float dx = k == 0 ? 1 : k == 1 ? -1 : 0;
                float dz = k == 2 ? 1 : k == 3 ? -1 : 0;
                Vector3f from = new Vector3f(0.5F + dx * OUT, 0.3F - down * random.nextFloat(), 0.5F + dz * OUT);
                Vector3f to = new Vector3f(0.5F + dx * 0.5F, 0.3F - down - random.nextFloat() * 1.2F,
                        0.5F + dz * 0.5F).add((random.nextFloat() - 0.5F) * 0.8F * Math.abs(dz), 0,
                        (random.nextFloat() - 0.5F) * 0.8F * Math.abs(dx));
                bolt(vc, m, random, from, to);
            }
        }
    }

    /** A thin iron bar between two points on one axis (drawn as an iron block squeezed into a box). */
    private static void beam(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float x0, float y0,
            float z0, float x1, float y1, float z1) {
        float minX = Math.min(x0, x1) - BEAM / 2;
        float minY = Math.min(y0, y1) - BEAM / 2;
        float minZ = Math.min(z0, z1) - BEAM / 2;
        pose.pushPose();
        pose.translate(minX, minY, minZ);
        pose.scale(Math.abs(x1 - x0) + BEAM, Math.abs(y1 - y0) + BEAM, Math.abs(z1 - z0) + BEAM);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.IRON_BLOCK.defaultBlockState(), pose,
                buffers, light, overlay);
        pose.popPose();
    }

    /** A jagged lightning line: a few kinked segments, each a pale blue cross of two strips. */
    private static void bolt(VertexConsumer vc, Matrix4f m, RandomSource random, Vector3f from, Vector3f to) {
        int steps = 5;
        Vector3f last = new Vector3f(from);
        for (int i = 1; i <= steps; i++) {
            Vector3f next = new Vector3f(from).lerp(to, i / (float) steps);
            if (i < steps) {
                next.add((random.nextFloat() - 0.5F) * 0.25F, (random.nextFloat() - 0.5F) * 0.25F,
                        (random.nextFloat() - 0.5F) * 0.25F);
            }
            strip(vc, m, last, next, new Vector3f(0.05F, 0, 0));
            strip(vc, m, last, next, new Vector3f(0, 0, 0.05F));
            last = next;
        }
    }

    private static void strip(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f side) {
        Vector3f[] q = {new Vector3f(a).sub(side), new Vector3f(a).add(side), new Vector3f(b).add(side),
                new Vector3f(b).sub(side)};
        for (int k : new int[] {0, 1, 2, 3, 3, 2, 1, 0}) {
            vc.addVertex(m, q[k].x, q[k].y, q[k].z).setColor(0.55F, 0.85F, 1.0F, 0.7F);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(AlternatorBlockEntity be) {
        return true; // the arms reach below its block
    }
}
