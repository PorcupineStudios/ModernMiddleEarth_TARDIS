package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.progression.Control;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the Capaldi console room's models (Aaron, 2026-11-04): the console with its pedestal down to the lower deck,
 * the time rotor (glass and column bob, the ceiling rings turn opposite ways) and the arches. The room's walls and
 * floors are real blocks. The console's body is one plain colour (Aaron, 2026-11-08: a blank slate) with the small
 * control models standing on its six panels ({@link ControlModels}): each is drawn only once it is on the console
 * (repaired), posed from the console's state (levers over while on, dials at their notch, buttons pressing, gauges at
 * their value) with its lamps in a second, full-bright pass. The monitor stands over the side it was last turned to
 * and shows that side's readout, like the 1.7.10 mod's monitor.
 */
public final class ConsoleRenderer implements BlockEntityRenderer<ConsoleBlockEntity> {
    private static final ResourceLocation TEXTURE = TardisRB.id("textures/entity/capaldi/console.png");
    private static final ResourceLocation GLOW = TardisRB.id("textures/entity/capaldi/console_glow.png");
    private static final ResourceLocation PLAIN = TardisRB.id("textures/entity/capaldi/console_plain.png");

    private static final int TEXT_COLOUR = 0xFF9FE8FF;
    private final Font font;

    public ConsoleRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(ConsoleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        ConsoleModel model = ConsoleModel.get();
        float time = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + partialTick;
        // the monitor glides round the rotor toward the angle it was last turned to
        float target = model.monitorTurn(be.monitorAngle());
        if (Float.isNaN(be.drawnAngle)) {
            be.drawnAngle = target;
        }
        be.drawnAngle += ConsoleModel.wrap(target - be.drawnAngle) * 0.12F;
        Matrix4f monitor = model.monitorMatrix(be.drawnAngle);
        boolean hasMonitor = be.shown().stream().anyMatch(c -> c.model().equals(Control.MONITOR));

        // the plain body, then the arches and rotor with the room's texture and their glowing bits
        draw(model.part("body"), pose, buffers.getBuffer(RenderType.entityCutoutNoCull(PLAIN)), light);
        for (int pass = 0; pass < 2; pass++) {
            boolean glow = pass == 1;
            int l = glow ? LightTexture.FULL_BRIGHT : light;
            VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(glow ? GLOW : TEXTURE));
            draw(model.part("arches"), pose, solid, l);
            if (hasMonitor) {
                pose.pushPose();
                mul(pose, monitor);
                draw(model.part("ctl_" + ConsoleModel.MONITOR), pose, solid, l);
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
        // the small controls on the panels
        for (ConsoleBlockEntity.Shown c : be.shown()) {
            Optional<ControlModels.Model> m = ControlModels.get(c.model());
            Matrix4f place = c.model().equals(Control.MONITOR) ? null : model.placement(c);
            if (m.isEmpty() || place == null) {
                continue;
            }
            ControlModels.Pose posed = ControlModels.pose(m.get(), be, c, time);
            pose.pushPose();
            mul(pose, place);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ControlModels.TEXTURE));
            for (Map.Entry<String, ControlModels.Part> p : m.get().parts().entrySet()) {
                pose.pushPose();
                mul(pose, posed.of(p.getKey()));
                draw(p.getValue().quads(), pose, vc, light);
                pose.popPose();
            }
            VertexConsumer lamps = buffers.getBuffer(RenderType.entityCutoutNoCull(ControlModels.GLOW));
            for (Map.Entry<String, ControlModels.Part> p : m.get().parts().entrySet()) {
                if (!posed.lit().contains(p.getKey())) {
                    continue;
                }
                pose.pushPose();
                mul(pose, posed.of(p.getKey()));
                draw(p.getValue().quads(), pose, lamps, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
            pose.popPose();
        }
        // text last: asking for the font's buffer ends the batches above
        if (hasMonitor) {
            pose.pushPose();
            mul(pose, monitor);
            readout(be, model, pose, buffers);
            pose.popPose();
        }
    }

    /** Multiplies the pose (and its normals) by a rigid transform with an even scale. */
    private static void mul(PoseStack pose, Matrix4f m) {
        pose.last().pose().mul(m);
        pose.last().normal().mul(new Matrix3f(m).normal());
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

    /** What the console knows (for the side the monitor stands over), written on the monitor's glass, sized to fit. */
    private void readout(ConsoleBlockEntity be, ConsoleModel model, PoseStack pose, MultiBufferSource buffers) {
        List<String> lines = be.readout();
        if (model.glass.isEmpty() || lines.isEmpty()) {
            return;
        }
        ConsoleModel.Glass g = model.glass.get();
        int widest = 1;
        for (String line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        float k = (float) Math.min(g.height() * 0.85 / (lines.size() * 10), g.width() * 0.85 / widest);
        pose.pushPose();
        Vec3 c = g.centre().add(g.normal().scale(0.004));
        pose.translate(c.x, c.y, c.z);
        Vec3 r = g.right();
        Vec3 u = g.up();
        Vec3 n = g.normal();
        Matrix4f basis = new Matrix4f((float) r.x, (float) r.y, (float) r.z, 0, (float) u.x, (float) u.y,
                (float) u.z, 0, (float) n.x, (float) n.y, (float) n.z, 0, 0, 0, 0, 1);
        pose.last().pose().mul(basis);
        pose.last().normal().mul(new Matrix3f(basis));
        pose.scale(k, -k, k);
        float left = -widest / 2F;
        float top = -lines.size() * 10 / 2F;
        for (int i = 0; i < lines.size(); i++) {
            font.drawInBatch(lines.get(i), left, top + i * 10, TEXT_COLOUR, false, pose.last().pose(), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
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
