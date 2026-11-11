package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.colony.work.LedgerNet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Ledger (RingRealms' Steward's Ledger): the colony at a glance, and pages for its buildings, people and stock.
 * Members with the {@code treasury} permission set the head tax and the wage bonus here.
 */
public class LedgerScreen extends Screen {
    private static final int ROW = 11;
    private static final String[] PAGES = {"Overview", "Buildings", "People", "Stock"};

    private LedgerNet.Ledger ledger;
    private int page;
    private int scroll;

    private LedgerScreen(LedgerNet.Ledger ledger) {
        super(Component.translatableWithFallback("screen.lotr_port.ledger", "Ledger"));
        this.ledger = ledger;
    }

    public static void show(LedgerNet.Ledger ledger) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LedgerScreen open && open.ledger.colony().equals(ledger.colony())) {
            open.ledger = ledger;
            open.rebuildWidgets();
        } else if (ledger.open()) {
            mc.setScreen(new LedgerScreen(ledger));
        }
    }

    private int left() {
        return Math.max(8, width / 2 - 190);
    }

    private int right() {
        return Math.min(width - 8, width / 2 + 190);
    }

    @Override
    protected void init() {
        int w = 70;
        int x = width / 2 - (PAGES.length * (w + 4) - 4) / 2;
        for (int i = 0; i < PAGES.length; i++) {
            int p = i;
            Button b = Button.builder(Component.translatableWithFallback("screen.lotr_port.ledger.page." + i,
                    PAGES[i]), x2 -> {
                        page = p;
                        scroll = 0;
                        rebuildWidgets();
                    }).bounds(x + i * (w + 4), 26, w, 20).build();
            b.active = page != i;
            addRenderableWidget(b);
        }
        if (page == 0 && ledger.canEdit()) {
            int y = 120;
            int bx = left() + 150;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> economy(-1, 0)).bounds(bx, y - 5, 20, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> economy(1, 0)).bounds(bx + 24, y - 5, 20,
                    20).build());
            addRenderableWidget(Button.builder(Component.literal("-"), b -> economy(0, -1)).bounds(bx, y + 19, 20, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> economy(0, 1)).bounds(bx + 24, y + 19, 20,
                    20).build());
        }
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.done", "Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    private void economy(int tax, int bonus) {
        PacketDistributor.sendToServer(new LedgerNet.SetEconomy(ledger.colony(), ledger.headTax() + tax,
                ledger.wageBonus() + bonus));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, Component.literal(ledger.name()), width / 2, 8, 0xFFD37F);
        int x = left();
        int y = 56;
        if (page == 0) {
            line(g, x, y, "Citizens", String.valueOf(ledger.citizens()));
            line(g, x, y + 14, "Happiness", ledger.happiness() + " / 100");
            line(g, x, y + 28, "Food", ledger.food() + " / 20");
            line(g, x, y + 42, "Treasury", ledger.coins() + " coins");
            line(g, x, y + 64, "Head tax", ledger.headTax() + " a day");
            line(g, x, y + 88, "Wage bonus", (ledger.wageBonus() >= 0 ? "+" : "") + ledger.wageBonus() + " a day");
            line(g, x, y + 112, "Buildings", String.valueOf(ledger.buildings().size()));
            if (!ledger.canEdit()) {
                g.drawString(font, Component.translatableWithFallback("screen.lotr_port.ledger.no_edit",
                        "Only those with the treasury permission set taxes and wages"), x, y + 132, 0x888888);
            }
            return;
        }
        List<LedgerNet.Row> rows = page == 1 ? ledger.buildings() : page == 2 ? ledger.people() : ledger.stock();
        int bottom = height - 34;
        g.fill(x - 4, y - 4, right() + 4, bottom + 2, 0x80000000);
        if (rows.isEmpty()) {
            g.drawString(font, page == 1 ? "No buildings yet: use a Building Writ on a door"
                    : page == 2 ? "Nobody lives here yet" : "Nothing in the stock: goods go to the storehouses", x, y,
                    0x888888);
            return;
        }
        int perRow = page == 3 ? ROW : ROW * 2;
        int visible = Math.max(1, (bottom - y) / perRow);
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
        for (int i = 0; i < visible && i + scroll < rows.size(); i++) {
            LedgerNet.Row r = rows.get(i + scroll);
            int ry = y + i * perRow;
            String value = page == 1 ? "quality " + r.value() : page == 2 ? "happy " + r.value() : "x" + r.value();
            int vw = font.width(value);
            g.drawString(font, font.plainSubstrByWidth(r.name(), right() - x - vw - 8), x, ry, 0xFFFFFF);
            g.drawString(font, value, right() - vw, ry, 0xFFD37F);
            if (page != 3) {
                String detail = r.detail() + (r.note().isEmpty() ? "" : " · " + r.note());
                g.drawString(font, font.plainSubstrByWidth(detail, right() - x), x + 6, ry + ROW - 1,
                        r.note().equals("striking") || r.note().equals("starving") ? 0xFF7070 : 0xAAAAAA);
            }
        }
        if (rows.size() > visible) {
            g.drawString(font, (scroll + 1) + "-" + Math.min(rows.size(), scroll + visible) + " of " + rows.size(),
                    x, bottom - 9, 0x888888);
        }
    }

    private void line(GuiGraphics g, int x, int y, String label, String value) {
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.ledger." + label.toLowerCase()
                .replace(' ', '_'), label), x, y, 0xAAAAAA);
        g.drawString(font, value, x + 80, y, 0xFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, scroll - (int) Math.signum(dy));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
