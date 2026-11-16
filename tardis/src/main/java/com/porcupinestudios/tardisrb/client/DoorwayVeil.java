package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlockEntity;
import com.porcupinestudios.tardisrb.entity.Dematerialising;
import com.porcupinestudios.tardisrb.flight.Materialise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Fades an open doorway with its (de)materialising box (Aaron 2026-11-16): a light drawn over the door portal, as
 * opaque as the box is see-through, so the view through the doorway phases out with the box and the portal goes (or
 * comes) only while fully covered. Works on both sides: the outside portal finds the box beside it, the console
 * room's portal the box at the far end.
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class DoorwayVeil {
    private DoorwayVeil() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Portal portal)) {
                continue;
            }
            float box = boxAlpha(mc.level, portal.getOriginPos(), pt);
            if (box < 0) {
                Level far = ClientWorldLoader.getWorld(portal.getDestDim());
                box = far == null ? -1 : boxAlpha(far, portal.getDestPos(), pt);
            }
            if (box < 0 || box >= 0.997f) {
                continue;
            }
            draw(pose, buffers.getBuffer(RenderType.debugQuads()), portal, cam, 1 - box);
            drew = true;
        }
        if (drew) {
            buffers.endBatch(RenderType.debugQuads());
        }
    }

    /** How solid the fading box at a doorway {@code at} is (0..1), or -1 if there is none fading there. */
    private static float boxAlpha(Level level, Vec3 at, float pt) {
        for (Dematerialising ghost : level.getEntitiesOfClass(Dematerialising.class, new AABB(at, at).inflate(1.5))) {
            return Materialise.alpha(ghost.progress(pt), true);
        }
        float time = level.getGameTime() + pt;
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(at.add(-1, -2, -1)),
                BlockPos.containing(at.add(1, 1, 1)))) {
            if (level.getBlockEntity(p) instanceof ExteriorBlockEntity be) {
                float t = be.materialised(time);
                return t >= 1 ? -1 : Materialise.alpha(t, false);
            }
        }
        return -1;
    }

    private static void draw(PoseStack pose, VertexConsumer vc, Portal portal, Vec3 cam, float alpha) {
        Vec3 c = portal.getOriginPos().add(portal.getNormal().scale(0.02)).subtract(cam);
        Vec3 w = portal.getAxisW().scale(portal.getWidth() / 2);
        Vec3 h = portal.getAxisH().scale(portal.getHeight() / 2);
        int a = Math.round(Math.min(1, alpha) * 255);
        Vec3[] corners = {c.subtract(w).subtract(h), c.add(w).subtract(h), c.add(w).add(h), c.subtract(w).add(h)};
        for (Vec3 v : corners) {
            // the soft blue-white of the dematerialisation light
            vc.addVertex(pose.last(), (float) v.x, (float) v.y, (float) v.z).setColor(150, 185, 235, a);
        }
    }
}
