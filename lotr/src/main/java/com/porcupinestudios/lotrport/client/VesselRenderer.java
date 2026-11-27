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
 * {@code converter/vessels.py}'s shapes (pixels: inner min, inner max, surface height, and for see-through glass
 * vessels the bottom: those draw the drink's sides too, so a bottle looks full rather than a floating surface).
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
        FILL.put("wine_glass_block", new float[] {6.25f, 9.75f, 7.2f, 4.25f});
        FILL.put("ale_horn_block", new float[] {7, 9, 9});
        FILL.put("ale_horn_gold", new float[] {7, 9, 9});
        FILL.put("glass_bottle", new float[] {5.25f, 10.75f, 6.6f, 0.25f});
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
        if (fill.length > 3) {
            float lo = fill[3] / 16f;
            float w0 = sprite.getV(1 - y);
            float w1 = sprite.getV(1 - lo);
            side(out, p, a, a, b, a, lo, y, u0, u1, w0, w1, color, light, 0, -1); // north
            side(out, p, b, b, a, b, lo, y, u0, u1, w0, w1, color, light, 0, 1); // south
            side(out, p, a, b, a, a, lo, y, u0, u1, w0, w1, color, light, -1, 0); // west
            side(out, p, b, a, b, b, lo, y, u0, u1, w0, w1, color, light, 1, 0); // east
        }
    }

    /** A wall of the drink from (x0, z0) to (x1, z1) facing (nx, nz); entityTranslucent draws both sides. */
    private static void side(VertexConsumer out, PoseStack.Pose p, float x0, float z0, float x1, float z1, float lo,
            float hi, float u0, float u1, float v0, float v1, int color, int light, int nx, int nz) {
        side(out, p, x0, hi, z0, u0, v0, color, light, nx, nz);
        side(out, p, x0, lo, z0, u0, v1, color, light, nx, nz);
        side(out, p, x1, lo, z1, u1, v1, color, light, nx, nz);
        side(out, p, x1, hi, z1, u1, v0, color, light, nx, nz);
    }

    private static void side(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, float u, float v,
            int color, int light, int nx, int nz) {
        out.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, nx, 0, nz);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, float u, float v,
            int color, int light) {
        out.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, 0, 1, 0);
    }
}
