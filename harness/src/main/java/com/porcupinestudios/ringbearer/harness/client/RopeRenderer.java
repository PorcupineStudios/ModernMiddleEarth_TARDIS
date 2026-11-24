package com.porcupinestudios.ringbearer.harness.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.HitchBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/**
 * The two ropes from a hitch to a hitched animal's harness: from the hooks at the ends of the hitch's bar to the tug
 * rings on the animal's sides (where its {@link HarnessFits.Fit} says), drawn like leads, after the animal itself
 * (RenderLivingEvent.Post, so the pose stack sits at the animal). The hitch is usually on a moving vehicle: its hooks go
 * through the vehicle's interpolated render pose.
 */
public final class RopeRenderer {
    /** The hooks on {@code models/block/hitch.json}: blocks to the side, up and forward of the block's centre. */
    private static final double HOOK_SIDE = 0.42, HOOK_UP = -0.11, HOOK_FORWARD = 0.42;
    private static final int SEGMENTS = 16;
    private static final float HALF_WIDTH = 0.03F;

    private RopeRenderer() {}

    public static void render(LivingEntity animal, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                              int animalLight) {
        var link = animal.getData(HarnessMod.HITCH_LINK);
        if (link.isEmpty()) {
            return;
        }
        Level level = animal.level();
        BlockPos pos = link.get().harness();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HitchBlock)) {
            return; // not loaded here yet
        }
        Direction facing = state.getValue(HitchBlock.FACING);
        ClientSubLevel vehicle = Sable.HELPER.getContainingClient(pos.getX() >> 4, pos.getZ() >> 4);

        HarnessFits.Fit fit = HarnessFits.of(animal.getType());
        Vec3 origin = animal.getPosition(partialTick);
        double yaw = Math.toRadians(Mth.rotLerp(partialTick, animal.yBodyRotO, animal.yBodyRot));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -fz, rz = fx;
        double side = fit.ropeAnchor().get(0), up = fit.ropeAnchor().get(1), fwd = fit.ropeAnchor().get(2);

        double hx = facing.getStepX(), hz = facing.getStepZ();
        double hrx = -hz, hrz = hx;
        for (int s = -1; s <= 1; s += 2) {
            Vector3d hook = new Vector3d(pos.getX() + 0.5 + hrx * HOOK_SIDE * s + hx * HOOK_FORWARD,
                    pos.getY() + 0.5 + HOOK_UP, pos.getZ() + 0.5 + hrz * HOOK_SIDE * s + hz * HOOK_FORWARD);
            if (vehicle != null) {
                vehicle.renderPose(partialTick).transformPosition(hook);
            }
            Vec3 tug = new Vec3(origin.x + rx * side * s + fx * fwd, origin.y + up, origin.z + rz * side * s + fz * fwd);
            int hookLight = LevelRenderer.getLightColor(level, BlockPos.containing(hook.x, hook.y, hook.z));
            rope(poseStack, buffers, tug.subtract(origin), new Vec3(hook.x, hook.y, hook.z).subtract(origin),
                    animalLight, hookLight, fit.ropeColor());
        }
    }

    /** A lead-like rope between two points relative to the pose stack, sagging a little, in two crossed ribbons. */
    static void rope(PoseStack poseStack, MultiBufferSource buffers, Vec3 a, Vec3 b, int lightA, int lightB,
                     int color) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3) {
            return;
        }
        Vec3 dir = d.scale(1 / len);
        Vec3 across = dir.cross(new Vec3(0, 1, 0));
        across = across.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : across.normalize();
        Vec3 over = across.cross(dir).normalize();
        double sag = 0.04 * len;
        Matrix4f m = poseStack.last().pose();
        for (Vec3 off : new Vec3[] {across.scale(HALF_WIDTH), over.scale(HALF_WIDTH)}) {
            VertexConsumer vc = buffers.getBuffer(RenderType.leash());
            for (int i = 0; i <= SEGMENTS; i++) {
                float t = (float) i / SEGMENTS;
                Vec3 p = a.add(d.scale(t)).add(0, -sag * 4 * t * (1 - t), 0);
                int block = (int) Mth.lerp(t, LightTexture.block(lightA), LightTexture.block(lightB));
                int sky = (int) Mth.lerp(t, LightTexture.sky(lightA), LightTexture.sky(lightB));
                int light = LightTexture.pack(block, sky);
                float shade = i % 2 == 0 ? 1.0F : 0.78F;
                float r = ((color >> 16) & 0xff) / 255F * shade;
                float g = ((color >> 8) & 0xff) / 255F * shade;
                float bl = (color & 0xff) / 255F * shade;
                vc.addVertex(m, (float) (p.x + off.x), (float) (p.y + off.y), (float) (p.z + off.z))
                        .setColor(r, g, bl, 1).setLight(light);
                vc.addVertex(m, (float) (p.x - off.x), (float) (p.y - off.y), (float) (p.z - off.z))
                        .setColor(r, g, bl, 1).setLight(light);
            }
        }
    }
}
