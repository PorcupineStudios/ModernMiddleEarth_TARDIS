package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.porcupinestudios.lotrport.mount.LotrHorse;
import com.porcupinestudios.lotrport.mount.MountArmorItem;
import com.porcupinestudios.lotrport.mount.Mounts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.WoolCarpetBlock;

/**
 * Draws LOTR's mounts ({@link LotrHorse}): the breed's body in the texture it rolled, a horse's markings, its
 * saddle, its armour (LOTR's mount armour, or vanilla horse armour in its 1.7.10 texture) and a camel's carpet.
 */
public class MountRenderer extends MobRenderer<LotrHorse, MountModel> {
    private static final ResourceLocation MISSING = ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png");
    private static final Map<String, MountModel> MODELS = new HashMap<>();

    public MountRenderer(EntityRendererProvider.Context context) {
        super(context, null, 0.7f);
        addLayer(new Extras(this));
    }

    private static MountModel model(Mounts.Breed breed) {
        return MODELS.computeIfAbsent(breed.id(), id -> {
            CreatureModel body = CreatureRenderer.model(breed.model());
            return body == null ? null : new MountModel(body, breed.oldHorse());
        });
    }

    @Override
    public void render(LotrHorse horse, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        Mounts.Breed breed = horse.breed();
        MountModel m = model(breed);
        if (m == null) {
            return;
        }
        model = m;
        shadowRadius = breed.width() * 0.5f * (horse.isBaby() ? 0.5f : 1f);
        super.render(horse, yaw, partialTick, pose, buffers, light);
    }

    @Override
    protected void scale(LotrHorse horse, PoseStack pose, float partialTick) {
        Mounts.Breed breed = horse.breed();
        float s = breed.scale();
        if (horse.isBaby() && (model.oldHorse || !model.body.sizesItsYoung())) {
            s *= 0.5f; // LOTR's own models shrink their young themselves
        }
        pose.scale(s, s, s);
        model.body.ctx.npc = horse;
        model.body.ctx.partial = partialTick;
        model.body.applyPre(0, pose);
    }

    @Override
    public ResourceLocation getTextureLocation(LotrHorse horse) {
        List<ResourceLocation> textures = horse.breed().textures();
        return textures.isEmpty() ? MISSING : textures.get(Math.floorMod(horse.textureIndex(), textures.size()));
    }

    /** What lies over the body: markings, saddle, armour, carpet. */
    private static final class Extras extends RenderLayer<LotrHorse, MountModel> {
        Extras(MountRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, LotrHorse horse, float limbSwing,
                float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            MountModel main = getParentModel();
            Mounts.Breed breed = horse.breed();
            int overlay = LivingEntityRenderer.getOverlayCoords(horse, 0f);
            ItemStack armour = horse.getBodyArmorItem();
            if (main.oldHorse) {
                int marking = horse.markingIndex();
                if (marking >= 0 && marking < breed.markings().size()) {
                    main.renderHorse(pose, buffers.getBuffer(RenderType.entityTranslucent(breed.markings().get(marking))),
                            light, overlay, -1, false);
                }
                ResourceLocation worn = armourTexture(armour);
                if (worn != null) {
                    main.renderHorse(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(worn)), light, overlay, -1,
                            false);
                }
                return;
            }
            if (breed.saddleModel() == null) {
                return;
            }
            CreatureModel over = CreatureRenderer.model(breed.saddleModel());
            if (over == null) {
                return;
            }
            over.attackTime = main.attackTime;
            over.ctx.partial = partialTick;
            over.setupAnimFor(horse, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            if (horse.isSaddled() && breed.saddle() != null) {
                over.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(breed.saddle())), light, overlay,
                        -1);
            }
            if (armour.getItem() instanceof BlockItem block && block.getBlock() instanceof WoolCarpetBlock carpet
                    && breed.carpet().size() >= 2) {
                DyeColor dye = carpet.getColor();
                over.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(breed.carpet().get(0))), light,
                        overlay, -1);
                over.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(breed.carpet().get(1))), light,
                        overlay, dye.getTextureDiffuseColor());
            } else {
                ResourceLocation worn = armourTexture(armour);
                if (worn != null) {
                    over.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(worn)), light, overlay, -1);
                }
            }
        }

        private static ResourceLocation armourTexture(ItemStack armour) {
            if (armour.getItem() instanceof MountArmorItem item) {
                return item.texture();
            }
            return armour.isEmpty() ? null : Mounts.VANILLA_ARMOUR.get(BuiltInRegistries.ITEM.getKey(armour.getItem()));
        }
    }
}
