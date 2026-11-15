package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.porcupinestudios.lotrport.land.ClaimBanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** A planted banner: the banner item's picture, large and upright, facing the way it was planted. */
public class ClaimBannerRenderer extends EntityRenderer<ClaimBanner> {
    public ClaimBannerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ClaimBanner banner, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
            int light) {
        pose.pushPose();
        pose.translate(0, 1.3, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-banner.getYRot()));
        pose.scale(2.6f, 2.6f, 2.6f);
        Minecraft.getInstance().getItemRenderer().renderStatic(banner.item(), ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, banner.level(), banner.getId());
        pose.popPose();
        super.render(banner, yaw, partial, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(ClaimBanner banner) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
