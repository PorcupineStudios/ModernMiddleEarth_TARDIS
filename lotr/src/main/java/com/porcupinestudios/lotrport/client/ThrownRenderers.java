package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.porcupinestudios.lotrport.content.thrown.OrcBomb;
import com.porcupinestudios.lotrport.content.thrown.Thrown;
import com.porcupinestudios.lotrport.content.thrown.ThrownWeapon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Renderers for LOTR's thrown things: LOTRRenderSpear, ThrowingAxe, Dart and OrcBomb's frames, in 1.21 terms. */
public final class ThrownRenderers {
    private ThrownRenderers() {}

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Thrown.WEAPON.get(), Weapon::new);
        event.registerEntityRenderer(Thrown.MISSILE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(Thrown.ORC_BOMB.get(), Bomb::new);
    }

    /**
     * A spear, axe or dart. LOTR: turned to the flight (yaw - 90 about y, pitch about z), arrow shake, -135 about z so
     * the icon's diagonal points along the flight; a spear 1 back with its large in-hand icon, a dart at 0.6 size,
     * an axe spinning (its pitch is the spin) half a block up. 1.7.10's flat icon spans 0..1 mirrored, the 1.21
     * item model -0.5..0.5, hence the shift and mirror before drawing.
     */
    static class Weapon extends EntityRenderer<ThrownWeapon> {
        Weapon(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(ThrownWeapon e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                int light) {
            ItemStack stack = e.item();
            if (stack.isEmpty()) {
                return;
            }
            pose.pushPose();
            float yRot = Mth.lerp(partial, e.yRotO, e.getYRot());
            float xRot = Mth.lerp(partial, e.xRotO, e.getXRot());
            ThrownWeapon.Kind kind = e.kind();
            if (kind == ThrownWeapon.Kind.AXE) {
                pose.translate(0, 0.5, 0);
                pose.mulPose(Axis.YP.rotationDegrees(yRot - 90f));
                float spin = e.isInGround() ? 0f
                        : (e.tickCount + partial) / Thrown.num("axe_spin_ticks", 9) * 360f;
                pose.mulPose(Axis.ZN.rotationDegrees(spin));
                pose.mulPose(Axis.ZP.rotationDegrees(-90f));
                pose.translate(0, 0.75, 0);
            } else {
                pose.mulPose(Axis.YP.rotationDegrees(yRot - 90f));
                pose.mulPose(Axis.ZP.rotationDegrees(xRot));
            }
            float shake = e.shakeTime - partial;
            if (shake > 0f) {
                pose.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(shake * 3f) * shake));
            }
            pose.mulPose(Axis.ZP.rotationDegrees(-135f));
            if (kind != ThrownWeapon.Kind.AXE) {
                pose.translate(0, -1, 0);
            }
            if (kind == ThrownWeapon.Kind.DART) {
                pose.scale(0.6f, 0.6f, 0.6f);
                pose.translate(0, 0.8, 0);
            }
            pose.translate(0.5, 0.5, -1 / 32f);
            pose.scale(-1f, 1f, 1f);
            var renderer = Minecraft.getInstance().getItemRenderer();
            BakedModel model = renderer.getModel(stack, e.level(), null, e.getId());
            if (kind == ThrownWeapon.Kind.SPEAR) {
                // LOTRRenderLargeItem: the spear flies as its large in-hand icon
                model = model.applyTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, new PoseStack(), false);
            }
            renderer.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, OverlayTexture.NO_OVERLAY,
                    model);
            pose.popPose();
            super.render(e, yaw, partial, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(ThrownWeapon e) {
            return TextureAtlas.LOCATION_BLOCKS;
        }
    }

    /** A lit orc bomb, as primed TNT: swells and flashes white before it goes (LOTRRenderOrcBomb). */
    static class Bomb extends EntityRenderer<OrcBomb> {
        private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;

        Bomb(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0.5f;
            blocks = context.getBlockRenderDispatcher();
        }

        @Override
        public void render(OrcBomb e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            pose.translate(0, 0.5, 0);
            int fuse = e.fuse();
            if (fuse - partial + 1f < 10f) {
                float f = 1f - (fuse - partial + 1f) / 10f;
                f = Mth.clamp(f, 0f, 1f);
                f *= f;
                f *= f;
                float s = 1f + f * 0.3f;
                pose.scale(s, s, s);
            }
            pose.mulPose(Axis.YP.rotationDegrees(-90f));
            pose.translate(-0.5, -0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(90f));
            TntMinecartRenderer.renderWhiteSolidBlock(blocks, e.block(), pose, buffers, light, fuse / 5 % 2 == 0);
            pose.popPose();
            super.render(e, yaw, partial, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(OrcBomb e) {
            return TextureAtlas.LOCATION_BLOCKS;
        }
    }
}
