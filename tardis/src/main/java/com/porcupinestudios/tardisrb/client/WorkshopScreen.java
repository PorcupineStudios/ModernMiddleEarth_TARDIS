package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.net.WorkshopNet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Repairs and research, opened from the console: one line per repair of the current stage (and any repair that can
 * be done now) and per research node, with cost and a button when it can be done. Placeholder layout until Aaron's
 * console art; a proper tree view comes with it.
 */
public final class WorkshopScreen extends Screen {
    private static final int ROW = 22;
    private WorkshopNet.WorkshopView view;
    private int scroll;

    private WorkshopScreen(WorkshopNet.WorkshopView view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    public static void show(WorkshopNet.WorkshopView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WorkshopScreen open) {
            open.view = view;
            open.rebuildWidgets();
        } else {
            mc.setScreen(new WorkshopScreen(view));
        }
    }

    private int top() {
        return 44;
    }

    private int visibleRows() {
        return Math.max(1, (height - top() - 30) / ROW);
    }

    @Override
    protected void init() {
        List<WorkshopNet.Entry> entries = view.entries();
        scroll = Math.max(0, Math.min(scroll, entries.size() - visibleRows()));
        int right = width / 2 + 170;
        for (int i = 0; i < visibleRows() && scroll + i < entries.size(); i++) {
            WorkshopNet.Entry e = entries.get(scroll + i);
            if (e.state() != WorkshopNet.AVAILABLE) {
                continue;
            }
            Component label = switch (e.kind()) {
                case WorkshopNet.REPAIR -> Component.translatableWithFallback("tardis_rb.workshop.repair", "Repair");
                case WorkshopNet.ROOM -> Component.translatableWithFallback("tardis_rb.workshop.build", "Build");
                default -> Component.translatableWithFallback("tardis_rb.workshop.research", "Research");
            };
            addRenderableWidget(Button.builder(label, b -> PacketDistributor.sendToServer(
                    new WorkshopNet.Act(e.kind(), e.id()))).bounds(right - 70, top() + i * ROW, 70, 18).build());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int before = scroll;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(dy), view.entries().size() - visibleRows()));
        if (scroll != before) {
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = width / 2 - 170;
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatableWithFallback("tardis_rb.workshop.status",
                "Stage %s    Hop range %s    Charge %s", view.stage(), view.range(), view.charge()), width / 2, 24,
                0xC0C0C0);
        List<WorkshopNet.Entry> entries = view.entries();
        for (int i = 0; i < visibleRows() && scroll + i < entries.size(); i++) {
            WorkshopNet.Entry e = entries.get(scroll + i);
            int y = top() + i * ROW;
            String kind = switch (e.kind()) {
                case WorkshopNet.REPAIR -> "Repair";
                case WorkshopNet.ROOM -> "Room";
                default -> "Research";
            };
            int color = switch (e.state()) {
                case WorkshopNet.DONE -> 0x70D070;
                case WorkshopNet.RUNNING -> 0x70B0FF;
                case WorkshopNet.AVAILABLE, WorkshopNet.AT_PART -> 0xFFFFFF;
                default -> 0x808080;
            };
            String state = switch (e.state()) {
                case WorkshopNet.DONE -> "done";
                case WorkshopNet.RUNNING -> "under way " + e.extra();
                case WorkshopNet.AT_PART -> "fix the part in the room";
                case WorkshopNet.LOCKED -> e.extra().isEmpty() || e.kind() != WorkshopNet.ROOM ? "locked"
                        : "locked, " + e.extra();
                default -> e.extra();
            };
            g.drawString(font, kind + ": " + e.title() + "  (" + state + ")", left, y, color);
            g.drawString(font, e.cost(), left + 8, y + 10, 0xA0A0A0);
        }
        if (!view.message().isEmpty()) {
            g.drawCenteredString(font, Component.literal(view.message()), width / 2, height - 20, 0xFFD080);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
