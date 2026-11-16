package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.content.figure.FigureItem;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.math.Axis;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.figure.Figure;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws a statue or rug on its LOTR creature model in the rest pose; rugs lie flat, face down. */
public class FigureRenderer extends EntityRenderer<Figure> {
    public FigureRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(Figure figure, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        CreatureModel model = CreatureRenderer.model(figure.model());
        if (model == null) {
            return;
        }
        if (figure.item().getItem() instanceof FigureItem fi && fi.spec().has("rug")) {
            renderRug(figure, model, fi.spec().getAsJsonObject("rug"), pose, buffers, light);
            super.render(figure, yaw, partialTick, pose, buffers, light);
            return;
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - figure.getYRot()));
        if (figure.flat()) {
            // laid out on the floor: belly down, flattened like a skin
            pose.translate(0, 0.05, 0);
            pose.scale(1f, 0.12f, 1f);
        }
        pose.scale(-1f, -1f, 1f); // a living renderer's model frame
        pose.translate(0, -1.501, 0);
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(figure)));
        model.renderRest(pose, buffer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(figure, yaw, partialTick, pose, buffers, light);
    }

    /** LOTR's LOTRRenderRugBase + LOTRModel*Rug: the creature's parts laid out by the figure's {@code rug} groups. */
    private void renderRug(Figure figure, CreatureModel model, JsonObject rug, PoseStack pose,
            MultiBufferSource buffers, int light) {
        model.resetPose();
        // LOTR's rug legs: front pitched 30, back -20, all rolled flat to the sides
        float[][] legs = {{30, 90}, {30, -90}, {-20, 90}, {-20, -90}};
        for (int i = 0; i < 4; i++) {
            ModelPart leg = model.part("leg" + (i + 1));
            if (leg != null) {
                leg.xRot = (float) Math.toRadians(legs[i][0]);
                leg.zRot = (float) Math.toRadians(legs[i][1]);
            }
        }
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(figure)));
        pose.pushPose();
        pose.scale(-1f, -1f, 1f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - figure.getYRot()));
        float s = ContentData.num(rug, "scale", 1f);
        pose.scale(s, s, s);
        pose.translate(0, ContentData.num(rug, "y", 0f), 0);
        for (JsonElement e : rug.getAsJsonArray("groups")) {
            JsonObject g = e.getAsJsonObject();
            pose.pushPose();
            float[] t = vec(g, "t", 0f);
            float[] sc = vec(g, "s", 1f);
            pose.translate(t[0], t[1], t[2]);
            pose.scale(sc[0], sc[1], sc[2]);
            for (JsonElement p : g.getAsJsonArray("parts")) {
                ModelPart part = model.part(p.getAsString());
                if (part != null) {
                    part.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
                }
            }
            pose.popPose();
        }
        pose.popPose();
        model.resetPose();
    }

    private static float[] vec(JsonObject o, String key, float fallback) {
        if (!o.has(key)) {
            return new float[] {fallback, fallback, fallback};
        }
        var a = o.getAsJsonArray(key);
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    @Override
    public ResourceLocation getTextureLocation(Figure figure) {
        return LotrPort.id("textures/entity/" + figure.texture());
    }
}
