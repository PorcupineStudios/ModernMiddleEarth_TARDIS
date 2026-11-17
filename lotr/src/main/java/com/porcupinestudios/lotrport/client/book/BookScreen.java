package com.porcupinestudios.lotrport.client.book;

import com.mojang.blaze3d.systems.RenderSystem;
import com.porcupinestudios.lotrport.client.ui.Imm;
import com.porcupinestudios.lotrport.client.ui.UiTheme;
import com.porcupinestudios.lotrport.colony.book.Book;
import com.porcupinestudios.lotrport.colony.book.Books;
import com.porcupinestudios.lotrport.colony.book.El;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Every colony book (plaques, the Town Hall and Treasury ledgers, the Customs Counter) in one screen: a header band
 * with the title and chips, a row of tabs, the overview page on the left and the open tab on the right (scrolling).
 * It only draws the {@link Book} the server sent and sends clicks back; the server answers with the book afresh.
 */
public class BookScreen extends Screen {
    private static final Pattern TOKEN = Pattern.compile("\\{(item|lang):([^}|]*)(?:\\|([^}]*))?}");
    private static final int[] ACCENTS = {0xFF8A5A2E, 0xFF4F7F3A, 0xFFC9A227, 0xFF3F6A9A};
    private static final int HEADER = 30;
    private static final int TABS = 18;
    private static final int GAP = 6;
    private static final int BAR = 6;
    private static final int LINE = 11;

    private Book book;
    private final Imm ui;
    private int scroll;
    private int leftScroll;
    private int contentHeight;
    private int leftHeight;
    private boolean dragging;
    private String toast = "";
    private long toastUntil;
    private EditBox field;
    private El fieldEl;
    private boolean fieldShown;

    // the frame, measured in init
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int lw;
    private int rx;
    private int ry;
    private int rw;
    private int rh;

    protected BookScreen(Book book) {
        super(Component.literal(book.title()));
        this.book = book;
        this.ui = new Imm(Minecraft.getInstance().font);
        note(book.message());
    }

    /** The client side of {@link Books.Show}: open the book, or refresh it in place. */
    public static void show(Book book) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BookScreen open && open.book.kind().equals(book.kind())
                && open.book.context().equals(book.context())) {
            boolean sameTab = open.book.tab().equals(book.tab());
            open.book = book;
            open.note(book.message());
            if (!sameTab) {
                open.scroll = 0;
            }
            open.rebuildWidgets();
        } else {
            mc.setScreen(new BookScreen(book));
        }
    }

    private void note(String m) {
        if (!m.isEmpty()) {
            toast = resolve(m);
            toastUntil = System.currentTimeMillis() + 4000;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        pw = Math.min(460, width - 12);
        ph = Math.min(272, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        lw = Math.max(120, (int) (pw * 0.33));
        int bodyY = py + HEADER + TABS + 4;
        rx = px + lw + 14;
        ry = bodyY + 4;
        rw = px + pw - 8 - rx - 10;
        rh = py + ph - 10 - ry;
        field = new EditBox(font, 0, 0, 100, 16, Component.empty());
        field.setMaxLength(48);
        field.setVisible(false);
        addRenderableWidget(field);
    }

    // ================================================================ text

    /** Replaces {@code {item:<id>}} and {@code {lang:<key>|<fallback>}} with client-side names. */
    static String resolve(String s) {
        if (s.indexOf('{') < 0) {
            return s;
        }
        Matcher m = TOKEN.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String r;
            if (m.group(1).equals("item")) {
                r = item(m.group(2)).getDescription().getString();
            } else {
                r = I18n.exists(m.group(2)) ? I18n.get(m.group(2)) : m.group(3) == null ? m.group(2) : m.group(3);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(r));
        }
        m.appendTail(out);
        return out.toString();
    }

    static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
    }

    static int toneColor(int tone) {
        return UiTheme.ON_LIGHT[Math.max(0, Math.min(4, tone))];
    }

    static int barColor(int tone) {
        return switch (tone) {
            case El.GOOD -> 0xFF6FA84A;
            case El.WARN -> 0xFFD9A23A;
            case El.BAD -> 0xFFC8553D;
            case El.MUTED -> 0xFF9A8F80;
            default -> 0xFF8FB85A;
        };
    }

    // ================================================================ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g, mx, my, partial);
        ui.begin(g, mx, my, 0);
        fieldShown = false;
        fieldEl = null;

        // frame and header band
        ui.sprite(UiTheme.PANEL, px, py, pw, ph);
        ui.sprite(UiTheme.INSET_DARK, px + 6, py + 6, pw - 12, HEADER - 4);
        int accent = ACCENTS[Math.floorMod(book.accent(), ACCENTS.length)];
        g.fill(px + 8, py + HEADER + 1, px + pw - 8, py + HEADER + 3, accent);
        String title = resolve(book.title());
        g.pose().pushPose();
        g.pose().translate(px + 14, py + 10, 0);
        g.pose().scale(1.25f, 1.25f, 1);
        g.drawString(font, ui.fit(title, (int) ((pw * 0.5) / 1.25)), 0, 0, UiTheme.TITLE, true);
        g.pose().popPose();
        int titleW = (int) (font.width(ui.fit(title, (int) ((pw * 0.5) / 1.25))) * 1.25f);
        ui.text(resolve(book.subtitle()), px + 20 + titleW, py + 13, 0xFFC9B394);
        drawChips(g, mx, my);
        drawClose(g);

        // tabs
        drawTabs(g, mx, my);

        // left page
        int ly = py + HEADER + TABS + 4;
        int lh = py + ph - 8 - ly;
        ui.sprite(UiTheme.INSET_LIGHT, px + 8, ly, lw, lh);
        int innerX = px + 14;
        int innerW = lw - 12;
        leftHeight = layout(book.left(), innerX, 0, innerW, false);
        leftScroll = Math.max(0, Math.min(leftScroll, leftHeight - (lh - 12)));
        ui.clip(px + 9, ly + 1, lw - 2, lh - 2);
        layoutClipped(book.left(), innerX, ly + 6 - leftScroll, innerW, ly + 1, ly + lh - 1);
        ui.unclip();

        // right page
        ui.sprite(UiTheme.INSET, rx - 6, ry - 4, rw + 22, rh + 8);
        contentHeight = layout(book.right(), rx, 0, rw, false);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - rh)));
        ui.clip(rx - 5, ry - 3, rw + 20, rh + 6);
        layoutClipped(book.right(), rx, ry - scroll, rw, ry - 3, ry + rh + 3);
        ui.unclip();
        drawScrollBar(g);

        field.setVisible(fieldShown);
        super.render(g, mx, my, partial);

        // the answer to the last click
        if (!toast.isEmpty() && System.currentTimeMillis() < toastUntil) {
            int tw = Math.min(pw - 40, font.width(toast) + 16);
            int tx = px + (pw - tw) / 2;
            int ty = py + ph - 26;
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(tx, ty, tx + tw, ty + 16, 0xE0201812);
            g.fill(tx, ty + 15, tx + tw, ty + 16, accent);
            ui.text(toast, tx + 8, ty, tw - 16, 16, 0, 0xFFF3E7CF);
            g.pose().popPose();
        }
        ui.tooltip().ifPresent(t -> g.renderTooltip(font, font.split(t, 220), mx, my));
    }

    private void drawChips(GuiGraphics g, int mx, int my) {
        int x = px + pw - 30;
        for (int i = book.chips().size() - 1; i >= 0; i--) {
            El c = book.chips().get(i);
            String v = resolve(c.value());
            int w = 18 + font.width(v) + 6;
            x -= w;
            g.renderItem(new ItemStack(item(c.item())), x, py + 8);
            ui.text(v, x + 18, py + 13, 0xFFF3E7CF);
            if (mx >= x && mx < x + w && my >= py + 6 && my < py + HEADER) {
                ui.tooltip(Component.literal(resolve(c.text())));
            }
        }
    }

    private void drawClose(GuiGraphics g) {
        int s = 14;
        int x = px + pw - 8 - s - 4;
        int y = py + 9;
        boolean hover = ui.over(x, y, s, s);
        ui.sprite(hover ? UiTheme.BUTTON_HOVER : UiTheme.BUTTON, x, y, s, s);
        ui.text("x", x, y, s, s - 2, 0, UiTheme.DARK_TEXT);
        ui.hit(x, y, s, s, this::onClose);
    }

    private void drawTabs(GuiGraphics g, int mx, int my) {
        int x = px + 8;
        int y = py + HEADER + 4;
        int avail = pw - 16;
        int n = book.tabs().size();
        int natural = 0;
        for (Book.Tab t : book.tabs()) {
            natural += 22 + font.width(resolve(t.label())) + 4;
        }
        boolean labels = natural <= avail;
        for (Book.Tab t : book.tabs()) {
            boolean sel = t.id().equals(book.tab());
            String label = resolve(t.label());
            int w = labels || sel ? 22 + font.width(label) + 6 : 24;
            boolean hover = ui.over(x, y, w, TABS);
            ui.sprite(sel ? UiTheme.TAB_SELECTED : hover ? UiTheme.TAB_HOVER : UiTheme.TAB, x, y, w, TABS);
            g.pose().pushPose();
            g.pose().translate(x + 4, y + 2, 0);
            g.pose().scale(0.875f, 0.875f, 1);
            g.renderItem(new ItemStack(item(t.icon())), 0, 0);
            g.pose().popPose();
            if (labels || sel) {
                ui.text(label, x + 21, y + 5, sel ? UiTheme.DARK_TEXT : 0xFF6B4A2B);
            } else if (hover) {
                ui.tooltip(Component.literal(label));
            }
            if (!sel) {
                String id = t.id();
                ui.hit(x, y, w, TABS, () -> PacketDistributor.sendToServer(new Books.Open(book.kind(), book.context(),
                        id)));
            }
            x += w + 2;
            if (--n < 0) {
                break;
            }
        }
    }

    private void drawScrollBar(GuiGraphics g) {
        if (contentHeight <= rh) {
            return;
        }
        int x = rx + rw + 6;
        ui.sprite(UiTheme.SCROLL_TRACK, x, ry - 2, 8, rh + 4);
        int handle = Math.max(16, (rh + 4) * rh / contentHeight);
        int hy = ry - 2 + (int) ((rh + 4 - handle) * (scroll / (double) Math.max(1, contentHeight - rh)));
        ui.sprite(UiTheme.SCROLL_HANDLE, x, hy, 8, handle);
    }

    // ================================================================ layout

    private int layoutClipped(List<El> els, int x, int y, int w, int clipTop, int clipBottom) {
        int top = y;
        for (El e : els) {
            int h = element(e, x, y, w, false, clipTop, clipBottom);
            boolean visible = y + h >= clipTop && y <= clipBottom;
            if (visible) {
                element(e, x, y, w, true, clipTop, clipBottom);
            }
            y += h;
        }
        return y - top;
    }

    /** Measures (draw false) or draws the elements; returns their height. */
    private int layout(List<El> els, int x, int y, int w, boolean draw) {
        int top = y;
        for (El e : els) {
            y += element(e, x, y, w, draw, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        return y - top;
    }

    private boolean live(int y, int h, int clipTop, int clipBottom) {
        return y >= clipTop - 1 && y + h <= clipBottom + 1 && ui.mouseY >= clipTop && ui.mouseY <= clipBottom;
    }

    private int element(El e, int x, int y, int w, boolean draw, int clipTop, int clipBottom) {
        GuiGraphics g = ui.g;
        switch (e.kind()) {
            case "head" -> {
                if (draw) {
                    ui.text(ui.fit(resolve(e.text()), w), x, y + 5, UiTheme.DARK_TEXT);
                    g.fill(x, y + 15, x + w, y + 16, 0x60603A1C);
                }
                return 20;
            }
            case "pair" -> {
                boolean icon = !e.item().isEmpty();
                int h = icon ? 18 : 12;
                if (draw) {
                    int tx = x;
                    if (icon) {
                        g.pose().pushPose();
                        g.pose().translate(x, y + 1, 0);
                        g.pose().scale(0.875f, 0.875f, 1);
                        g.renderItem(new ItemStack(item(e.item())), 0, 0);
                        g.pose().popPose();
                        tx += 18;
                    }
                    String value = resolve(e.value());
                    int vw = Math.min(font.width(value), (int) ((w - (tx - x)) * 0.6));
                    String label = ui.fit(resolve(e.text()), w - (tx - x) - vw - 6);
                    boolean click = !e.action().isEmpty() && live(y, h, clipTop, clipBottom);
                    boolean hover = ui.over(x, y, w, h) && click;
                    int ty = y + (h - 8) / 2;
                    ui.text(label, tx, ty, hover ? 0xFF8A4A1A : e.tone() == El.MUTED ? toneColor(El.MUTED)
                            : UiTheme.DARK_TEXT);
                    ui.text(ui.fit(value, vw), x + w - vw, ty, toneColor(e.tone() == El.NORMAL ? El.MUTED : e.tone()));
                    if (hover) {
                        g.fill(tx, ty + 9, tx + font.width(label), ty + 10, 0xFF8A4A1A);
                        ui.hit(x, y, w, h, () -> click(e.action(), e.arg(), 0));
                    }
                    if (!e.why().isEmpty() && ui.over(x, y, w, h) && live(y, h, clipTop, clipBottom)) {
                        ui.tooltip(Component.literal(resolve(e.why())));
                    }
                }
                return h;
            }
            case "meter" -> {
                if (draw) {
                    meter(e, x, y, w);
                    if (!e.why().isEmpty() && ui.over(x, y, w, 20) && live(y, 20, clipTop, clipBottom)) {
                        ui.tooltip(Component.literal(resolve(e.why())));
                    }
                }
                return 21;
            }
            case "text" -> {
                List<FormattedCharSequence> lines = font.split(Component.literal(resolve(e.text())), w);
                if (draw) {
                    int ly = y + 2;
                    for (FormattedCharSequence l : lines) {
                        g.drawString(font, l, x, ly, toneColor(e.tone()), false);
                        ly += LINE - 1;
                    }
                }
                return lines.size() * (LINE - 1) + 5;
            }
            case "gap" -> {
                return GAP;
            }
            case "card" -> {
                return card(e, x, y, w, draw, clipTop, clipBottom);
            }
            case "buttons" -> {
                return row(e.children(), x, y, w, draw, clipTop, clipBottom);
            }
            case "button", "stepper", "tag" -> {
                return row(List.of(e), x, y, w, draw, clipTop, clipBottom);
            }
            case "items" -> {
                return items(e.children(), x, y, w, draw, clipTop, clipBottom);
            }
            case "field" -> {
                if (draw) {
                    ui.text(resolve(e.text()), x, y + 4, toneColor(El.MUTED));
                    int fx = x + font.width(resolve(e.text())) + 6;
                    int fw = Math.max(60, w - (fx - x) - 50);
                    if (y >= clipTop && y + 16 <= clipBottom && fieldEl == null) {
                        fieldEl = e;
                        fieldShown = e.enabled();
                        field.setX(fx);
                        field.setY(y);
                        field.setWidth(fw);
                    }
                    El send = El.button("Send", e.action(), "", e.enabled(), e.why());
                    button(send, fx + fw + 4, y, 44, 16, live(y, 16, clipTop, clipBottom), () -> {
                        click(e.action(), field.getValue(), 0);
                        field.setValue("");
                    });
                }
                return 20;
            }
            default -> {
                return 0;
            }
        }
    }

    private void meter(El e, int x, int y, int w) {
        GuiGraphics g = ui.g;
        int tx = x;
        if (!e.item().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x, y + 2, 0);
            g.pose().scale(0.875f, 0.875f, 1);
            g.renderItem(new ItemStack(item(e.item())), 0, 0);
            g.pose().popPose();
            tx += 18;
        }
        String value = resolve(e.value());
        int vw = font.width(value);
        ui.text(ui.fit(resolve(e.text()), w - (tx - x) - vw - 6), tx, y + 2, UiTheme.DARK_TEXT);
        ui.text(value, x + w - vw, y + 2, toneColor(El.MUTED));
        bar(tx, y + 12, w - (tx - x), BAR, e.fill(), e.mark(), e.tone());
    }

    private void bar(int x, int y, int w, int h, float fill, float mark, int tone) {
        GuiGraphics g = ui.g;
        g.fill(x, y, x + w, y + h, 0xFF5A4030);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFE9DCC0);
        int f = Math.round((w - 2) * Math.max(0, Math.min(1, fill)));
        int c = barColor(tone);
        g.fill(x + 1, y + 1, x + 1 + f, y + h - 1, c);
        if (h > 3) {
            g.fill(x + 1, y + 1, x + 1 + f, y + 2, 0x40FFFFFF);
        }
        if (mark >= 0) {
            int m = x + 1 + Math.round((w - 2) * mark);
            g.fill(m, y - 1, m + 1, y + h + 1, 0xFF3A2414);
        }
    }

    // ---------------------------------------------------------------- cards

    private int cardHeight(El e, int w) {
        int textW = w - 40;
        int h = 30;
        int meters = 0;
        int tagsW = 0;
        int tagRows = 0;
        for (El c : e.children()) {
            if (c.kind().equals("meter")) {
                meters++;
            } else if (c.kind().equals("tag")) {
                int tw = font.width(resolve(c.text())) + 8;
                if (tagRows == 0 || tagsW + tw > textW) {
                    tagRows++;
                    tagsW = 0;
                }
                tagsW += tw + 3;
            }
        }
        int perRow = textW >= 200 ? 2 : 1;
        int meterRows = (meters + perRow - 1) / perRow;
        h = Math.max(h, 26 + meterRows * 10 + tagRows * 13 + 4);
        return h;
    }

    private int card(El e, int x, int y, int w, boolean draw, int clipTop, int clipBottom) {
        int h = cardHeight(e, w);
        if (!draw) {
            return h + 3;
        }
        GuiGraphics g = ui.g;
        boolean clickable = !e.action().isEmpty() && live(y, h, clipTop, clipBottom);
        boolean hover = clickable && ui.over(x, y, w, h);
        ui.sprite(hover ? UiTheme.ROW_HOVER : UiTheme.INSET_LIGHT, x, y, w, h);
        if (e.tone() == El.BAD || e.tone() == El.WARN || e.tone() == El.GOOD) {
            int c = barColor(e.tone());
            g.fill(x, y + 2, x + 2, y + h - 2, c);
        }
        face(e.face(), x + 5, y + 4, 22);
        // buttons right-aligned on the first line
        List<El> buttons = new ArrayList<>();
        List<El> meters = new ArrayList<>();
        List<El> tags = new ArrayList<>();
        for (El c : e.children()) {
            switch (c.kind()) {
                case "button", "stepper" -> buttons.add(c);
                case "meter" -> meters.add(c);
                case "tag" -> tags.add(c);
                default -> { }
            }
        }
        int bx = x + w - 4;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            El b = buttons.get(i);
            int bw = Math.max(44, font.width(resolve(b.text())) + 12);
            bx -= bw;
            button(b, bx, y + 5, bw, 16, live(y, h, clipTop, clipBottom), () -> click(b.action(), b.arg(), b.num()));
            bx -= 3;
        }
        int tx = x + 32;
        int textW = bx - tx - 2;
        int titleColor = hover ? 0xFF8A4A1A : UiTheme.DARK_TEXT;
        ui.text(ui.fit(resolve(e.text()), textW), tx, y + 5, titleColor);
        ui.text(ui.fit(resolve(e.value()), textW), tx, y + 15, toneColor(El.MUTED));
        int fullW = x + w - 6 - tx;
        int perRow = fullW >= 200 ? 2 : 1;
        int colW = (fullW - (perRow - 1) * 8) / perRow;
        int my = y + 27;
        for (int i = 0; i < meters.size(); i++) {
            El m = meters.get(i);
            int cx = tx + (i % perRow) * (colW + 8);
            int cy = my + (i / perRow) * 10;
            String label = resolve(m.text());
            int labelW = Math.min(60, font.width(label));
            ui.text(ui.fit(label, 60), cx, cy, toneColor(El.MUTED));
            String v = resolve(m.value());
            int vw = font.width(v);
            ui.text(v, cx + colW - vw, cy, toneColor(El.MUTED));
            bar(cx + labelW + 4, cy + 2, Math.max(10, colW - labelW - vw - 10), 5, m.fill(), m.mark(), m.tone());
        }
        int ty = my + ((meters.size() + perRow - 1) / perRow) * 10;
        int tagX = tx;
        for (El t : tags) {
            String s = resolve(t.text());
            int tw = font.width(s) + 8;
            if (tagX + tw > x + w - 6) {
                tagX = tx;
                ty += 13;
            }
            ui.sprite(UiTheme.INSET_DARK, tagX, ty, tw, 12);
            ui.text(s, tagX + 4, ty + 2, 0xFFF3E7CF);
            tagX += tw + 3;
        }
        if (hover) {
            ui.hit(x, y, w, h, () -> click(e.action(), e.arg(), 0));
        }
        return h + 3;
    }

    /** A face: a skin ("tex|64": the face and hat layer) or an item. */
    private void face(String face, int x, int y, int s) {
        GuiGraphics g = ui.g;
        if (face.isEmpty()) {
            g.fill(x, y, x + s, y + s, 0xFF8A6F52);
            return;
        }
        String[] parts = face.split("\\|");
        if (face.contains("textures/") || parts.length > 1) {
            ResourceLocation tex = ResourceLocation.tryParse(parts[0]);
            if (tex == null) {
                return;
            }
            int texH = parts.length > 1 ? 64 : 32;
            g.fill(x - 1, y - 1, x + s + 1, y + s + 1, 0xFF3A2414);
            g.blit(tex, x, y, s, s, 8, 8, 8, 8, 64, texH);
            RenderSystem.enableBlend();
            g.blit(tex, x, y, s, s, 40, 8, 8, 8, 64, texH);
            RenderSystem.disableBlend();
            return;
        }
        g.renderItem(new ItemStack(item(face)), x + (s - 16) / 2, y + (s - 16) / 2);
    }

    // ---------------------------------------------------------------- rows of buttons

    private int widthOf(El b) {
        String label = resolve(b.text());
        return switch (b.kind()) {
            case "stepper" -> font.width(label) + 6 + 14 + Math.max(28, font.width(resolve(b.value())) + 8) + 14;
            case "tag" -> font.width(label) + 8;
            default -> Math.max(40, font.width(label) + 14);
        };
    }

    private int row(List<El> items, int x, int y, int w, boolean draw, int clipTop, int clipBottom) {
        int cx = x;
        int cy = y + 2;
        int rowH = 16;
        for (El b : items) {
            int bw = Math.min(w, widthOf(b));
            int h = b.kind().equals("tag") ? 12 : 16;
            if (cx > x && cx + bw > x + w) {
                cx = x;
                cy += rowH + 3;
            }
            if (draw) {
                boolean live = live(cy, h, clipTop, clipBottom);
                switch (b.kind()) {
                    case "stepper" -> stepper(b, cx, cy, bw, live);
                    case "tag" -> {
                        ui.sprite(UiTheme.INSET_DARK, cx, cy + 2, bw, 12);
                        ui.text(resolve(b.text()), cx + 4, cy + 4, 0xFFF3E7CF);
                    }
                    default -> button(b, cx, cy, bw, 16, live, () -> click(b.action(), b.arg(), b.num()));
                }
            }
            cx += bw + 4;
        }
        return cy + rowH + 3 - y;
    }

    private void button(El b, int x, int y, int w, int h, boolean live, Runnable run) {
        boolean enabled = b.enabled() && !b.action().isEmpty();
        boolean hover = live && ui.over(x, y, w, h);
        ui.sprite(!enabled ? UiTheme.BUTTON_DISABLED : hover ? UiTheme.BUTTON_HOVER : UiTheme.BUTTON, x, y, w, h);
        int color = !enabled ? UiTheme.DISABLED_TEXT : b.tone() == El.GOOD ? 0xFF2F6A1E : b.tone() == El.BAD
                ? 0xFF9A2A1A : UiTheme.DARK_TEXT;
        ui.text(resolve(b.text()), x + 3, y, w - 6, h - 2, 0, color);
        if (hover && !b.why().isEmpty()) {
            ui.tooltip(Component.literal(resolve(b.why())));
        }
        if (enabled && live) {
            ui.hit(x, y, w, h, run);
        }
    }

    private void stepper(El s, int x, int y, int w, boolean live) {
        String label = resolve(s.text());
        int lw = font.width(label);
        ui.text(label, x, y + 4, UiTheme.DARK_TEXT);
        int ax = x + lw + 6;
        int vw = w - (ax - x) - 28;
        boolean canDown = s.enabled() && s.num() > s.min();
        boolean canUp = s.enabled() && s.num() < s.max();
        El down = El.button("<", "x", "", canDown, s.why());
        El up = El.button(">", "x", "", canUp, s.why());
        int step = s.step();
        button(down, ax, y, 14, 16, live, () -> click(s.action(), s.arg(), Math.max(s.min(), s.num()
                - (hasShiftDown() ? step * 10 : step))));
        ui.sprite(UiTheme.INSET_LIGHT, ax + 14, y, vw, 16);
        ui.text(resolve(s.value()), ax + 14, y, vw, 16, 0, UiTheme.DARK_TEXT);
        button(up, ax + 14 + vw, y, 14, 16, live, () -> click(s.action(), s.arg(), Math.min(s.max(), s.num()
                + (hasShiftDown() ? step * 10 : step))));
        if (live && ui.over(ax, y, vw + 28, 16) && s.enabled()) {
            ui.tooltip(Component.literal("Shift-click for 10 at a time"));
        }
    }

    // ---------------------------------------------------------------- item grids

    private int items(List<El> stacks, int x, int y, int w, boolean draw, int clipTop, int clipBottom) {
        int cell = 20;
        int cols = Math.max(1, w / cell);
        int rows = (stacks.size() + cols - 1) / cols;
        if (draw) {
            GuiGraphics g = ui.g;
            for (int i = 0; i < stacks.size(); i++) {
                El s = stacks.get(i);
                int cx = x + (i % cols) * cell;
                int cy = y + 2 + (i / cols) * cell;
                g.fill(cx, cy, cx + 18, cy + 18, 0x30603A1C);
                ItemStack st = new ItemStack(item(s.item()));
                g.renderItem(st, cx + 1, cy + 1);
                g.renderItemDecorations(font, st, cx + 1, cy + 1, count(s.num()));
                if (ui.over(cx, cy, 18, 18) && live(cy, 18, clipTop, clipBottom)) {
                    ui.tooltip(Component.literal(st.getHoverName().getString() + " x" + s.num()));
                }
            }
        }
        return rows * cell + 4;
    }

    static String count(int n) {
        return n >= 10000 ? n / 1000 + "k" : n >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fk", n / 1000.0)
                : Integer.toString(n);
    }

    // ================================================================ input

    private void click(String action, String arg, int num) {
        if (action.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new Books.Click(book.kind(), book.context(), book.tab(), action, arg, num));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (fieldShown && field.isMouseOver(mx, my)) {
                return super.mouseClicked(mx, my, button);
            }
            field.setFocused(false);
            int sx = rx + rw + 6;
            if (contentHeight > rh && mx >= sx && mx < sx + 8 && my >= ry - 2 && my < ry + rh + 2) {
                dragging = true;
                dragTo(my);
                return true;
            }
            if (ui.click(mx, my)) {
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                        .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    private void dragTo(double my) {
        int handle = Math.max(16, (rh + 4) * rh / Math.max(1, contentHeight));
        double f = (my - ry - handle / 2.0) / Math.max(1, rh + 4 - handle);
        scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * Math.max(0, contentHeight - rh));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < rx - 8) {
            leftScroll = Math.max(0, leftScroll - (int) Math.signum(sy) * 20);
        } else {
            scroll = Math.max(0, Math.min(Math.max(0, contentHeight - rh), scroll - (int) Math.signum(sy) * 24));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (fieldShown && field.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (fieldEl != null) {
                    click(fieldEl.action(), field.getValue(), 0);
                    field.setValue("");
                }
                return true;
            }
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                return field.keyPressed(key, scan, mods) || true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        renderTransparentBackground(g);
    }

    Optional<Book> book() {
        return Optional.ofNullable(book);
    }
}
