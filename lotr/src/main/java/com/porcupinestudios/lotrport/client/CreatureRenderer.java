package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/**
 * Draws NPCs with a creature body ({@link CreatureModel}) the way LOTR's renderer for them did: its
 * preRenderCallback (size, lean, fade), the model's own draw calls, then the renderer's extra passes: a troll's
 * outfit or armour (shirt and trousers models with the outfit texture the troll rolled), its weapon from the troll
 * weapons texture, and the rock in a mountain troll's hand while it winds up a throw. Which of LOTR's renderers an
 * NPC uses is its state "renderer".
 */
public class CreatureRenderer extends MobRenderer<LotrNpc, CreatureModel> {
    private static final Map<String, CreatureModel> MODELS = new HashMap<>();
    private final NpcRenderer owner;
    private int rendererIndex;

    public CreatureRenderer(EntityRendererProvider.Context context, NpcRenderer owner) {
        super(context, null, 0.5f);
        this.owner = owner;
        addLayer(new Layers(this));
    }

    static CreatureModel model(String id) {
        return MODELS.computeIfAbsent(id, CreatureRenderer::load);
    }

    /** Draws it; false if its model can't be found (the caller falls back to the humanoid). */
    public boolean draw(LotrNpc npc, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        CreatureModel m = model(npc.model());
        if (m == null) {
            return false;
        }
        model = m;
        rendererIndex = (int) npc.state("state.renderer");
        shadowRadius = npc.getBbWidth() * 0.5f;
        super.render(npc, yaw, partialTick, pose, buffers, light);
        return true;
    }

    private static CreatureModel load(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return null;
        }
        ResourceLocation file = rl.withPath("npc_model/" + rl.getPath() + ".json");
        return Minecraft.getInstance().getResourceManager().getResource(file).map(r -> {
            try (Reader in = r.openAsReader()) {
                JsonObject def = JsonParser.parseReader(in).getAsJsonObject();
                return new CreatureModel(def);
            } catch (Exception e) {
                LogUtils.getLogger().warn("Could not build creature model {}", file, e);
                return null;
            }
        }).orElse(null);
    }

    @Override
    protected void scale(LotrNpc npc, PoseStack pose, float partialTick) {
        model.ctx.npc = npc;
        model.ctx.partial = partialTick;
        model.ctx.swing = model.attackTime;
        model.applyPre(rendererIndex, pose);
    }

    @Override
    protected RenderType getRenderType(LotrNpc npc, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (bodyVisible && model.translucent(rendererIndex)) {
            return RenderType.entityTranslucent(getTextureLocation(npc));
        }
        return super.getRenderType(npc, bodyVisible, translucent, glowing);
    }

    @Override
    public ResourceLocation getTextureLocation(LotrNpc npc) {
        if (npc.state("is.LOTREntityStoneTroll") > 0) {
            return ResourceLocation.fromNamespaceAndPath("lotr_port", "textures/entity/troll/stone.png");
        }
        return owner.getTextureLocation(npc);
    }

    /** The renderer's extra passes ({@code renderers[i].layers} in the model file). */
    private static final class Layers extends RenderLayer<LotrNpc, CreatureModel> {
        private final CreatureRenderer renderer;

        Layers(CreatureRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, LotrNpc npc, float limbSwing,
                float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            CreatureModel main = getParentModel();
            int index = renderer.rendererIndex;
            if (index < 0 || index >= main.renderers.size() || npc.state("is.LOTREntityStoneTroll") > 0) {
                return;
            }
            CreatureModel.Renderer r = main.renderers.get(index);
            int overlay = LivingEntityRenderer.getOverlayCoords(npc, 0f);
            for (int i = 0; i < r.layers().size(); i++) {
                JsonObject layer = r.layers().get(i);
                if (layer.has("model")) {
                    CreatureModel outfit = model("lotr_port:" + layer.get("model").getAsString());
                    ResourceLocation tex = pick(layer, npc);
                    if (outfit == null || tex == null) {
                        continue;
                    }
                    outfit.attackTime = main.attackTime;
                    outfit.setupAnim(npc, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
                    outfit.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, overlay,
                            -1);
                } else if (layer.has("rock_when")) {
                    if (npc.state(layer.get("rock_when").getAsString()) > 0 && main.attackTime <= 0) {
                        ModelPart arm = main.part(layer.get("attach").getAsString());
                        pose.pushPose();
                        if (arm != null) {
                            arm.translateAndRotate(pose);
                        }
                        JsonArray t = layer.getAsJsonArray("translate");
                        pose.translate(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat());
                        pose.mulPose(Axis.YP.rotationDegrees(layer.getAsJsonArray("rotate").get(0).getAsFloat()));
                        pose.translate(-0.5, -0.5, -0.5);
                        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                                Blocks.COBBLESTONE.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
                        pose.popPose();
                    }
                } else if (layer.has("draws")) {
                    if (layer.has("hide_when") && npc.state(layer.get("hide_when").getAsString()) > 0
                            && main.attackTime <= 0) {
                        continue;
                    }
                    ResourceLocation tex = pick(layer, npc);
                    List<CreatureModel.Draw> draws = r.layerDraws().get(i);
                    if (tex != null && !draws.isEmpty()) {
                        main.renderDraws(draws, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light,
                                overlay, -1);
                    }
                }
            }
        }

        /** The layer's texture for this NPC: the outfit or armour it rolled ({@code outfitSkin}). */
        private static ResourceLocation pick(JsonObject layer, LotrNpc npc) {
            JsonArray textures = layer.getAsJsonArray("textures");
            if (textures == null || textures.isEmpty()) {
                return null;
            }
            int i = Math.floorMod((int) npc.state("outfitSkin"), textures.size());
            return ResourceLocation.tryParse(textures.get(i).getAsString());
        }
    }
}
