package com.porcupinestudios.ringbearer.api.client.bugreport;

import com.mojang.blaze3d.platform.NativeImage;
import com.porcupinestudios.ringbearer.api.RingBearerApi;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Title, description and a preview of the screenshot (which the player may leave out), then Send. */
public final class BugReportScreen extends Screen {
    private static final int W = 320;
    private final Screen previous;
    private final DynamicTexture shot;
    private final ResourceLocation shotId;
    private EditBox summary;
    private MultiLineEditBox description;
    private Checkbox attach;
    private Button send;
    private Component status = Component.translatable("gui.ringbearer_api.bug_report.public");
    private boolean sending;

    public BugReportScreen(Screen previous, NativeImage screenshot) {
        super(Component.translatable("gui.ringbearer_api.bug_report.title"));
        this.previous = previous;
        this.shot = new DynamicTexture(screenshot);
        this.shotId = RingBearerApi.id("bug_report_shot");
        net.minecraft.client.Minecraft.getInstance().getTextureManager().register(shotId, shot);
    }

    @Override
    protected void init() {
        int x = (width - W) / 2;
        String oldTitle = summary == null ? "" : summary.getValue();
        String oldText = description == null ? "" : description.getValue();
        summary = addRenderableWidget(new EditBox(font, x, 22, W, 18, Component.translatable("gui.ringbearer_api.bug_report.summary")));
        summary.setMaxLength(120);
        summary.setHint(Component.translatable("gui.ringbearer_api.bug_report.summary"));
        summary.setValue(oldTitle);
        summary.setResponder(s -> updateSend());
        description = addRenderableWidget(new MultiLineEditBox(font, x, 44, W, 72,
                Component.translatable("gui.ringbearer_api.bug_report.description"), Component.empty()));
        description.setCharacterLimit(8000);
        description.setValue(oldText);
        attach = addRenderableWidget(Checkbox.builder(Component.translatable("gui.ringbearer_api.bug_report.attach"), font)
                .pos(x + 116, 140).selected(attach == null || attach.selected()).build());
        send = addRenderableWidget(Button.builder(Component.translatable("gui.ringbearer_api.bug_report.send"), b -> send())
                .bounds(x, 196, W / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x + W / 2 + 2, 196, W / 2 - 2, 20).build());
        setInitialFocus(summary);
        updateSend();
    }

    private void updateSend() {
        send.active = !sending && !summary.getValue().isBlank();
    }

    private void send() {
        byte[] png = null;
        if (attach.selected()) {
            try {
                png = shot.getPixels().asByteArray();
            } catch (IOException e) {
                RingBearerApi.LOG.warn("Bug report: could not encode screenshot", e);
            }
        }
        sending = true;
        updateSend();
        status = Component.translatable("gui.ringbearer_api.bug_report.sending");
        String openScreen = previous == null ? null : previous.getClass().getName();
        BugReport.send(summary.getValue().strip(), description.getValue().strip(), png, openScreen)
                .whenComplete((url, err) -> minecraft.execute(() -> {
                    sending = false;
                    if (err != null) {
                        Throwable cause = err.getCause() != null ? err.getCause() : err;
                        RingBearerApi.LOG.warn("Bug report failed", cause);
                        status = Component.translatable("gui.ringbearer_api.bug_report.failed", String.valueOf(cause.getMessage()));
                        updateSend();
                        return;
                    }
                    minecraft.keyboardHandler.setClipboard(url);
                    SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            Component.translatable("gui.ringbearer_api.bug_report.sent"), Component.literal(url));
                    if (minecraft.screen == this) {
                        onClose();
                    }
                }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = (width - W) / 2;
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        NativeImage px = shot.getPixels();
        int h = 64;
        int w = Math.min(110, px.getWidth() * h / px.getHeight());
        if (attach.selected()) {
            g.blit(shotId, x, 122, w, h, 0, 0, px.getWidth(), px.getHeight(), px.getWidth(), px.getHeight());
        } else {
            g.fill(x, 122, x + w, 122 + h, 0x80000000);
        }
        g.drawWordWrap(font, status, x + 116, 162, W - 116, sending ? 0xAAAAAA : 0xCCCCCC);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previous);
    }

    @Override
    public void removed() {
        minecraft.getTextureManager().release(shotId); // also frees the screenshot's pixels
    }
}
