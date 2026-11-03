package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.console.ConsoleLayout;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the console (Aaron, 2026-11-03: like the original mod's): a plinth and column, six sloped panels, a bobbing time
 * rotor, and each control on it (button, lever, dial, monitor) where the synced control list puts it. Buttons sink
 * when pressed, levers flip, dials turn. Placeholder colours until Aaron's model and textures.
 */
public final class ConsoleRenderer implements BlockEntityRenderer<ConsoleBlockEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(TardisRB.id("console"), "main");
    private static final ResourceLocation TEXTURE = TardisRB.id("textures/entity/console.png");

    private final ModelPart root;

    public ConsoleRenderer(BlockEntityRendererProvider.Context context) {
        root = context.bakeLayer(LAYER);
    }

    /** The model, in pixels, y up, centred on the console block's floor. */
    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition top = mesh.getRoot();
        PartDefinition body = top.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6, 0, -6, 12, 2, 12)
                .texOffs(0, 16).addBox(-3, 2, -3, 6, 14, 6)
                .texOffs(0, 40).addBox(-5, 15, -5, 10, 1, 10), PartPose.ZERO);
        float mid = (float) ((ConsoleLayout.INNER + ConsoleLayout.OUTER) / 2 * 16);
        float midY = (float) ((ConsoleLayout.INNER_Y + ConsoleLayout.OUTER_Y) / 2 * 16);
        float tilt = (float) ConsoleLayout.tilt();
        for (int i = 0; i < 6; i++) {
            float yaw = (float) ConsoleLayout.yaw(i);
            float sin = (float) Math.sin(yaw);
            float cos = (float) Math.cos(yaw);
            body.addOrReplaceChild("panel" + i, CubeListBuilder.create().texOffs(64, 0)
                    .addBox(-7.5F, -1, -7.5F, 15, 1, 15), PartPose.offsetAndRotation(sin * mid, midY, cos * mid,
                    tilt, yaw, 0));
            body.addOrReplaceChild("strut" + i, CubeListBuilder.create().texOffs(96, 32)
                    .addBox(-1, 0, -1, 2, 12, 2), PartPose.offsetAndRotation(sin * 5, 1, cos * 5, -0.35F, yaw, 0));
        }
        top.addOrReplaceChild("rotor", CubeListBuilder.create().texOffs(64, 32).addBox(-2, 0, -2, 4, 12, 4),
                PartPose.ZERO);
        top.addOrReplaceChild("button", CubeListBuilder.create().texOffs(0, 64).addBox(-1.5F, 0, -1.5F, 3, 2, 3),
                PartPose.ZERO);
        PartDefinition lever = top.addOrReplaceChild("lever", CubeListBuilder.create().texOffs(32, 64)
                .addBox(-1.5F, 0, -2, 3, 1, 4), PartPose.ZERO);
        lever.addOrReplaceChild("handle", CubeListBuilder.create().texOffs(32, 80).addBox(-0.5F, 0, -0.5F, 1, 6, 1)
                .texOffs(48, 80).addBox(-1, 6, -1, 2, 2, 2), PartPose.offset(0, 1, 0));
        PartDefinition dial = top.addOrReplaceChild("dial", CubeListBuilder.create().texOffs(64, 64)
                .addBox(-2.5F, 0, -2.5F, 5, 1, 5), PartPose.ZERO);
        dial.addOrReplaceChild("pointer", CubeListBuilder.create().texOffs(96, 64).addBox(-0.5F, 0, -2.5F, 1, 1, 3),
                PartPose.offset(0, 1, 0));
        PartDefinition monitor = top.addOrReplaceChild("monitor", CubeListBuilder.create().texOffs(0, 96)
                .addBox(-4, 0, -1, 8, 7, 1), PartPose.ZERO);
        monitor.addOrReplaceChild("screen", CubeListBuilder.create().texOffs(32, 96).addBox(-3, 1, 0, 6, 5, 0.3F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void render(ConsoleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        float time = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + partialTick;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        root.getChild("body").render(pose, vc, light, overlay);
        ModelPart rotor = root.getChild("rotor");
        rotor.y = 17 + (float) Math.sin(time * 0.1) * 1.5F;
        rotor.render(pose, vc, light, overlay);
        pose.popPose();
        float tilt = (float) ConsoleLayout.tilt();
        for (ConsoleBlockEntity.Shown c : be.shown()) {
            Vec3 at = ConsoleLayout.point(c.panel(), c.u(), c.v());
            pose.pushPose();
            pose.translate(at.x, at.y - 0.04, at.z);
            pose.mulPose(Axis.YP.rotation((float) ConsoleLayout.yaw(c.panel())));
            pose.mulPose(Axis.XP.rotation(tilt));
            float since = time - be.usedAt(c.id());
            switch (c.kind()) {
                case "button" -> {
                    ModelPart b = root.getChild("button");
                    b.y = since >= 0 && since < 6 ? -1 : 0;
                    b.render(pose, vc, light, overlay);
                }
                case "lever" -> {
                    ModelPart l = root.getChild("lever");
                    l.getChild("handle").xRot = be.uses(c.id()) % 2 == 0 ? -0.6F : 0.6F;
                    l.render(pose, vc, light, overlay);
                }
                case "dial" -> {
                    ModelPart d = root.getChild("dial");
                    int turns = switch (c.action()) {
                        case "step" -> be.step();
                        case "destination" -> be.destination();
                        default -> be.uses(c.id());
                    };
                    d.getChild("pointer").yRot = turns * (float) (Math.PI / 2);
                    d.render(pose, vc, light, overlay);
                }
                case "monitor" -> {
                    pose.mulPose(Axis.XP.rotation(-tilt - 0.25F));
                    root.getChild("monitor").render(pose, vc, light, overlay);
                }
                default -> { }
            }
            pose.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(ConsoleBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.5, 1.0, 1.5);
    }
}
