package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.entity.PilotCamera;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.flight.PilotStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * The pilot's health and energy bars (Aaron 2026-11-19), bottom left while flying the box or watching from it: the
 * TARDIS's health and its charge against the charge cap, from {@link PilotStatus}. Out of charge it says so (the box
 * flies on, slowly and drifting). Placeholder look until Aaron's art.
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class PilotHud {
    private static final int W = 120;
    private static final int H = 7;

    private PilotHud() {}

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui
                || !(mc.player.getVehicle() instanceof TardisBody || mc.player.getVehicle() instanceof PilotCamera)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int x = 8;
        int y = g.guiHeight() - 46;
        bar(g, font, x, y, "Health", PilotStatus.health, PilotStatus.maxHealth, 0xFFD04040);
        bar(g, font, x, y + 18, "Energy", PilotStatus.charge, PilotStatus.chargeCap, 0xFF40C0F0);
        if (PilotStatus.empty()) {
            g.drawString(font, Component.translatableWithFallback("tardis_rb.hud.empty",
                    "Out of artron: thrusters failing"), x, y - 12, 0xFFFF6060);
        } else if (PilotStatus.camera) {
            g.drawString(font, Component.translatableWithFallback("tardis_rb.hud.camera",
                    "Set down (G to fly again)"), x, y - 12, 0xFFE0E0A0);
        }
    }

    private static void bar(GuiGraphics g, Font font, int x, int y, String label, double value, double max,
            int colour) {
        double f = max <= 0 ? 0 : Math.max(0, Math.min(1, value / max));
        g.drawString(font, Component.translatableWithFallback("tardis_rb.hud." + label.toLowerCase(), label)
                .getString() + "  " + Math.round(value) + " / " + Math.round(max), x, y, 0xFFFFFFFF);
        g.fill(x, y + 9, x + W, y + 9 + H, 0xA0000000);
        g.fill(x + 1, y + 10, x + 1 + (int) ((W - 2) * f), y + 9 + H - 1, colour);
    }
}
