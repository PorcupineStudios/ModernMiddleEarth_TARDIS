package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.porcupinestudios.lotrport.npc.RiggedNpc;
import com.porcupinestudios.lotrport.rig.RigDef;
import com.porcupinestudios.lotrport.rig.RigPose;
import com.porcupinestudios.lotrport.rig.RigSkeleton;
import com.porcupinestudios.lotrport.rig.RigState;
import com.porcupinestudios.lotrport.rig.RigVoxels;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a {@link RiggedNpc}: each bone's boxes under the matrix its rig pose gives (legs reaching for the feet the rig
 * planted), in the NPC's skin. Also the client side of footfalls: step sound, dust and, for heavy creatures, a camera
 * shake that fades with distance.
 */
public final class RigRenderer {
    /** IK and ground feet within this many blocks; further out legs keep their rest pose. */
    private static final double IK_RANGE = 64;
    private static final Map<RigSkeleton, ModelPart[]> MESHES = new IdentityHashMap<>();
    private static final Map<RigSkeleton, TextureAtlasSprite[][]> TILES = new IdentityHashMap<>();
    private static float shake;
    private static float shakePrev;

    private RigRenderer() {}

    public static void init() {
        RiggedNpc.clientFootfall = RigRenderer::footfall;
        NeoForge.EVENT_BUS.addListener(RigRenderer::camera);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> {
            shakePrev = shake;
            shake *= 0.8f;
        });
    }

    /** Draws it; false when it has no rig on this side (the caller falls back to the creature model). */
    public static boolean draw(RiggedNpc npc, ResourceLocation texture, float partial, PoseStack stack,
            MultiBufferSource buffers, int light) {
        RigSkeleton sk = npc.skeleton();
        RigState state = npc.rigState();
        RigPose pose = npc.renderPose();
        if (sk == null || state == null || pose == null) {
            return false;
        }
        RigState.Body body = npc.body(partial);
        float u = sk.def.hide().isPresent() ? npc.hideProgress(partial) : 0;
        if (u > RiggedNpc.CROUCH) { // settling into (or breaking out of) its blocks
            float v = (u - RiggedNpc.CROUCH) / (1 - RiggedNpc.CROUCH);
            drawVoxels(sk, body.yaw, v >= 1 ? 1 : 0.55f + 0.45f * v * v * (3 - 2 * v), stack, buffers, light);
            if (v >= 1) {
                return true;
            }
        }
        double dist = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(body.x, body.y, body.z);
        boolean ik = npc.isAlive() && dist < IK_RANGE * IK_RANGE;
        pose.pose(state, body, partial, ik);
        ModelPart[] parts = MESHES.computeIfAbsent(sk, RigRenderer::bake);
        TextureAtlasSprite[][] tiles = TILES.computeIfAbsent(sk, RigRenderer::tiles);

        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(-body.yaw));
        if (npc.deathTime > 0) { // tip over as vanilla's dying mobs do
            float f = Mth.sqrt((npc.deathTime + partial - 1f) / 20f * 1.6f);
            stack.mulPose(Axis.ZP.rotationDegrees(Math.min(f, 1f) * 90f));
        }
        stack.scale(body.scale, body.scale, body.scale);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        VertexConsumer blockVc = null;
        int overlay = LivingEntityRenderer.getOverlayCoords(npc, 0f);
        float tile = 16f / body.scale; // model pixels a block's texture covers, so it tiles at real block size
        for (int b = 0; b < sk.count; b++) {
            if (parts[b] == null && tiles[b] == null) {
                continue;
            }
            stack.pushPose();
            Matrix4f m = pose.bones[b];
            stack.last().pose().mul(m);
            stack.last().normal().mul(new Matrix3f(m).normal());
            stack.scale(1, -1, -1); // model pixels: y down, head to -z
            if (parts[b] != null) {
                parts[b].render(stack, vc, light, overlay, -1);
            }
            if (tiles[b] != null) {
                if (blockVc == null) {
                    blockVc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
                }
                List<RigDef.Cube> cubes = sk.bones[b].cubes();
                for (int c = 0; c < cubes.size(); c++) {
                    if (tiles[b][c] != null) {
                        tiledCube(cubes.get(c), tiles[b][c], tile, stack.last(), blockVc, light, overlay);
                    }
                }
            }
            stack.popPose();
        }
        stack.popPose();
        return true;
    }

    /**
     * A box in a block's texture, repeated every {@code tile} model pixels from its corner, so it shows at the same
     * size as the blocks around it (and as the blocks the creature hides as).
     */
    private static void tiledCube(RigDef.Cube c, TextureAtlasSprite sprite, float tile, PoseStack.Pose pose,
            VertexConsumer vc, int light, int overlay) {
        float g = c.inflate();
        float[] lo = {c.origin().get(0) - g, c.origin().get(1) - g, c.origin().get(2) - g};
        float[] hi = {lo[0] + c.size().get(0) + 2 * g, lo[1] + c.size().get(1) + 2 * g, lo[2] + c.size().get(2) + 2 * g};
        for (int axis = 0; axis < 3; axis++) {
            int ua = axis == 0 ? 2 : 0; // the face's across axis
            int va = axis == 1 ? 2 : 1; // its up-down axis (y on the sides)
            for (int side = 0; side < 2; side++) {
                float at = side == 0 ? lo[axis] : hi[axis];
                float n = side == 0 ? -1 : 1;
                for (float u0 = lo[ua]; u0 < hi[ua] - 1e-4f; u0 += tile) {
                    float u1 = Math.min(hi[ua], u0 + tile);
                    for (float v0 = lo[va]; v0 < hi[va] - 1e-4f; v0 += tile) {
                        float v1 = Math.min(hi[va], v0 + tile);
                        float su0 = sprite.getU0();
                        float sw = sprite.getU1() - su0;
                        float sv0 = sprite.getV0();
                        float sh = sprite.getV1() - sv0;
                        float tu1 = su0 + sw * (u1 - u0) / tile;
                        float tv1 = sv0 + sh * (v1 - v0) / tile;
                        float[][] corners = {{u0, v0, su0, sv0}, {u1, v0, tu1, sv0}, {u1, v1, tu1, tv1},
                            {u0, v1, su0, tv1}};
                        for (float[] k : corners) {
                            float[] p = new float[3];
                            p[axis] = at;
                            p[ua] = k[0];
                            p[va] = k[1];
                            vc.addVertex(pose, p[0] / 16f, p[1] / 16f, p[2] / 16f).setColor(-1).setUv(k[2], k[3])
                                    .setOverlay(overlay).setLight(light)
                                    .setNormal(pose, axis == 0 ? n : 0, axis == 1 ? n : 0, axis == 2 ? n : 0);
                        }
                    }
                }
            }
        }
    }

    /** Per bone and box, the block texture it tiles (null where it uses the skin). */
    private static TextureAtlasSprite[][] tiles(RigSkeleton sk) {
        var shapes = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper();
        TextureAtlasSprite[][] out = new TextureAtlasSprite[sk.count][];
        for (int b = 0; b < sk.count; b++) {
            List<RigDef.Cube> cubes = sk.bones[b].cubes();
            for (int c = 0; c < cubes.size(); c++) {
                if (cubes.get(c).block().isPresent()) {
                    if (out[b] == null) {
                        out[b] = new TextureAtlasSprite[cubes.size()];
                    }
                    out[b][c] = shapes.getParticleIcon(RiggedNpc.block(cubes.get(c).block().get()));
                }
            }
        }
        return out;
    }

    /**
     * The blocks it hides as, on the block grid (it stands on a block centre, a quarter turn), each scaled about its
     * centre by {@code grow} (the settle into blocks grows them from inside the crouched body).
     */
    private static void drawVoxels(RigSkeleton sk, float yaw, float grow, PoseStack stack, MultiBufferSource buffers,
            int light) {
        RigVoxels vox = sk.voxels();
        BlockState[] states = new BlockState[vox.blocks.size()];
        for (int i = 0; i < states.length; i++) {
            states[i] = RiggedNpc.block(vox.blocks.get(i));
        }
        var blocks = Minecraft.getInstance().getBlockRenderer();
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(-Math.round(yaw / 90f) * 90f));
        for (int[] c : vox.cells) {
            stack.pushPose();
            stack.translate(c[0], c[1] + 0.5, c[2]);
            stack.scale(grow, grow, grow);
            stack.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(states[c[3]], stack, buffers, light, OverlayTexture.NO_OVERLAY);
            stack.popPose();
        }
        stack.popPose();
    }

    /** One model part per bone holding its boxes (relative to the bone pivot, as written). */
    private static ModelPart[] bake(RigSkeleton sk) {
        MeshDefinition mesh = new MeshDefinition();
        for (int b = 0; b < sk.count; b++) {
            List<RigDef.Cube> cubes = sk.bones[b].cubes().stream().filter(c -> c.block().isEmpty()).toList();
            if (cubes.isEmpty()) {
                continue;
            }
            CubeListBuilder list = CubeListBuilder.create();
            for (RigDef.Cube c : cubes) {
                list.texOffs(c.uv().get(0), c.uv().get(1)).mirror(c.mirror())
                        .addBox(c.origin().get(0), c.origin().get(1), c.origin().get(2), c.size().get(0),
                                c.size().get(1), c.size().get(2), new CubeDeformation(c.inflate()));
            }
            mesh.getRoot().addOrReplaceChild("b" + b, list, PartPose.ZERO);
        }
        List<Integer> tex = sk.def.texture();
        ModelPart root = LayerDefinition.create(mesh, tex.get(0), tex.size() > 1 ? tex.get(1) : tex.get(0))
                .bakeRoot();
        ModelPart[] out = new ModelPart[sk.count];
        for (int b = 0; b < sk.count; b++) {
            if (root.hasChild("b" + b)) {
                out[b] = root.getChild("b" + b);
            }
        }
        return out;
    }

    private static void footfall(RiggedNpc.Footfall f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        BlockPos below = BlockPos.containing(f.x(), f.y() - 0.05, f.z());
        BlockState ground = mc.level.getBlockState(below);
        if (ground.isAir()) {
            return;
        }
        float volume = Math.min(2f, 0.15f + 0.25f * f.scale() * (1 + f.heavy()));
        float pitch = Math.max(0.4f, 1.1f - 0.12f * f.scale() - 0.3f * f.heavy());
        var sound = ground.getSoundType(mc.level, below, f.npc()).getStepSound();
        mc.level.playLocalSound(f.x(), f.y(), f.z(), sound, SoundSource.HOSTILE, volume, pitch, false);
        int dust = (int) (f.heavy() * 8 + (f.scale() > 2 ? 2 : 0));
        for (int i = 0; i < dust; i++) {
            double a = mc.level.random.nextDouble() * Math.PI * 2;
            double r = 0.2 * f.scale();
            mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), f.x() + Math.cos(a) * r,
                    f.y() + 0.1, f.z() + Math.sin(a) * r, Math.cos(a) * 0.1, 0.05, Math.sin(a) * 0.1);
        }
        Player p = mc.player;
        if (f.heavy() > 0 && p != null) {
            double d = p.distanceToSqr(f.x(), f.y(), f.z());
            double reach = 6 + 10 * f.heavy() * f.scale();
            if (d < reach * reach) {
                shake = Math.max(shake, (float) (f.heavy() * (1 - Math.sqrt(d) / reach)));
            }
        }
    }

    private static void camera(ViewportEvent.ComputeCameraAngles event) {
        float s = Mth.lerp((float) event.getPartialTick(), shakePrev, shake);
        if (s < 0.01f) {
            return;
        }
        float t = (float) (System.nanoTime() / 1e9 * 30);
        event.setPitch(event.getPitch() + Mth.sin(t) * s * 1.2f);
        event.setRoll(event.getRoll() + Mth.cos(t * 1.3f) * s * 0.8f);
    }
}
