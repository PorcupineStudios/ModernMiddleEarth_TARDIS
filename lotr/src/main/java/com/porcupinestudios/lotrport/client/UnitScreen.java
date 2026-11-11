package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.society.CoreComponents.Order;
import com.porcupinestudios.lotrport.unit.UnitNet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The unit screen (key U, or right-click one of your units): every unit you command, your hired units and your
 * colonies' soldiers. Pick one on the left; on the right its orders (follow, guard, stand, for the whole squad if
 * ticked), its squad name, stationing it in one of your colonies, and dismissing it. The server checks every press.
 */
public class UnitScreen extends Screen {
    private static final int ROW = 12;
    private static final int LIST_W = 150;

    private UnitNet.UnitList data;
    private UUID selected;
    private int stationIndex;
    private double scroll;
    private EditBox squadBox;
    private Checkbox wholeSquad;
    private boolean squadTicked;

    private UnitScreen(UnitNet.UnitList data) {
        super(Component.translatableWithFallback("screen.lotr_port.units", "Units"));
        this.data = data;
        this.selected = data.focus().orElse(data.units().isEmpty() ? null : data.units().get(0).id());
    }

    /** From the server: open the screen, or refresh it if it is open. */
    public static void show(UnitNet.UnitList list) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof UnitScreen open) {
            open.data = list;
            if (list.focus().isPresent()) {
                open.selected = list.focus().get();
            }
            open.rebuildWidgets();
        } else if (list.open()) {
            mc.setScreen(new UnitScreen(list));
        }
    }

    private int left() {
        return width / 2 - 190;
    }

    private int top() {
        return 30;
    }

    private Optional<UnitNet.Entry> current() {
        return data.units().stream().filter(e -> e.id().equals(selected)).findFirst();
    }

    @Override
    protected void init() {
        int x = left() + LIST_W + 16;
        int y = top() + 50;
        Optional<UnitNet.Entry> cur = current();
        if (cur.isPresent()) {
            UnitNet.Entry e = cur.get();
            int bw = 70;
            if (e.yours()) {
                addRenderableWidget(button("Follow", UnitNet.Kind.FOLLOW, x, y, bw));
            }
            addRenderableWidget(button("Guard", UnitNet.Kind.GUARD, x + (e.yours() ? bw + 4 : 0), y, bw));
            addRenderableWidget(button("Stand", UnitNet.Kind.STAND, x + (e.yours() ? 2 : 1) * (bw + 4), y, bw));
            wholeSquad = Checkbox.builder(Component.translatableWithFallback("screen.lotr_port.units.whole_squad",
                    "Whole squad"), font).pos(x, y + 24).selected(squadTicked)
                    .onValueChange((c, v) -> squadTicked = v).build();
            wholeSquad.active = !e.squad().isEmpty();
            addRenderableWidget(wholeSquad);

            squadBox = new EditBox(font, x, y + 52, 140, 18, Component.literal("Squad"));
            squadBox.setMaxLength(24);
            squadBox.setValue(e.squad());
            addRenderableWidget(squadBox);
            addRenderableWidget(Button.builder(Component.translatableWithFallback("screen.lotr_port.units.set_squad",
                    "Set squad"), b -> send(UnitNet.Kind.SQUAD, squadBox.getValue()))
                    .bounds(x + 146, y + 51, 70, 20).build());

            List<UnitNet.Target> targets = data.colonies();
            if (e.yours() && !targets.isEmpty()) {
                UnitNet.Target t = targets.get(Math.floorMod(stationIndex, targets.size()));
                addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
                    stationIndex++;
                    rebuildWidgets();
                }).bounds(x, y + 78, 20, 20).build());
                addRenderableWidget(Button.builder(Component.translatableWithFallback(
                        "screen.lotr_port.units.station", "Station in %s", t.name()),
                        b -> send(UnitNet.Kind.STATION, t.id().toString())).bounds(x + 24, y + 78, 192, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatableWithFallback("screen.lotr_port.units.dismiss",
                    "Dismiss"), b -> send(UnitNet.Kind.DISMISS, "")).bounds(x, y + 104, 70, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.done", "Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    private Button button(String label, UnitNet.Kind kind, int x, int y, int w) {
        return Button.builder(Component.translatableWithFallback("screen.lotr_port.units."
                + label.toLowerCase(java.util.Locale.ROOT), label), b -> send(kind, "")).bounds(x, y, w, 20).build();
    }

    private void send(UnitNet.Kind kind, String arg) {
        if (selected != null) {
            PacketDistributor.sendToServer(new UnitNet.Action(selected, kind.ordinal(), arg, squadTicked));
        }
    }

    private int listHeight() {
        return height - top() - 40;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        int x = left();
        int y = top();
        g.fill(x - 2, y - 2, x + LIST_W + 2, y + listHeight() + 2, 0x80000000);
        if (data.units().isEmpty()) {
            g.drawString(font, Component.translatableWithFallback("screen.lotr_port.units.none",
                    "You command no units. Hire them from captains."), x + 4, y + 4, 0xAAAAAA);
            return;
        }
        g.enableScissor(x, y, x + LIST_W, y + listHeight());
        int row = 0;
        for (UnitNet.Entry e : data.units()) {
            int ry = y + row * ROW - (int) scroll;
            row++;
            if (ry + ROW < y || ry > y + listHeight()) {
                continue;
            }
            boolean sel = e.id().equals(selected);
            if (sel) {
                g.fill(x, ry, x + LIST_W, ry + ROW, 0x60FFFFFF);
            }
            String label = (e.squad().isEmpty() ? "" : "[" + e.squad() + "] ") + display(e);
            g.drawString(font, font.plainSubstrByWidth(label, LIST_W - 4), x + 2, ry + 2,
                    e.yours() ? 0xFFFFFF : 0xFFD37F);
        }
        g.disableScissor();

        Optional<UnitNet.Entry> cur = current();
        if (cur.isEmpty()) {
            return;
        }
        UnitNet.Entry e = cur.get();
        int dx = x + LIST_W + 16;
        g.drawString(font, display(e), dx, y, 0xFFFFFF);
        g.drawString(font, Component.translatable("npc." + e.type().replace(':', '.')), dx, y + 12, 0xAAAAAA);
        Component owner = e.yours() ? Component.translatableWithFallback("screen.lotr_port.units.yours",
                "Your unit") : Component.translatableWithFallback("screen.lotr_port.units.soldier_of",
                "Soldier of %s (%s coins a day%s)", e.colony(), e.wage(), e.unpaid() > 0 ? ", " + e.unpaid()
                        + " days unpaid" : "");
        g.drawString(font, owner, dx, y + 24, e.unpaid() > 0 ? 0xFF7777 : 0xAAAAAA);
        Order order = e.orderValue();
        g.drawString(font, UnitNet.orderName(order).copy().append(" · " + e.where()
                + (e.health() >= 0 ? String.format(java.util.Locale.ROOT, " · %.0f/%.0f ❤", e.health(),
                        e.maxHealth()) : " · away")), dx, y + 36, 0xAAAAAA);
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.units.squad", "Squad"), dx,
                y + 50 + 42, 0xAAAAAA);
    }

    private static String display(UnitNet.Entry e) {
        return e.name().isEmpty() ? e.type().substring(e.type().indexOf(':') + 1).replace('_', ' ') : e.name();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left();
        int y = top();
        if (mx >= x && mx < x + LIST_W && my >= y && my < y + listHeight()) {
            int i = (int) ((my - y + scroll) / ROW);
            if (i >= 0 && i < data.units().size()) {
                selected = data.units().get(i).id();
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int max = Math.max(0, data.units().size() * ROW - listHeight());
        scroll = Math.max(0, Math.min(max, scroll - dy * ROW * 2));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
