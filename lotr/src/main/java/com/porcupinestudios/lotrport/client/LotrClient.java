package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.net.MapNet;
import com.porcupinestudios.lotrport.net.QuestNet;
import com.porcupinestudios.lotrport.npc.LotrEntities;
import com.porcupinestudios.lotrport.content.LotrBowItem;
import com.porcupinestudios.lotrport.content.LotrCrossbowItem;
import com.porcupinestudios.lotrport.content.LotrItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Client setup: entity renderers, model layers, the map key. Never referenced from common code. */
@EventBusSubscriber(modid = LotrPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LotrClient {
    /** LOTR people: the classic humanoid, for 64x32 skins and for 64x64 ones. */
    public static final ModelLayerLocation NPC_LAYER = new ModelLayerLocation(LotrPort.id("npc"), "main");
    public static final ModelLayerLocation NPC_TALL_LAYER = new ModelLayerLocation(LotrPort.id("npc"), "tall");

    /** Opens the Middle-earth map (M, as LOTR's map is reached from the Red Book). */
    public static final KeyMapping MAP_KEY = new KeyMapping("key.lotr_port.map", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M, "key.categories.lotr_port");

    /** On the map screen: teleport to the block under the cursor (operators and creative players), as LOTR's. */
    public static final KeyMapping MAP_TELEPORT_KEY = new KeyMapping("key.lotr_port.map_teleport",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_T, "key.categories.lotr_port");

    /** On the map screen: fast travel to the selected waypoint, as LOTR's. */
    public static final KeyMapping FAST_TRAVEL_KEY = new KeyMapping("key.lotr_port.fast_travel",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.categories.lotr_port");

    private LotrClient() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        MapNet.clientHandler = (data, ctx) -> MapScreen.open(data);
        QuestNet.offerHandler = QuestOfferScreen::open;
        QuestNet.bookHandler = RedBookScreen::show;
        event.enqueueWork(LotrClient::rangedProperties);
    }

    @SubscribeEvent
    public static void clientExtensions(RegisterClientExtensionsEvent event) {
        ArmorModels.register(event);
    }

    /**
     * Bow and crossbow pull frames: vanilla registers its "pulling"/"pull"/"charged" predicates only for its own bow
     * and crossbow. A LOTR bow's pull runs over its own draw time.
     */
    private static void rangedProperties() {
        ResourceLocation pulling = ResourceLocation.withDefaultNamespace("pulling");
        ResourceLocation pull = ResourceLocation.withDefaultNamespace("pull");
        ResourceLocation charged = ResourceLocation.withDefaultNamespace("charged");
        for (var holder : LotrItems.BY_ID.values()) {
            Item item = holder.get();
            if (item instanceof LotrBowItem bow) {
                ItemProperties.register(item, pulling,
                        (stack, level, e, seed) -> e != null && e.isUsingItem() && e.getUseItem() == stack ? 1f : 0f);
                ItemProperties.register(item, pull, (stack, level, e, seed) -> e == null || e.getUseItem() != stack ? 0f
                        : (stack.getUseDuration(e) - e.getUseItemRemainingTicks()) / (float) bow.drawTime());
            } else if (item instanceof LotrCrossbowItem) {
                ItemProperties.register(item, pulling, (stack, level, e, seed) -> e != null && e.isUsingItem()
                        && e.getUseItem() == stack && !CrossbowItem.isCharged(stack) ? 1f : 0f);
                ItemProperties.register(item, pull, (stack, level, e, seed) -> e == null || CrossbowItem.isCharged(stack)
                        ? 0f : (stack.getUseDuration(e) - e.getUseItemRemainingTicks())
                                / (float) CrossbowItem.getChargeDuration(stack, e));
                ItemProperties.register(item, charged,
                        (stack, level, e, seed) -> CrossbowItem.isCharged(stack) ? 1f : 0f);
            }
        }
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(MAP_KEY);
        event.register(MAP_TELEPORT_KEY);
        event.register(FAST_TRAVEL_KEY);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NPC_LAYER,
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 32));
        event.registerLayerDefinition(NPC_TALL_LAYER,
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64));
        for (String kind : NpcModels.KINDS) {
            event.registerLayerDefinition(NpcModels.layer(kind), () -> NpcModels.tall(kind));
        }
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(LotrEntities.NPC.get(), NpcRenderer::new);
    }
}
