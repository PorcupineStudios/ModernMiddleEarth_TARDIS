package com.porcupinestudios.ringbearer.api.client.bugreport;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * In-game bug reporter: the key (F7 by default, works in menus too) or the pause menu's button grabs the current frame
 * and opens {@link BugReportScreen}. The pause button first closes the menu and waits two frames so the shot shows
 * the game, not the menu.
 */
public final class BugReportClient {
    public static final KeyMapping KEY = new KeyMapping("key.ringbearer_api.bug_report", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7, "key.categories.misc");
    private static int framesUntilShot;

    private BugReportClient() {}

    public static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, BugReportConfig.SPEC);
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(KEY));
        NeoForge.EVENT_BUS.addListener(BugReportClient::onKey);
        NeoForge.EVENT_BUS.addListener(BugReportClient::onPauseInit);
        NeoForge.EVENT_BUS.addListener(BugReportClient::onFrame);
    }

    private static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getAction() == GLFW.GLFW_PRESS && KEY.matches(e.getKey(), e.getScanCode())
                && !(mc.screen instanceof BugReportScreen) && !(mc.screen instanceof ConfirmScreen)) {
            open(mc.screen);
        }
    }

    private static void onPauseInit(ScreenEvent.Init.Post e) {
        if (e.getScreen() instanceof PauseScreen pause && pause.showsPauseMenu()) {
            e.addListener(Button.builder(Component.translatable("gui.ringbearer_api.bug_report.button"), b -> {
                Minecraft.getInstance().setScreen(null);
                framesUntilShot = 2;
            }).bounds(4, 4, 90, 20).build());
        }
    }

    private static void onFrame(RenderFrameEvent.Post e) {
        if (framesUntilShot > 0 && --framesUntilShot == 0) {
            open(null);
        }
    }

    private static void open(Screen previous) {
        Minecraft mc = Minecraft.getInstance();
        NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        int max = BugReportConfig.SCREENSHOT_MAX_WIDTH.get();
        if (shot.getWidth() > max) {
            NativeImage small = new NativeImage(max, shot.getHeight() * max / shot.getWidth(), false);
            shot.resizeSubRectTo(0, 0, shot.getWidth(), shot.getHeight(), small);
            shot.close();
            shot = small;
        }
        NativeImage taken = shot;
        // the player agrees to what gets sent (public issue, screenshot, cleaned logs) before the form opens
        mc.setScreen(new ConfirmScreen(agreed -> {
            if (agreed) {
                mc.setScreen(new BugReportScreen(previous, taken));
            } else {
                taken.close();
                mc.setScreen(previous);
            }
        }, Component.translatable("gui.ringbearer_api.bug_report.disclaimer.title"),
                Component.translatable("gui.ringbearer_api.bug_report.disclaimer"),
                Component.translatable("gui.ringbearer_api.bug_report.disclaimer.agree"), CommonComponents.GUI_CANCEL));
    }
}
