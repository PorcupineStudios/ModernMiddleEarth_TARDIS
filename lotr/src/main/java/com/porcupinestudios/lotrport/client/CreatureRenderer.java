package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws NPCs with a creature body ({@link CreatureModel}): the model named by the NPC's type, at LOTR's render
 * scale, with the NPC's skin. Used by {@link NpcRenderer} for any type whose model is a resource id.
 */
public class CreatureRenderer extends MobRenderer<LotrNpc, CreatureModel> {
    private final Map<String, CreatureModel> models = new HashMap<>();
    private final NpcRenderer owner;

    public CreatureRenderer(EntityRendererProvider.Context context, NpcRenderer owner) {
        super(context, null, 0.5f);
        this.owner = owner;
    }

    /** Draws it; false if its model can't be found (the caller falls back to the humanoid). */
    public boolean draw(LotrNpc npc, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        CreatureModel m = models.computeIfAbsent(npc.model(), CreatureRenderer::load);
        if (m == null) {
            return false;
        }
        model = m;
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
        float s = npc.renderScale();
        pose.scale(s, s, s);
    }

    @Override
    public ResourceLocation getTextureLocation(LotrNpc npc) {
        return owner.getTextureLocation(npc);
    }
}
