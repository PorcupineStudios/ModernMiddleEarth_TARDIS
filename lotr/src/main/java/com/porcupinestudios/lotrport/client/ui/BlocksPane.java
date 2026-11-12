package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Draws a {@link Page.Blocks} like Scratch: C blocks (if/else) with their arms, stack blocks under each other and
 * hexagon conditions in slots, all with inline inputs. Clicking a picker input opens its menu, a number's arrows step
 * it (Shift 10, Ctrl 64; the wheel over a number too), clicking a block's words opens its menu (change, remove,
 * move), and the "+" slots add blocks. Rows wrap when a block is wider than the page.
 */
final class BlocksPane {
    static final int ROW = 22;
    static final int INPUT = 14;
    static final int GAP = 4;
    static final int PAD = 6;
    static final int ARM = 14;
    static final int CAP = 12;
    static final int WHITE = 0xFFFFFFFF;
    static final int INPUT_TEXT = 0xFF575E75;

    private final UiScreen screen;
    private Page.Blocks page;
    private final ScrollBar bar;
    private int top;
    private int bottom;
    /** The number under the mouse last frame, for the wheel. */
    private Stat.Stepper hoverNumber;

    BlocksPane(UiScreen screen, Page.Blocks page) {
        this.screen = screen;
        this.page = page;
        this.bar = new ScrollBar(screen);
    }

    void set(Page.Blocks p) {
        page = p;
    }

    boolean scroll(double mouseY, double amount) {
        if (hoverNumber != null) {
            Stat.Stepper st = hoverNumber;
            int v = st.value() + (amount > 0 ? 1 : -1) * st.step() * Sections.fast();
            screen.send(st.action(), "", Math.max(st.min(), Math.min(st.max(), v)));
            return true;
        }
        return mouseY >= top && mouseY <= bottom && bar.wheel(amount, ROW * 2);
    }

    void draw(Imm ui, int x, int y, int w, int h) {
        int buttonsH = page.buttons().isEmpty() ? 0 : UiTheme.BUTTON_HEIGHT + 6;
        ui.sprite(UiTheme.INSET_LIGHT, x, y, w, h - buttonsH);
        hoverNumber = null;
        int innerW = w - 20 - ScrollBar.WIDTH;
        // measure, then draw
        int contentH = script(ui, x + 8, 0, innerW, false);
        int viewH = h - buttonsH - 8;
        bar.measure(contentH, viewH);
        ui.clip(x, y + 2, w, h - buttonsH - 4);
        script(ui, x + 8, y + 6 - bar.offset, innerW, true);
        ui.unclip();
        top = y;
        bottom = y + h - buttonsH;
        bar.draw(ui, x + w - ScrollBar.WIDTH - 3, y + 3, h - buttonsH - 6);
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
    }

    /** The whole script, blocks with a gap between top-level scripts; returns its height. */
    private int script(Imm ui, int x, int y, int w, boolean draw) {
        int cy = y;
        for (Page.Block b : page.script()) {
            cy += block(ui, b, x, cy, w, draw)[1] + 10;
        }
        if (page.add().isPresent()) {
            Stat.Picker pk = page.add().get();
            int aw = ui.font.width(pk.choices().isEmpty() ? "" : "+ Add rule") + 24;
            if (draw) {
                dashed(ui, x, cy, aw, ROW, 0xFF8A7F6A);
                ui.text("+ Add rule", x + 12, cy + 7, 0xFF5A5040);
                int ay = cy;
                ui.hit(x, ay, aw, ROW, () -> screen.popup(new Popups.ChoiceMenu(screen, pk, x, ay + ROW,
                        Math.max(aw, 200))));
            }
            cy += ROW + 8;
        }
        for (Component n : page.notes()) {
            if (draw) {
                ui.text(n, x, cy, UiTheme.ON_LIGHT[1]);
            }
            cy += 11;
        }
        return cy - y;
    }

    /** Lays out (and draws) one block; returns {w, h}. */
    private int[] block(Imm ui, Page.Block b, int x, int y, int maxW, boolean draw) {
        if (b.shape().equals("bool")) {
            return bool(ui, b, x, y, maxW, draw);
        }
        int color = b.color();
        int dark = darker(color);
        if (b.shape().equals("stack")) {
            int[] row = row(ui, b.head(), x + PAD, y, maxW - 2 * PAD, false, b);
            int bw = Math.max(row[0] + 2 * PAD, 60);
            int bh = Math.max(ROW, row[1] + 8);
            if (draw) {
                body(ui, x, y, bw, bh, color, dark, true);
                menu(ui, b, x, y, bw, bh);
                row(ui, b.head(), x + PAD, y + (bh - row[1]) / 2, maxW - 2 * PAD, true, b);
            }
            return new int[] {bw, bh};
        }
        // C block: head, arms (each: label row for all but the first, then its body), cap
        int[] head = row(ui, b.head(), x + PAD, y, maxW - 2 * PAD, false, b);
        int headH = Math.max(ROW, head[1] + 8);
        int bw = Math.max(head[0] + 2 * PAD, 140);
        int cy = y + headH;
        int[][] armSizes = new int[b.arms().size()][];
        int widest = bw;
        for (int i = 0; i < b.arms().size(); i++) {
            Page.Arm a = b.arms().get(i);
            int ah = i == 0 ? 0 : ROW - 4;
            int inner = 0;
            int innerW = 0;
            for (Page.Block c : a.body()) {
                int[] s = block(ui, c, 0, 0, maxW - ARM, false);
                inner += s[1];
                innerW = Math.max(innerW, s[0]);
            }
            if (a.add().isPresent()) {
                inner += ROW - 4;
                innerW = Math.max(innerW, ui.font.width("+ add") + 16);
            }
            armSizes[i] = new int[] {ah, Math.max(inner, 14)};
            widest = Math.max(widest, ARM + innerW + 8);
        }
        bw = Math.min(maxW, widest);
        int total = headH;
        for (int[] s : armSizes) {
            total += s[0] + s[1];
        }
        total += CAP;
        if (!draw) {
            return new int[] {bw, total};
        }
        // the C: head, a left bar the whole height, arm label rows and the cap
        body(ui, x, y, bw, headH, color, dark, true);
        ui.g.fill(x, y + headH, x + ARM, y + total - CAP, color);
        ui.g.fill(x, y + headH, x + 1, y + total - CAP, dark);
        menu(ui, b, x, y, bw, headH);
        row(ui, b.head(), x + PAD, y + (headH - head[1]) / 2, maxW - 2 * PAD, true, b);
        for (int i = 0; i < b.arms().size(); i++) {
            Page.Arm a = b.arms().get(i);
            if (i > 0) {
                body(ui, x, cy, bw, armSizes[i][0], color, dark, false);
                ui.text(a.label(), x + PAD, cy + (armSizes[i][0] - 8) / 2, WHITE);
                menu(ui, b, x, cy, bw, armSizes[i][0]);
                cy += armSizes[i][0];
            }
            int by = cy;
            for (Page.Block c : a.body()) {
                by += block(ui, c, x + ARM, by, maxW - ARM, true)[1];
            }
            if (a.add().isPresent()) {
                Stat.Picker pk = a.add().get();
                int aw = ui.font.width("+ add") + 16;
                int ax = x + ARM + 2;
                int ay = by + 2;
                dashed(ui, ax, ay, aw, ROW - 8, darker(color));
                ui.text("+ add", ax + 8, ay + 4, darker(darker(color)));
                ui.hit(ax, ay, aw, ROW - 8, () -> screen.popup(new Popups.ChoiceMenu(screen, pk, ax, ay + ROW - 8,
                        Math.max(aw, 140))));
            }
            cy += armSizes[i][1];
        }
        body(ui, x, cy, Math.min(bw, 80), CAP, color, dark, false);
        ui.g.fill(x, cy + CAP - 1, x + Math.min(bw, 80), cy + CAP, dark);
        return new int[] {bw, total};
    }

    /** A hexagon condition: its row between pointed ends. */
    private int[] bool(Imm ui, Page.Block b, int x, int y, int maxW, boolean draw) {
        int[] row = row(ui, b.head(), 0, 0, maxW - 20, false, b);
        int bh = Math.max(INPUT + 4, row[1] + 4);
        int point = Math.min(10, bh / 2);
        int bw = row[0] + 2 * point + 4;
        if (draw) {
            hex(ui, x, y, bw, bh, b.color(), darker(b.color()));
            menu(ui, b, x, y, bw, bh);
            row(ui, b.head(), x + point + 2, y + (bh - row[1]) / 2, maxW - 20, true, b);
        }
        return new int[] {bw, bh};
    }

    /** Lays out (and draws) a row of parts, wrapping at {@code maxW}; returns {w, h}. */
    private int[] row(Imm ui, List<Page.Part> parts, int x, int y, int maxW, boolean draw, Page.Block owner) {
        int cx = 0;
        int lineH = 0;
        int lineY = 0;
        int widest = 0;
        // sizes first (so each line can be centred on its tallest part)
        int n = parts.size();
        int[][] size = new int[n][];
        for (int i = 0; i < n; i++) {
            size[i] = part(ui, parts.get(i), 0, 0, maxW, false, owner);
        }
        int[] lineOf = new int[n];
        int[] lineHeights = new int[n + 1];
        int line = 0;
        for (int i = 0; i < n; i++) {
            if (cx > 0 && cx + size[i][0] > maxW) {
                widest = Math.max(widest, cx - GAP);
                lineHeights[line] = lineH;
                line++;
                cx = 0;
                lineH = 0;
            }
            lineOf[i] = line;
            cx += size[i][0] + GAP;
            lineH = Math.max(lineH, size[i][1]);
        }
        lineHeights[line] = lineH;
        widest = Math.max(widest, cx - GAP);
        int total = 0;
        for (int l = 0; l <= line; l++) {
            total += lineHeights[l] + (l > 0 ? 3 : 0);
        }
        if (draw) {
            cx = 0;
            int cur = 0;
            lineY = 0;
            for (int i = 0; i < n; i++) {
                if (lineOf[i] != cur) {
                    lineY += lineHeights[cur] + 3;
                    cur = lineOf[i];
                    cx = 0;
                }
                int py = y + lineY + (lineHeights[cur] - size[i][1]) / 2;
                part(ui, parts.get(i), x + cx, py, maxW, true, owner);
                cx += size[i][0] + GAP;
            }
        }
        return new int[] {Math.max(0, widest), Math.max(total, 9)};
    }

    private int[] part(Imm ui, Page.Part p, int x, int y, int maxW, boolean draw, Page.Block owner) {
        switch (p.kind()) {
            case 1 -> {
                Stat.Picker pk = p.picker().orElseThrow();
                String s = ui.fit(p.text().getString(), 140);
                int w = ui.font.width(s) + 16;
                if (draw) {
                    boolean hover = ui.over(x, y, w, INPUT);
                    pill(ui, x, y, w, INPUT, hover ? 0xFFF0F0F0 : WHITE);
                    ui.text(s, x + 5, y + 3, INPUT_TEXT);
                    ui.text("\u25be", x + w - 9, y + 3, INPUT_TEXT);
                    ui.hit(x, y, w, INPUT, () -> screen.popup(new Popups.ChoiceMenu(screen, pk, x, y + INPUT,
                            Math.max(w, 120))));
                }
                return new int[] {w, INPUT};
            }
            case 2 -> {
                Stat.Stepper st = p.stepper().orElseThrow();
                String s = Integer.toString(st.value());
                int w = ui.font.width(s) + 22;
                if (draw) {
                    pill(ui, x, y, w, INPUT, WHITE);
                    ui.text(s, x + 11, y + 3, INPUT_TEXT);
                    boolean hover = ui.over(x, y, w, INPUT);
                    int arrow = hover ? 0xFF4C97FF : 0xFFB0B0B0;
                    ui.text("\u2039", x + 3, y + 3, arrow);
                    ui.text("\u203a", x + w - 7, y + 3, arrow);
                    if (hover) {
                        hoverNumber = st;
                        ui.tooltip(Component.literal("Click the arrows or scroll; Shift x10, Ctrl x64"));
                    }
                    ui.hit(x, y, w / 2, INPUT, () -> screen.send(st.action(), "", Math.max(st.min(), st.value()
                            - st.step() * Sections.fast())));
                    ui.hit(x + w / 2, y, w - w / 2, INPUT, () -> screen.send(st.action(), "", Math.min(st.max(),
                            st.value() + st.step() * Sections.fast())));
                }
                return new int[] {w, INPUT};
            }
            case 3 -> {
                return block(ui, p.block().orElseThrow(), x, y, maxW, draw);
            }
            case 4 -> {
                Stat.Picker pk = p.picker().orElseThrow();
                int w = 30;
                int h = INPUT + 2;
                if (draw) {
                    hex(ui, x, y, w, h, darker(owner.color()), darker(darker(owner.color())));
                    ui.text("+", x + w / 2 - 2, y + 4, WHITE);
                    ui.hit(x, y, w, h, () -> screen.popup(new Popups.ChoiceMenu(screen, pk, x, y + h, 160)));
                }
                return new int[] {w, h};
            }
            default -> {
                String s = p.text().getString();
                int w = ui.font.width(s);
                if (draw) {
                    ui.text(s, x, y + 3, WHITE);
                }
                return new int[] {w, INPUT};
            }
        }
    }

    /** A click on a block's own area (not its inputs) opens its menu. */
    private void menu(Imm ui, Page.Block b, int x, int y, int w, int h) {
        b.menu().ifPresent(pk -> ui.hit(x, y, w, h, () -> screen.popup(new Popups.ChoiceMenu(screen, pk, x + 4,
                y + h, 150))));
    }

    // ---------------------------------------------------------------- shapes

    static int darker(int argb) {
        int r = (argb >> 16 & 0xFF) * 3 / 4;
        int g = (argb >> 8 & 0xFF) * 3 / 4;
        int b = (argb & 0xFF) * 3 / 4;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** A block body with rounded corners, an edge below and (for stack rows) Scratch's notch and tab. */
    private static void body(Imm ui, int x, int y, int w, int h, int color, int dark, boolean notch) {
        ui.g.fill(x + 1, y, x + w - 1, y + h, color);
        ui.g.fill(x, y + 1, x + 1, y + h - 1, color);
        ui.g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
        ui.g.fill(x + 1, y + h - 1, x + w - 1, y + h, dark);
        if (notch) {
            // the notch on top (darker) and the tab below (the block's colour) that clicks into the next block
            ui.g.fill(x + 10, y, x + 22, y + 2, dark);
            ui.g.fill(x + 10, y + h, x + 22, y + h + 2, color);
        }
    }

    private static void pill(Imm ui, int x, int y, int w, int h, int color) {
        ui.g.fill(x + 2, y, x + w - 2, y + h, color);
        ui.g.fill(x, y + 2, x + w, y + h - 2, color);
        ui.g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }

    /** A hexagon: pointed left and right ends. */
    private static void hex(Imm ui, int x, int y, int w, int h, int color, int dark) {
        int half = h / 2;
        int point = Math.min(10, half);
        for (int i = 0; i < h; i++) {
            int d = Math.abs(i - half);
            int inset = point * d / Math.max(1, half);
            ui.g.fill(x + inset, y + i, x + w - inset, y + i + 1, i == h - 1 ? dark : color);
        }
    }

    private static void dashed(Imm ui, int x, int y, int w, int h, int color) {
        for (int i = 0; i < w; i += 4) {
            ui.g.fill(x + i, y, x + Math.min(w, i + 2), y + 1, color);
            ui.g.fill(x + i, y + h - 1, x + Math.min(w, i + 2), y + h, color);
        }
        for (int i = 0; i < h; i += 4) {
            ui.g.fill(x, y + i, x + 1, y + Math.min(h, i + 2), color);
            ui.g.fill(x + w - 1, y + i, x + w, y + Math.min(h, i + 2), color);
        }
    }

    static boolean shift() {
        return Screen.hasShiftDown();
    }
}
