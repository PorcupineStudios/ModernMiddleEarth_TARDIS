package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.net.ConsoleNet;
import com.porcupinestudios.tardisrb.net.WorkshopNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The wreck's flight console: where the TARDIS is, its charge and hop range, and X/Y/Z boxes with a Hop button, and
 * a destination picker with a Jump button that lands near the same X/Y/Z in the chosen dimension. A
 * placeholder screen until Aaron's console art and the repaired controls come in.
 */
public final class ConsoleScreen extends Screen {
    private ConsoleNet.ConsoleView view;
    private EditBox x;
    private EditBox y;
    private EditBox z;
    private Button hop;
    private Button destination;
    private Button jump;
    private int picked;

    private ConsoleScreen(ConsoleNet.ConsoleView view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    /** Opens the screen, or refreshes it (keeping typed numbers) if it is already open. */
    public static void show(ConsoleNet.ConsoleView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ConsoleScreen open) {
            open.view = view;
            open.hop.active = view.canHop();
            open.refreshDestinations();
        } else {
            mc.setScreen(new ConsoleScreen(view));
        }
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int top = height / 2 - 20;
        x = box(cx - 95, top, String.valueOf(view.pos().getX()));
        y = box(cx - 30, top, String.valueOf(view.pos().getY()));
        z = box(cx + 35, top, String.valueOf(view.pos().getZ()));
        hop = addRenderableWidget(Button.builder(Component.translatableWithFallback("tardis_rb.console.hop", "Hop"),
                b -> send()).bounds(cx - 40, top + 28, 80, 20).build());
        hop.active = view.canHop();
        destination = addRenderableWidget(Button.builder(Component.empty(), b -> {
            picked++;
            refreshDestinations();
        }).bounds(cx - 150, top + 76, 200, 20).build());
        jump = addRenderableWidget(Button.builder(Component.translatableWithFallback("tardis_rb.console.jump",
                "Jump"), b -> sendJump()).bounds(cx + 55, top + 76, 95, 20).build());
        refreshDestinations();
        addRenderableWidget(Button.builder(Component.translatableWithFallback("tardis_rb.console.workshop",
                "Repairs & research"), b -> PacketDistributor.sendToServer(new WorkshopNet.OpenWorkshop()))
                .bounds(cx - 70, top + 52, 140, 20).build());
    }

    private void refreshDestinations() {
        destination.setMessage(destinationLabel());
        destination.active = view.destinations().size() > 1;
        jump.active = !view.destinations().isEmpty();
    }

    private Component destinationLabel() {
        if (view.destinations().isEmpty()) {
            return Component.translatableWithFallback("tardis_rb.console.no_destinations", "No destinations open");
        }
        ResourceLocation id = view.destinations().get(Math.floorMod(picked, view.destinations().size()));
        return Component.translatableWithFallback("tardis_rb.console.destination", "To: %s",
                Component.translatableWithFallback("tardis_rb.destination." + id.getNamespace() + "." + id.getPath(),
                        id.getPath()));
    }

    private void sendJump() {
        if (view.destinations().isEmpty()) {
            return;
        }
        try {
            PacketDistributor.sendToServer(new ConsoleNet.Jump(
                    view.destinations().get(Math.floorMod(picked, view.destinations().size())),
                    Integer.parseInt(x.getValue()), Integer.parseInt(y.getValue()), Integer.parseInt(z.getValue())));
        } catch (NumberFormatException e) {
            // an empty or "-" box: nothing to send
        }
    }

    private EditBox box(int left, int top, String value) {
        EditBox box = new EditBox(font, left, top, 60, 20, Component.empty());
        box.setValue(value);
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,8}"));
        return addRenderableWidget(box);
    }

    private void send() {
        try {
            PacketDistributor.sendToServer(new ConsoleNet.Hop(Integer.parseInt(x.getValue()),
                    Integer.parseInt(y.getValue()), Integer.parseInt(z.getValue())));
        } catch (NumberFormatException e) {
            // an empty or "-" box: nothing to send
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int top = height / 2 - 70;
        g.drawCenteredString(font, title, cx, top, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal(view.dimension() + "  " + view.pos().getX() + " "
                + view.pos().getY() + " " + view.pos().getZ()), cx, top + 14, 0xC0C0C0);
        g.drawCenteredString(font, Component.translatableWithFallback("tardis_rb.console.status",
                "Charge %s / %s    Hop range %s", view.charge(), view.chargeCap(), view.range()), cx, top + 26,
                0xC0C0C0);
        g.drawString(font, "X", cx - 95, top + 40, 0xA0A0A0);
        g.drawString(font, "Y", cx - 30, top + 40, 0xA0A0A0);
        g.drawString(font, "Z", cx + 35, top + 40, 0xA0A0A0);
        if (!view.message().isEmpty()) {
            g.drawCenteredString(font, Component.literal(view.message()), cx, height / 2 + 84, 0xFFD080);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
