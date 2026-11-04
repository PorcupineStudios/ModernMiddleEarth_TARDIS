package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/** Draws an NPC with its skin, held items and armour; its size comes from the scale attribute. */
public class NpcRenderer extends HumanoidMobRenderer<LotrNpc, HumanoidModel<LotrNpc>> {
    private static final ResourceLocation FALLBACK =
            ResourceLocation.fromNamespaceAndPath("lotr_port", "textures/entity/npc_fallback.png");
    /** Speech is drawn within this many blocks, wrapped at this many pixels. */
    private static final double SPEECH_RANGE = 24;
    private static final int SPEECH_WIDTH = 160;
    private static final ResourceLocation QUEST_MARK =
            ResourceLocation.fromNamespaceAndPath("lotr_port", "textures/gui/quest/quest_offer.png");
    /** The quest mark shows within this many blocks, this many blocks wide (as LOTR's). */
    private static final double MARK_RANGE = 16;
    private static final float MARK_SIZE = 0.75f;
    private final HumanoidModel<LotrNpc> classic;
    private final Map<String, HumanoidModel<LotrNpc>> tall = new HashMap<>();

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(LotrClient.NPC_LAYER)), 0.5f);
        classic = model;
        for (String kind : NpcModels.KINDS) {
            tall.put(kind, new HumanoidModel<>(context.bakeLayer(NpcModels.layer(kind))));
        }
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public void render(LotrNpc npc, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        // 64x64 skins use LOTR's body for the race (hair layer, feet, ears); 64x32 ones the classic humanoid
        model = npc.tallSkin() ? tall.getOrDefault(npc.model(), tall.get("human")) : classic;
        super.render(npc, yaw, partialTick, pose, buffers, light);
        renderSpeech(npc, pose, buffers, light);
        renderQuestMark(npc, pose, buffers);
    }

    /**
     * LOTR's quest mark: its quest-offer icon above the head of an NPC offering a quest, tinted with the faction
     * colour, facing the camera, within 16 blocks (higher when the NPC is talking, so it clears the speech).
     */
    private void renderQuestMark(LotrNpc npc, PoseStack pose, MultiBufferSource buffers) {
        int color = npc.questOfferColor();
        if (color == -1 || entityRenderDispatcher.distanceToSqr(npc) > MARK_RANGE * MARK_RANGE) {
            return;
        }
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        boolean talking = !npc.speech().getString().isEmpty();
        pose.pushPose();
        pose.translate(0, npc.getBbHeight() + (talking ? 1.25 : 0.6), 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(-MARK_SIZE, -MARK_SIZE, MARK_SIZE);
        Matrix4f m = pose.last().pose();
        VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(QUEST_MARK));
        int full = LightTexture.FULL_BRIGHT;
        v.addVertex(m, -0.5f, -0.5f, 0).setColor(r, g, b, 1f).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(full).setNormal(0, 1, 0);
        v.addVertex(m, 0.5f, -0.5f, 0).setColor(r, g, b, 1f).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(full).setNormal(0, 1, 0);
        v.addVertex(m, 0.5f, 0.5f, 0).setColor(r, g, b, 1f).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(full).setNormal(0, 1, 0);
        v.addVertex(m, -0.5f, 0.5f, 0).setColor(r, g, b, 1f).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(full).setNormal(0, 1, 0);
        pose.popPose();
    }

    /** Draws what the NPC is saying above its head, wrapped like a speech bubble, facing the camera. */
    private void renderSpeech(LotrNpc npc, PoseStack pose, MultiBufferSource buffers, int light) {
        Component line = npc.speech();
        if (line.getString().isEmpty() || entityRenderDispatcher.distanceToSqr(npc) > SPEECH_RANGE * SPEECH_RANGE) {
            return;
        }
        Font font = getFont();
        List<FormattedCharSequence> rows = font.split(line, SPEECH_WIDTH);
        pose.pushPose();
        pose.translate(0, npc.getBbHeight() + 0.75, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(0.025f, -0.025f, 0.025f);
        Matrix4f matrix = pose.last().pose();
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255) << 24;
        for (int i = 0; i < rows.size(); i++) {
            FormattedCharSequence row = rows.get(i);
            float x = -font.width(row) / 2f;
            float y = (i - rows.size() + 1) * (font.lineHeight + 1);
            font.drawInBatch(row, x, y, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, background,
                    light);
        }
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(LotrNpc npc) {
        ResourceLocation skin = npc.skin().isEmpty() ? null : ResourceLocation.tryParse(npc.skin());
        return skin == null ? FALLBACK : skin;
    }
}
