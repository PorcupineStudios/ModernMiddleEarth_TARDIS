package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.porcupinestudios.lotrport.animal.AnimalType;
import com.porcupinestudios.lotrport.animal.LotrAnimal;
import com.porcupinestudios.lotrport.animal.LotrAnimals;
import com.porcupinestudios.lotrport.animal.LotrMount;
import com.porcupinestudios.lotrport.animal.MountArmorItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.layers.HorseArmorLayer;
import net.minecraft.client.renderer.entity.layers.HorseMarkingLayer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.Horse;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Draws LOTR's animals on their converted models ({@link AnimalModel}), with LOTR's skins, scale and saddles. */
public class AnimalRenderer extends MobRenderer<Mob, AnimalModel> {
    private final AnimalType type;

    AnimalRenderer(EntityRendererProvider.Context ctx, AnimalType type) {
        super(ctx, new AnimalModel(LotrAnimals.MODELS.get(type.model()), type), type.width() * 0.5f);
        this.type = type;
        if (type.saddle() != null) {
            addLayer(new RenderLayer<>(this) {
                @Override
                public void render(PoseStack pose, MultiBufferSource buffers, int light, Mob mob, float limbSwing,
                        float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                    if (mob instanceof LotrMount m && m.isSaddled()) {
                        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(
                                type.saddle())), light, OverlayTexture.NO_OVERLAY, -1);
                    }
                }
            });
        }
        if (type.armor() != null) { // LOTR's mount armor: its texture over the skin, on the same model
            addLayer(new RenderLayer<>(this) {
                @Override
                public void render(PoseStack pose, MultiBufferSource buffers, int light, Mob mob, float limbSwing,
                        float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                    if (mob.getBodyArmorItem().getItem() instanceof MountArmorItem armor && armor.armorTexture() != null) {
                        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(
                                armor.armorTexture())), light, OverlayTexture.NO_OVERLAY, -1);
                    }
                }
            });
        }
    }

    @Override
    public ResourceLocation getTextureLocation(Mob mob) {
        int skin = mob instanceof LotrAnimal a ? a.skin() : mob instanceof LotrMount m ? m.skin() : 0;
        return type.skins().get(Math.floorMod(skin, type.skins().size()));
    }

    @Override
    protected void scale(Mob mob, PoseStack pose, float partialTick) {
        float s = type.scale() * (mob.isBaby() ? 0.5f : 1f);
        pose.scale(s, s, s);
    }

    @SuppressWarnings("unchecked")
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        for (AnimalType t : LotrAnimals.TYPES.values()) {
            EntityType<?> et = LotrAnimals.ENTITY.get(t.id()).get();
            if (t.kind().equals("pony")) {
                event.registerEntityRenderer((EntityType<Horse>) et, ctx -> new PonyRenderer(ctx, t.scale()));
            } else if (t.model() != null && LotrAnimals.MODELS.containsKey(t.model()) && !t.skins().isEmpty()) {
                event.registerEntityRenderer((EntityType<Mob>) et, ctx -> new AnimalRenderer(ctx, t));
            }
        }
    }

    /** The Shire pony: vanilla's horse renderer (coats, markings, armor) at LOTR's pony scale. */
    static final class PonyRenderer extends AbstractHorseRenderer<Horse, HorseModel<Horse>> {
        PonyRenderer(EntityRendererProvider.Context ctx, float scale) {
            super(ctx, new HorseModel<>(ctx.bakeLayer(ModelLayers.HORSE)), 1.1f * scale);
            addLayer(new HorseMarkingLayer(this));
            addLayer(new HorseArmorLayer(this, ctx.getModelSet()));
        }

        @Override
        public ResourceLocation getTextureLocation(Horse horse) {
            return ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_"
                    + horse.getVariant().name().toLowerCase(java.util.Locale.ROOT).replace("_", "") + ".png");
        }
    }
}
