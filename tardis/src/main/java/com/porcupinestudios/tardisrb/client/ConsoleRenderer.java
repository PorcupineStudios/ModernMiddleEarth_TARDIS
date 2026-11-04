package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Capaldi console room's models (Aaron, 2026-11-04): the console with its pedestal down to the lower deck,
 * the time rotor (glass and column bob, the ceiling rings turn opposite ways) and the arches. The room's walls and
 * floors are real blocks. Each control is drawn only once it is on the console (repaired); a used control sinks
 * into its panel for a moment and levers and switches stay over while flipped. The glowing bits get a second,
 * full-bright pass.
 */
public final class ConsoleRenderer implements BlockEntityRenderer<ConsoleBlockEntity> {
    private static final ResourceLocation TEXTURE = TardisRB.id("textures/entity/capaldi/console.png");
    private static final ResourceLocation GLOW = TardisRB.id("textures/entity/capaldi/console_glow.png");
    private static final List<String> STATIC = List.of("body", "arches");

    public ConsoleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ConsoleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        ConsoleModel model = ConsoleModel.get();
        float time = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + partialTick;
        // two passes: the textured model lit by the world, then its glowing bits at full brightness
        for (int pass = 0; pass < 2; pass++) {
            boolean glow = pass == 1;
            int l = glow ? LightTexture.FULL_BRIGHT : light;
            VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(glow ? GLOW : TEXTURE));
            for (String part : STATIC) {
                draw(model.part(part), pose, solid, l);
            }
            for (ConsoleBlockEntity.Shown c : be.shown()) {
                ConsoleModel.ControlPart ctl = model.controls.get(c.part());
                if (ctl == null) {
                    continue;
                }
                pose.pushPose();
                animate(be, c, ctl, model, time, pose);
                draw(model.part("ctl_" + c.part()), pose, solid, l);
                pose.popPose();
            }
            // the rotor: the ceiling rings turn opposite ways, the column rises and falls inside the glass
            spin(model, "rotor_spinners", time * 0.6F, pose, solid, l);
            spin(model, "rotor_spinner_mid", -time * 0.9F, pose, solid, l);
            pose.pushPose();
            pose.translate(0, Math.sin(time * 0.08) * 0.12, 0);
            draw(model.part("rotor_column"), pose, solid, l);
            pose.popPose();
            VertexConsumer glass = buffers.getBuffer(glow ? RenderType.entityCutoutNoCull(GLOW)
                    : RenderType.entityTranslucent(TEXTURE));
            draw(model.part("rotor_glass"), pose, glass, l);
        }
    }

    private static void spin(ConsoleModel model, String part, float degrees, PoseStack pose, VertexConsumer vc,
            int light) {
        pose.pushPose();
        pose.translate(model.pivotX, 0, model.pivotZ);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.translate(-model.pivotX, 0, -model.pivotZ);
        draw(model.part(part), pose, vc, light);
        pose.popPose();
    }

    /** A used control sinks a little into its panel; levers and switches lean over while flipped. */
    private static void animate(ConsoleBlockEntity be, ConsoleBlockEntity.Shown c, ConsoleModel.ControlPart ctl,
            ConsoleModel model, float time, PoseStack pose) {
        Vec3 centre = ctl.box().getCenter();
        // the way out of the panel: away from the rotor axis and up
        Vec3 out = new Vec3(centre.x - model.pivotX, 0, centre.z - model.pivotZ).normalize().add(0, 1.4, 0)
                .normalize();
        float since = time - be.usedAt(c.id());
        if (since >= 0 && since < 6) {
            Vec3 in = out.scale(-0.03 * (1 - since / 6));
            pose.translate(in.x, in.y, in.z);
        }
        boolean flips = c.kind().equals("lever") || c.kind().equals("switch");
        if (flips && be.uses(c.id()) % 2 == 1) {
            // lean over about the control's base, round the panel's horizontal axis
            Vec3 axis = new Vec3(-(centre.z - model.pivotZ), 0, centre.x - model.pivotX).normalize();
            pose.translate(centre.x, ctl.box().minY, centre.z);
            pose.mulPose(Axis.of(axis.toVector3f()).rotationDegrees(-35));
            pose.translate(-centre.x, -ctl.box().minY, -centre.z);
        }
    }

    private static void draw(List<ConsoleModel.Quad> quads, PoseStack pose, VertexConsumer vc, int light) {
        PoseStack.Pose p = pose.last();
        for (ConsoleModel.Quad q : quads) {
            for (int i = 0; i < 4; i++) {
                float[] v = q.v()[i];
                vc.addVertex(p, v[0], v[1], v[2]).setColor(255, 255, 255, 255).setUv(v[3], v[4])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(p, q.n()[0], q.n()[1], q.n()[2]);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ConsoleBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(ConsoleBlockEntity be) {
        // the arches span the whole room and the pedestal reaches the lower deck
        return new AABB(be.getBlockPos()).inflate(12, 0, 12).expandTowards(0, 8, 0).expandTowards(0, -4, 0);
    }
}
