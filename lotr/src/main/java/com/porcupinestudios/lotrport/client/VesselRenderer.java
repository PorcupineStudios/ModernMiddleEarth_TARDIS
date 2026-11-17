package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.lotrport.drink.Drinks;
import com.porcupinestudios.lotrport.drink.VesselBlockEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The drink inside a set-down vessel (LOTR drew the liquid's colour in the mug's top): a still-water surface tinted
 * the drink's colour, at the vessel's fill line. Empty vessels draw nothing. Fill lines match
 * {@code converter/vessels.py}'s shapes (pixels: inner min, inner max, surface height).
 */
public final class VesselRenderer implements BlockEntityRenderer<VesselBlockEntity> {
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final Map<String, float[]> FILL = new HashMap<>();

    static {
        float[] mug = {6, 10, 6};
        float[] goblet = {6, 10, 9};
        FILL.put("mug_block", mug);
        FILL.put("ceramic_mug_block", mug);
        FILL.put("goblet_gold", goblet);
        FILL.put("goblet_silver", goblet);
        FILL.put("goblet_copper", goblet);
        FILL.put("goblet_wood", goblet);
        FILL.put("skull_cup_block", new float[] {6, 10, 5});
        FILL.put("wine_glass_block", new float[] {7, 9, 7});
        FILL.put("ale_horn_block", new float[] {7, 9, 9});
        FILL.put("ale_horn_gold", new float[] {7, 9, 9});
        FILL.put("glass_bottle", new float[] {6, 10, 5});
    }

    @Override
    public void render(VesselBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        ItemStack held = be.held();
        if (held.isEmpty()) {
            return;
        }
        Drinks.DrinkFluidEntry drink = Drinks.BY_ITEM.get(BuiltInRegistries.ITEM.getKey(held.getItem()));
        float[] fill = FILL.get(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath());
        if (drink == null || fill == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
        float a = fill[0] / 16f;
        float b = fill[1] / 16f;
        float y = fill[2] / 16f;
        float u0 = sprite.getU(fill[0] / 16f);
        float u1 = sprite.getU(fill[1] / 16f);
        float v0 = sprite.getV(fill[0] / 16f);
        float v1 = sprite.getV(fill[1] / 16f);
        int color = 0xE0000000 | (drink.color() & 0xFFFFFF);
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        vertex(out, p, a, y, a, u0, v0, color, light);
        vertex(out, p, a, y, b, u0, v1, color, light);
        vertex(out, p, b, y, b, u1, v1, color, light);
        vertex(out, p, b, y, a, u1, v0, color, light);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, float u, float v,
            int color, int light) {
        out.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, 0, 1, 0);
    }
}
