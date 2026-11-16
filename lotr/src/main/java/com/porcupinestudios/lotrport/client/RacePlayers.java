package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrClientConfig;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.player.Races;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Draws a player who has a race with a {@code model} on LOTR's body for that race (the NPC models: hair layer, ears,
 * feet), in the race's skin, in place of the vanilla player model. First person keeps vanilla's arm.
 */
@EventBusSubscriber(modid = LotrPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RacePlayers {
    private static Renderer renderer;

    private RacePlayers() {}

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) {
        if (renderer == null) { // AddLayers fires again on every resource reload
            NeoForge.EVENT_BUS.addListener(RacePlayers::render);
        }
        renderer = new Renderer(event.getContext());
    }

    private static void render(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player) || renderer == null
                || !LotrClientConfig.RACE_SKINS.get()) {
            return;
        }
        String model = Races.CLIENT_MODELS.get(player.getUUID());
        ResourceLocation skin = Races.CLIENT_SKINS.get(player.getUUID());
        if (model == null || skin == null) {
            return;
        }
        event.setCanceled(true);
        renderer.draw(player, model, skin, event);
    }

    private static final class Renderer extends LivingEntityRenderer<AbstractClientPlayer,
            HumanoidModel<AbstractClientPlayer>> {
        private final Map<String, HumanoidModel<AbstractClientPlayer>> models = new HashMap<>();
        private ResourceLocation skin;

        Renderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(LotrClient.NPC_LAYER)), 0.5f);
            models.put("classic", model);
            for (String kind : NpcModels.KINDS) {
                models.put(kind, new HumanoidModel<>(context.bakeLayer(NpcModels.layer(kind))));
            }
            addLayer(new HumanoidArmorLayer<>(this,
                    new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                    context.getModelManager()));
            addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        }

        void draw(AbstractClientPlayer player, String kind, ResourceLocation skin, RenderPlayerEvent.Pre event) {
            this.skin = skin;
            model = models.getOrDefault(kind, models.get("human"));
            model.crouching = player.isCrouching();
            HumanoidModel.ArmPose main = pose(player, InteractionHand.MAIN_HAND);
            HumanoidModel.ArmPose off = pose(player, InteractionHand.OFF_HAND);
            boolean right = player.getMainArm() == HumanoidArm.RIGHT;
            model.rightArmPose = right ? main : off;
            model.leftArmPose = right ? off : main;
            float yaw = Mth.lerp(event.getPartialTick(), player.yRotO, player.getYRot());
            render(player, yaw, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight());
        }

        /** The vanilla player renderer's arm poses, short of its spyglass, horn and brush cases. */
        private static HumanoidModel.ArmPose pose(AbstractClientPlayer player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty()) {
                return HumanoidModel.ArmPose.EMPTY;
            }
            if (player.getUsedItemHand() == hand && player.getUseItemRemainingTicks() > 0) {
                switch (stack.getUseAnimation()) {
                    case BLOCK: return HumanoidModel.ArmPose.BLOCK;
                    case BOW: return HumanoidModel.ArmPose.BOW_AND_ARROW;
                    case SPEAR: return HumanoidModel.ArmPose.THROW_SPEAR;
                    case CROSSBOW: return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                    default: break;
                }
            }
            return HumanoidModel.ArmPose.ITEM;
        }

        /** PlayerRenderer's body tilts: elytra flight, and swimming or crawling (LivingEntityRenderer has neither). */
        @Override
        protected void setupRotations(AbstractClientPlayer player, com.mojang.blaze3d.vertex.PoseStack pose, float bob,
                float yBodyRot, float partialTick, float scale) {
            super.setupRotations(player, pose, bob, yBodyRot, partialTick, scale);
            float swim = player.getSwimAmount(partialTick);
            float xRot = player.getViewXRot(partialTick);
            if (player.isFallFlying()) {
                float t = player.getFallFlyingTicks() + partialTick;
                float lean = Mth.clamp(t * t / 100f, 0f, 1f);
                if (!player.isAutoSpinAttack()) {
                    pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(lean * (-90f - xRot)));
                }
                net.minecraft.world.phys.Vec3 look = player.getViewVector(partialTick);
                net.minecraft.world.phys.Vec3 move = player.getDeltaMovementLerped(partialTick);
                double m = move.horizontalDistanceSqr();
                double l = look.horizontalDistanceSqr();
                if (m > 0 && l > 0) {
                    double cos = (move.x * look.x + move.z * look.z) / Math.sqrt(m * l);
                    double side = move.x * look.z - move.z * look.x;
                    pose.mulPose(com.mojang.math.Axis.YP.rotation((float) (Math.signum(side) * Math.acos(cos))));
                }
            } else if (swim > 0f) {
                float target = player.isInWater() ? -90f - xRot : -90f;
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(Mth.lerp(swim, 0f, target)));
                if (player.isVisuallySwimming()) {
                    pose.translate(0f, -1f, 0.3f);
                }
            }
        }

        @Override
        public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
            return skin;
        }
    }
}
