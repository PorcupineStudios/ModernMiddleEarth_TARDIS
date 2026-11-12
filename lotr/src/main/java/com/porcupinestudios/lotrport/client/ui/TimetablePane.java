package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Draws a {@link Page.Timetable} (ONI's schedule screen): the palette, an hour ruler with a mark at the current hour,
 * one row of 24 cells per line, notes under them, and buttons. Pick a kind, then click or drag across an editable
 * row's cells to paint them: each changed cell is sent at once and shown before the server answers. Click a row's
 * name to select it for the row buttons. Rows and notes scroll when taller than the page.
 */
final class TimetablePane {
    private static final int PALETTE_H = 18;
    private static final int RULER_H = 11;
    private static final int ROW_H = 16;
    private static final int NOTE_H = 11;

    private final UiScreen screen;
    private final ScrollBar bar;
    private Page.Timetable page;
    /** Cells as painted here, ahead of the server's next copy. */
    private final Map<String, char[]> cells = new HashMap<>();
    private int kind;
    private String selected = "";
    private boolean painting;
    // the grid of the last frame, for drag painting
    private int gridX;
    private int gridY;
    private int cellW;
    private int bodyTop;
    private int bodyBottom;

    TimetablePane(UiScreen screen, Page.Timetable page) {
        this.screen = screen;
        this.bar = new ScrollBar(screen);
        set(page);
        for (int i = 0; i < page.kinds().size(); i++) {
            if (page.kinds().get(i).paintable()) {
                kind = i;
                break;
            }
        }
    }

    void set(Page.Timetable p) {
        page = p;
        cells.clear();
        for (Page.Line l : p.lines()) {
            cells.put(l.id(), l.cells().toCharArray());
        }
        if (p.lines().stream().noneMatch(l -> l.id().equals(selected))) {
            selected = "";
        }
    }

    void draw(Imm ui, int x, int y, int w, int h) {
        int buttonsH = page.buttons().isEmpty() && page.rowActions().isEmpty() ? 0 : UiTheme.BUTTON_HEIGHT + 6;
        ui.sprite(UiTheme.INSET_LIGHT, x, y, w, h - buttonsH);

        // palette
        int px = x + 8;
        int py = y + 5;
        boolean canPaint = page.paint().isPresent();
        if (canPaint) {
            ui.text(Component.translatableWithFallback("ui.lotr_port.paint", "Paint:").getString(), px, py + 4,
                    UiTheme.ON_LIGHT[1]);
            px += ui.font.width("Paint:") + 6;
            for (int i = 0; i < page.kinds().size(); i++) {
                Page.Kind k = page.kinds().get(i);
                if (!k.paintable()) {
                    continue;
                }
                String label = k.label().getString();
                int bw = ui.font.width(label) + 22;
                boolean on = i == kind;
                if (on || ui.over(px, py, bw, PALETTE_H - 4)) {
                    ui.g.fill(px, py, px + bw, py + PALETTE_H - 4, on ? 0x50000000 : 0x25000000);
                }
                ui.g.fill(px + 3, py + 2, px + 13, py + 12, UiTheme.swatch(k.swatch()));
                ui.text(label, px + 16, py + 3, UiTheme.DARK_TEXT);
                int index = i;
                ui.hit(px, py, bw, PALETTE_H - 4, () -> kind = index);
                px += bw + 4;
            }
        } else {
            // a legend instead, for those who may only look
            for (Page.Kind k : page.kinds()) {
                String label = k.label().getString();
                ui.g.fill(px, py + 2, px + 10, py + 12, UiTheme.swatch(k.swatch()));
                ui.text(label, px + 13, py + 3, UiTheme.DARK_TEXT);
                px += ui.font.width(label) + 22;
            }
        }

        // ruler
        int labelW = Math.min(118, (int) (w * 0.3));
        gridX = x + 8 + labelW + 4;
        cellW = Math.max(4, (w - 16 - labelW - 4 - ScrollBar.WIDTH - 4) / 24);
        int ry = y + 5 + PALETTE_H;
        int now = currentHour();
        for (int hr = 0; hr < 24; hr += 3) {
            ui.text(String.valueOf(hr), gridX + hr * cellW + 1, ry, UiTheme.ON_LIGHT[1]);
        }
        if (now >= 0) {
            ui.g.fill(gridX + now * cellW + cellW / 2 - 1, ry + 8, gridX + now * cellW + cellW / 2 + 1,
                    ry + RULER_H, UiTheme.DARK_TEXT);
        }

        // rows and notes, scrolling
        bodyTop = ry + RULER_H;
        bodyBottom = y + h - buttonsH - 4;
        int contentH = page.lines().size() * ROW_H + (page.notes().isEmpty() ? 0 : 6 + page.notes().size() * NOTE_H);
        bar.measure(contentH, bodyBottom - bodyTop);
        gridY = bodyTop - bar.offset;
        ui.clip(x, bodyTop, w, bodyBottom - bodyTop);
        int rowY = gridY;
        for (Page.Line l : page.lines()) {
            drawRow(ui, l, x + 8, rowY, labelW, now);
            rowY += ROW_H;
        }
        rowY += 6;
        for (Component note : page.notes()) {
            String s = note.getString();
            String shown = ui.fit(s, w - 20 - ScrollBar.WIDTH);
            ui.text(shown, x + 8, rowY, UiTheme.ON_LIGHT[1]);
            if (!shown.equals(s) && ui.over(x + 8, rowY, w - 20, NOTE_H)) {
                ui.tooltip(note);
            }
            rowY += NOTE_H;
        }
        ui.unclip();
        bar.draw(ui, x + w - ScrollBar.WIDTH - 3, bodyTop, bodyBottom - bodyTop);

        // buttons: row actions (need a selected row), then the rest
        int bx = x + w;
        List<Action> buttons = page.buttons();
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Action a = buttons.get(i);
            int bw = ui.font.width(a.label()) + 16;
            bx -= bw;
            ui.button(bx, y + h - UiTheme.BUTTON_HEIGHT, bw, UiTheme.BUTTON_HEIGHT, a.label(), a.enabled(), a.why(),
                    () -> screen.act(a, "", 0));
            bx -= 4;
        }
        for (int i = page.rowActions().size() - 1; i >= 0; i--) {
            Action a = page.rowActions().get(i);
            int bw = ui.font.width(a.label()) + 16;
            bx -= bw;
            boolean enabled = a.enabled() && !selected.isEmpty();
            ui.button(bx, y + h - UiTheme.BUTTON_HEIGHT, bw, UiTheme.BUTTON_HEIGHT, a.label(), enabled,
                    a.enabled() ? java.util.Optional.of(Component.translatableWithFallback("ui.lotr_port.pick_row",
                            "Click a row's name first")) : a.why(), () -> screen.act(a, selected, 0));
            bx -= 4;
        }
    }

    private void drawRow(Imm ui, Page.Line l, int x, int y, int labelW, int now) {
        boolean sel = l.id().equals(selected);
        if (sel) {
            ui.g.fill(x - 3, y, x + labelW + 2, y + ROW_H - 2, 0x30000000);
        }
        String name = l.label().getString();
        String detail = l.detail().getString();
        int nameW = ui.font.width(name);
        String shownName = ui.fit(name, labelW - 2);
        ui.text(Component.literal(shownName).withStyle(l.editable() ? ChatFormatting.BOLD : ChatFormatting.ITALIC),
                x, y + 3, l.editable() ? UiTheme.DARK_TEXT : UiTheme.ON_LIGHT[1]);
        int used = Math.min(labelW, ui.font.width(Component.literal(shownName).withStyle(ChatFormatting.BOLD)) + 4);
        if (!detail.isEmpty() && used < labelW - 12) {
            ui.text(ui.fit(detail, labelW - used), x + used, y + 3, UiTheme.ON_LIGHT[1]);
        }
        if (ui.over(x, y, labelW, ROW_H - 2)) {
            if (nameW > labelW - 2 || !detail.isEmpty()) {
                ui.tooltip(Component.literal(name + (detail.isEmpty() ? "" : " " + detail)));
            }
        }
        if (l.editable()) {
            ui.hit(x - 3, y, labelW + 5, ROW_H - 2, () -> selected = l.id());
        }
        char[] c = cells.getOrDefault(l.id(), l.cells().toCharArray());
        for (int hr = 0; hr < 24 && hr < c.length; hr++) {
            int cx = gridX + hr * cellW;
            int col = UiTheme.swatch(swatchOf(c[hr]));
            ui.g.fill(cx, y + 1, cx + cellW - 1, y + ROW_H - 3, l.editable() ? col : (col & 0x00FFFFFF) | 0xA0000000);
            if (hr == now) {
                ui.g.fill(cx, y + ROW_H - 3, cx + cellW - 1, y + ROW_H - 2, UiTheme.DARK_TEXT);
            }
        }
        if (l.editable() && page.paint().isPresent()) {
            ui.hit(gridX, y + 1, cellW * 24, ROW_H - 4, () -> {
                painting = true;
                paintAt(ui.mouseX, ui.mouseY);
            });
        }
    }

    private int swatchOf(char letter) {
        for (Page.Kind k : page.kinds()) {
            if (k.letter() == letter) {
                return k.swatch();
            }
        }
        return 7;
    }

    boolean painting() {
        return painting;
    }

    void stopPainting() {
        painting = false;
    }

    /** Paints the cell under the mouse with the picked kind (while the button is held). */
    void paintAt(double mx, double my) {
        if (!painting || page.paint().isEmpty() || my < bodyTop || my >= bodyBottom || mx < gridX) {
            return;
        }
        int row = (int) Math.floor((my - gridY) / ROW_H);
        int hr = (int) Math.floor((mx - gridX) / cellW);
        if (row < 0 || row >= page.lines().size() || hr < 0 || hr >= 24) {
            return;
        }
        Page.Line l = page.lines().get(row);
        if (!l.editable() || kind < 0 || kind >= page.kinds().size()) {
            return;
        }
        char letter = page.kinds().get(kind).letter();
        char[] c = cells.computeIfAbsent(l.id(), k -> l.cells().toCharArray());
        if (hr < c.length && c[hr] != letter) {
            c[hr] = letter;
            screen.send(page.paint().get(), l.id(), hr * 16 + kind);
        }
    }

    boolean scroll(double mouseY, double amount) {
        return mouseY >= bodyTop && mouseY <= bodyBottom && bar.wheel(amount, ROW_H);
    }

    /** The clock hour in the player's world, or -1. */
    private static int currentHour() {
        var level = Minecraft.getInstance().level;
        return level == null ? -1 : (int) ((Math.floorMod(level.getDayTime(), 24000L) / 1000 + 6) % 24);
    }
}
