package com.porcupinestudios.ringbearer.harness.client;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client entry: the Config button in the Mods list opens NeoForge's config screen; the worn harness is drawn on
 * horse-family animals ({@link HarnessLayer}) and fitted on others ({@link BodyHarness}); hitched animals get their two ropes ({@link RopeRenderer}).
 */
@Mod(value = HarnessMod.MOD_ID, dist = Dist.CLIENT)
public final class HarnessClient {
    public HarnessClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> {
            e.registerLayerDefinition(HarnessModel.LAYER, HarnessModel::create);
            e.registerLayerDefinition(WagonWheelModel.LAYER, WagonWheelModel::create);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(HarnessMod.WAGON_WHEEL_BE.get(), WagonWheelRenderer::new));
        modBus.addListener(HarnessClient::addLayers);
        modBus.addListener(PartPoses::onProcess);
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(new HarnessFits()));
        com.porcupinestudios.ringbearer.harness.content.Harnessing.clientFits = HarnessFits::has;
        NeoForge.EVENT_BUS.addListener(HarnessClient::harnessSlotFrame);
        DriverClient.register();
        ShowcaseCamera.register();
        MovieRecorder.register();
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Post<?, ?> e) -> {
            RopeRenderer.render(e.getEntity(), e.getPartialTick(), e.getPoseStack(), e.getMultiBufferSource(),
                    e.getPackedLight());
            BodyHarness.render(e.getEntity(), e.getRenderer(), e.getPartialTick(), e.getPoseStack(),
                    e.getMultiBufferSource(), e.getPackedLight());
        });
    }

    private static final net.minecraft.resources.ResourceLocation SLOT_FRAME =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("container/slot");
    private static final net.minecraft.resources.ResourceLocation SLOT_ICON = HarnessMod.id("textures/slot/harness.png");

    /** The horse screen's background has no frame where the harness slot sits (under the armour): draw one. */
    private static void harnessSlotFrame(net.neoforged.neoforge.client.event.ContainerScreenEvent.Render.Background e) {
        if (!(e.getContainerScreen() instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen screen)) {
            return;
        }
        for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
            if (slot instanceof com.porcupinestudios.ringbearer.harness.content.HarnessSlot && slot.isActive()) {
                int x = screen.getGuiLeft() + slot.x, y = screen.getGuiTop() + slot.y;
                e.getGuiGraphics().blitSprite(SLOT_FRAME, x - 1, y - 1, 18, 18);
                if (!slot.hasItem()) {
                    e.getGuiGraphics().blit(SLOT_ICON, x, y, 0, 0, 16, 16, 16, 16);
                }
            }
        }
    }

    /** Every horse-family renderer (horses, donkeys, mules, skeleton and zombie horses) gets the harness layer. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (EntityType<?> type : e.getEntityTypes()) {
            EntityRenderer<?> r = e.getRenderer(type);
            if (r instanceof AbstractHorseRenderer horse) {
                horse.addLayer(new HarnessLayer(horse, e.getContext().bakeLayer(HarnessModel.LAYER)));
            }
        }
    }
}
