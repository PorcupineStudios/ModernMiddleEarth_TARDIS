package com.porcupinestudios.lotrport.client.book;

import com.mojang.blaze3d.systems.RenderSystem;
import com.porcupinestudios.lotrport.client.ui.Imm;
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
 * Every colony book (plaques, the Town Hall and Treasury ledgers, the Customs Counter) in one screen, drawn as
 * RingRealms' book screens were (port of its 1.7.10 {@code client/ui/BookScreen}): LOTR's Red Book open on two pages,
 * the title and chips heading the left page over its overview, the open tab on the right page (scrolling), ribbon tabs
 * down the right edge, and an info strip at the foot of the page that says what the mouse is over (no tooltips, no
 * text over the world). Scaled down to fit small windows. It only draws the {@link Book} the server sent and sends
 * clicks back; the server answers with the book afresh.
 */
public class BookScreen extends Screen {
    private static final Pattern TOKEN = Pattern.compile("\\{(item|lang):([^}|]*)(?:\\|([^}]*))?}");
    static final ResourceLocation BOOK = com.porcupinestudios.lotrport.LotrPort.id("textures/gui/quest/red_book.png");
    static final int BW = 420;
    static final int BH = 256;
    static final int TEX = 512;
    static final int LX = 18;
    static final int RX = 224;
    static final int PAGE_W = 176;
    static final int TOP = 14;
    static final int BOTTOM = 238;
    static final int INFO_H = 32;
    static final int HEAD_H = 38;
    static final int INK = 0xFF3B2614;
    static final int RED = 0xFF7A1D10;
    static final int DIM = 0xFF7D6448;
    static final int GREEN = 0xFF3F6B22;
    static final int GOLD = 0xFF8C5A10;
    static final int PALE = 0xFFF2E6C8;
    private static final int[] RIBBONS = {0xFF8D1B14, 0xFF2F5A2A, 0xFF2B4A78, 0xFF6B4A1E};
    private static final java.util.Map<ResourceLocation, Boolean> TEXTURES = new java.util.HashMap<>();
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
    private String fieldOwner = "";

    // the book's place on screen and the right page, measured in init
    private float scale = 1;
    private int ox;
    private int oy;
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

    /** Turns the open book to a tab, as clicking its ribbon does (dev scripts). */
    public static void turnTo(String tab) {
        if (Minecraft.getInstance().screen instanceof BookScreen open) {
            PacketDistributor.sendToServer(new Books.Open(open.book.kind(), open.book.context(), tab));
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
        // fit the book (and the ribbons sticking out) into small windows by scaling it down
        int out = ribbonOut();
        scale = Math.min(1f, Math.min((height - 4) / (float) BH, (width - 4) / (float) (BW + out + 4)));
        ox = Math.round((width / scale - BW - out) / 2f);
        oy = Math.round((height / scale - BH) / 2f);
        rx = RX;
        ry = TOP;
        rw = PAGE_W - 6;
        rh = BOTTOM - INFO_H - 4 - TOP;
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
        return switch (tone) {
            case El.MUTED -> DIM;
            case El.GOOD -> GREEN;
            case El.WARN -> GOLD;
            case El.BAD -> RED;
            default -> INK;
        };
    }

    private int bookX(double mx) {
        return (int) Math.floor(mx / scale) - ox;
    }

    private int bookY(double my) {
        return (int) Math.floor(my / scale) - oy;
    }

    /**
     * Clips to a box in book units. GuiGraphics' scissor is in screen units and ignores the pose (the book is moved and
     * scaled by it), so the box is turned into screen units here; clipping in book units cut away whole pages.
     */
    private void clip(int x, int y, int w, int h) {
        ui.g.enableScissor((int) Math.floor((ox + x) * scale), (int) Math.floor((oy + y) * scale),
                (int) Math.ceil((ox + x + w) * scale), (int) Math.ceil((oy + y + h) * scale));
    }

    /** RingRealms' dotted rule under titles. */
    private void dots(int x, int y, int w) {
        for (int i = 0; i < w; i += 2) {
            ui.g.fill(x + i, y, x + i + 1, y + 1, 0xFFB39C72);
        }
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
        // dim the world once, under the book (Screen.render would dim again over it)
        renderTransparentBackground(g);
        int bx = bookX(mx);
        int by = bookY(my);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.pose().translate(ox, oy, 0);
        ui.begin(g, bx, by, 0);
        fieldShown = false;
        fieldEl = null;
        RenderSystem.enableBlend();
        g.blit(BOOK, 0, 0, 0, 0, BW, BH, TEX, TEX);
        drawRibbons(g);

        // left page: the title (red, a dotted rule under it), the subtitle, the chips, then the overview
        String title = resolve(book.title());
        ui.text(ui.fit(title, PAGE_W), LX, TOP, RED);
        dots(LX, TOP + 10, PAGE_W);
        ui.text(ui.fit(resolve(book.subtitle()), PAGE_W), LX, TOP + 13, DIM);
        drawChips(g);
        int ly = TOP + HEAD_H;
        int lh = BOTTOM - INFO_H - 4 - ly;
        leftHeight = layout(book.left(), LX, 0, PAGE_W, false);
        leftScroll = Math.max(0, Math.min(leftScroll, leftHeight - lh));
        clip(LX - 2, ly, PAGE_W + 4, lh);
        layoutClipped(book.left(), LX, ly - leftScroll, PAGE_W, ly, ly + lh);
        g.disableScissor();

        // right page: the open tab
        contentHeight = layout(book.right(), rx, 0, rw, false);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - rh)));
        clip(rx - 2, ry, rw + 4, rh);
        layoutClipped(book.right(), rx, ry - scroll, rw, ry, ry + rh);
        g.disableScissor();
        drawScrollBar(g);
        drawInfo(g, bx);
        g.pose().popPose();

        field.setVisible(fieldShown);
        super.render(g, mx, my, partial);
    }

    /** The info strip at the foot of a page: what the mouse is over, else the answer to the last click. */
    private void drawInfo(GuiGraphics g, int bx) {
        Optional<Component> over = ui.tooltip();
        String info;
        boolean left;
        if (over.isPresent()) {
            info = over.get().getString();
            left = bx < (LX + PAGE_W + RX) / 2;
        } else if (!toast.isEmpty() && System.currentTimeMillis() < toastUntil) {
            info = toast;
            left = false;
        } else {
            return;
        }
        int x = left ? LX : RX;
        int top = BOTTOM - INFO_H;
        g.fill(x - 2, top - 2, x + PAGE_W + 2, top - 1, 0x40806040);
        int y = top + 1;
        boolean first = true;
        for (String para : info.split("\n")) {
            for (FormattedCharSequence l : font.split(Component.literal(para), PAGE_W)) {
                if (y > BOTTOM - 8) {
                    return;
                }
                g.drawString(font, l, x, y, over.isEmpty() ? RED : first ? INK : DIM, false);
                first = false;
                y += 10;
            }
        }
    }

    /** The header chips (coins, people...) in a row under the subtitle; hover says what each is. */
    private void drawChips(GuiGraphics g) {
        int x = LX;
        int y = TOP + 24;
        for (El c : book.chips()) {
            String v = resolve(c.value());
            int w = 13 + font.width(v) + 8;
            if (x + w > LX + PAGE_W) {
                break;
            }
            g.pose().pushPose();
            g.pose().translate(x, y - 2, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.renderItem(new ItemStack(item(c.item())), 0, 0);
            g.pose().popPose();
            ui.text(v, x + 13, y + 1, INK);
            if (ui.over(x, y - 2, w, 12)) {
                ui.tooltip(Component.literal(resolve(c.text())));
            }
            x += w;
        }
    }

    private static final int RIBBON_H = 18;

    /** How far the ribbons stick out past the book: room for the longest tab name, so no name is ever cut. */
    private int ribbonOut() {
        int w = 0;
        for (Book.Tab t : book.tabs()) {
            w = Math.max(w, font.width(resolve(t.label())));
        }
        return book.tabs().isEmpty() ? 0 : w + 30;
    }

    /**
     * Ribbon tabs down the right edge (RingRealms' bookmarks), each in its own colour with its icon and whole name
     * written level; the open one sticks out further and joins the page.
     */
    private void drawRibbons(GuiGraphics g) {
        List<Book.Tab> tabs = book.tabs();
        if (tabs.isEmpty()) {
            return;
        }
        int out = ribbonOut();
        int step = Math.min(RIBBON_H + 4, (BH - 24) / tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            Book.Tab t = tabs.get(i);
            boolean on = t.id().equals(book.tab());
            int x = BW - 8;
            int y = 18 + i * step;
            int h = Math.min(RIBBON_H, step - 2);
            boolean hover = !on && ui.over(x, y, out + 8, h);
            int w = out + (on ? 8 : hover ? 5 : 2);
            int colour = RIBBONS[i % RIBBONS.length];
            g.fill(x, y, x + w, y + h, colour);
            // a darker edge and a swallowtail notch, like a ribbon
            g.fill(x, y + h - 1, x + w, y + h, 0x60000000);
            g.fill(x + w - 3, y + h / 2 - 1, x + w, y + h / 2 + 1, 0xFF000000 | (colour & 0x00FFFFFF) >> 1 & 0x7F7F7F);
            if (on) {
                g.fill(x, y - 1, x + w, y, 0xFFE8C66A);
            }
            g.pose().pushPose();
            g.pose().translate(x + 10, y + (h - 12) / 2f, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.renderItem(new ItemStack(item(t.icon())), 0, 0);
            g.pose().popPose();
            g.drawString(font, resolve(t.label()), x + 24, y + (h - 8) / 2, PALE, false);
            if (!on) {
                String id = t.id();
                ui.hit(x, y, out + 8, h, () -> PacketDistributor.sendToServer(new Books.Open(book.kind(),
                        book.context(), id)));
            }
        }
    }

    private void drawScrollBar(GuiGraphics g) {
        if (contentHeight <= rh) {
            return;
        }
        int x = rx + rw + 3;
        GuiGraphics gg = ui.g;
        gg.fill(x + 1, ry, x + 2, ry + rh, 0x40806040);
        int handle = Math.max(16, rh * rh / contentHeight);
        int hy = ry + (int) ((rh - handle) * (scroll / (double) Math.max(1, contentHeight - rh)));
        gg.fill(x, hy, x + 3, hy + handle, 0xFF8D6A44);
    }

    // ================================================================ layout

    /**
     * Splits a page into its parts: everything before the first heading, then each heading with what follows it up to
     * the next heading. Each headed part is drawn in its own outlined box, so separate things read as separate.
     */
    private static List<List<El>> groups(List<El> els) {
        List<List<El>> out = new ArrayList<>();
        List<El> cur = new ArrayList<>();
        for (El e : els) {
            if (e.kind().equals("head") && !cur.isEmpty()) {
                out.add(cur);
                cur = new ArrayList<>();
            }
            cur.add(e);
        }
        if (!cur.isEmpty()) {
            out.add(cur);
        }
        return out;
    }

    private static final int BOX_PAD = 4;
    private static final int CAPTION = 14;

    /** One part of a page: a headed part in an outlined box with its heading as the caption, else plain. */
    private int group(List<El> part, int x, int y, int w, boolean draw, int clipTop, int clipBottom) {
        if (!part.get(0).kind().equals("head")) {
            int h = 0;
            for (El e : part) {
                h += element(e, x, y + h, w, draw, clipTop, clipBottom);
            }
            return h + (h > 0 ? 3 : 0);
        }
        int ix = x + BOX_PAD;
        int iw = w - 2 * BOX_PAD;
        int inner = 0;
        for (El e : part.subList(1, part.size())) {
            inner += element(e, ix, y + CAPTION + 2 + inner, iw, false, clipTop, clipBottom);
        }
        int h = CAPTION + 2 + inner + BOX_PAD;
        if (draw) {
            GuiGraphics g = ui.g;
            g.fill(x, y, x + w, y + h, 0x16806040);
            g.fill(x, y, x + w, y + CAPTION, 0x24806040);
            g.renderOutline(x, y, w, h, 0x90806040);
            g.fill(x + 1, y + CAPTION, x + w - 1, y + CAPTION + 1, 0x60806040);
            ui.text(ui.fit(resolve(part.get(0).text()), iw), ix, y + 3, RED);
            int cy = y + CAPTION + 2;
            for (El e : part.subList(1, part.size())) {
                cy += element(e, ix, cy, iw, true, clipTop, clipBottom);
            }
        }
        return h + 5;
    }

    private int layoutClipped(List<El> els, int x, int y, int w, int clipTop, int clipBottom) {
        int top = y;
        for (List<El> part : groups(els)) {
            int h = group(part, x, y, w, false, clipTop, clipBottom);
            if (y + h >= clipTop && y <= clipBottom) {
                group(part, x, y, w, true, clipTop, clipBottom);
            }
            y += h;
        }
        return y - top;
    }

    /** Measures (draw false) or draws the elements; returns their height. */
    private int layout(List<El> els, int x, int y, int w, boolean draw) {
        int top = y;
        for (List<El> part : groups(els)) {
            y += group(part, x, y, w, draw, Integer.MIN_VALUE, Integer.MAX_VALUE);
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
                    ui.text(ui.fit(resolve(e.text()), w), x, y + 5, RED);
                    dots(x, y + 15, w);
                }
                return 20;
            }
            case "pair" -> {
                boolean icon = !e.item().isEmpty();
                int indent = icon ? 18 : 0;
                String value = resolve(e.value());
                String labelText = resolve(e.text());
                // too long for one line: the label on its own line, the value right-aligned under it
                boolean two = !value.isEmpty() && font.width(labelText) + font.width(value) + 8 > w - indent;
                int h = two ? 22 : icon ? 18 : 12;
                if (draw) {
                    int tx = x + indent;
                    if (icon) {
                        g.pose().pushPose();
                        g.pose().translate(x, y + 1, 0);
                        g.pose().scale(0.875f, 0.875f, 1);
                        g.renderItem(new ItemStack(item(e.item())), 0, 0);
                        g.pose().popPose();
                    }
                    int vw = two ? Math.min(font.width(value), w - indent)
                            : Math.min(font.width(value), (int) ((w - indent) * 0.6));
                    String label = ui.fit(labelText, two ? w - indent : w - indent - vw - 6);
                    boolean click = !e.action().isEmpty() && live(y, h, clipTop, clipBottom);
                    boolean hover = ui.over(x, y, w, h) && click;
                    int ty = two ? y + 2 : y + (h - 8) / 2;
                    ui.text(label, tx, ty, hover ? RED : e.tone() == El.MUTED ? DIM : INK);
                    ui.text(ui.fit(value, vw), x + w - vw, two ? ty + 11 : ty,
                            toneColor(e.tone() == El.NORMAL ? El.MUTED : e.tone()));
                    if (hover) {
                        g.fill(tx, ty + 9, tx + font.width(label), ty + 10, RED);
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
            case "grid" -> {
                return grid(e.children(), x, y, w, draw, clipTop, clipBottom);
            }
            case "field" -> {
                if (draw) {
                    ui.text(resolve(e.text()), x, y + 4, DIM);
                    int fx = x + font.width(resolve(e.text())) + 6;
                    int fw = Math.max(60, w - (fx - x) - 50);
                    if (y >= clipTop && y + 16 <= clipBottom && fieldEl == null) {
                        // a field shows what it holds (a building's name) until the player types
                        String owner = book.kind() + "|" + book.context() + "|" + e.action();
                        if (!owner.equals(fieldOwner)) {
                            fieldOwner = owner;
                            field.setValue(e.value());
                        }
                        fieldEl = e;
                        fieldShown = e.enabled();
                        field.setX(Math.round((ox + fx) * scale));
                        field.setY(Math.round((oy + y) * scale));
                        field.setWidth(Math.round(fw * scale));
                        field.setHeight(Math.max(10, Math.round(16 * scale)));
                    }
                    El send = El.button(e.action().equals("rename") ? "Save" : "Send", e.action(), "", e.enabled(),
                            e.why());
                    button(send, fx + fw + 4, y, 44, 16, live(y, 16, clipTop, clipBottom), () -> {
                        click(e.action(), field.getValue(), 0);
                        if (!e.action().equals("rename")) {
                            field.setValue("");
                        }
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
        ui.text(ui.fit(resolve(e.text()), w - (tx - x) - vw - 6), tx, y + 2, INK);
        ui.text(value, x + w - vw, y + 2, toneColor(El.MUTED));
        bar(tx, y + 12, w - (tx - x), BAR, e.fill(), e.mark(), e.tone());
    }

    private void bar(int x, int y, int w, int h, float fill, float mark, int tone) {
        GuiGraphics g = ui.g;
        g.fill(x, y, x + w, y + h, 0xFF8D6A44);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFD8C8A4);
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
        g.fill(x, y, x + w, y + h, hover ? 0x30806040 : 0x18806040);
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
        int titleColor = hover ? RED : INK;
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
            g.fill(tagX, ty, tagX + tw, ty + 12, 0x40806040);
            ui.text(s, tagX + 4, ty + 2, INK);
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
                        ui.g.fill(cx, cy + 2, cx + bw, cy + 14, 0x40806040);
                        ui.text(resolve(b.text()), cx + 4, cy + 4, toneColor(b.tone()));
                    }
                    default -> button(b, cx, cy, bw, 16, live, () -> click(b.action(), b.arg(), b.num()));
                }
            }
            cx += bw + 4;
        }
        return cy + rowH + 3 - y;
    }

    /** RingRealms' Red Book button: three slices of the book sheet's bar (ends kept, middle stretched). */
    private void button(El b, int x, int y, int w, int h, boolean live, Runnable run) {
        boolean enabled = b.enabled() && !b.action().isEmpty();
        boolean hover = live && ui.over(x, y, w, h);
        GuiGraphics g = ui.g;
        int v = 256 + (enabled ? 2 : 1) * 20;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, enabled ? 1f : 0.6f);
        g.blit(BOOK, x, y, 3, h, 170, v, 3, 20, TEX, TEX);
        g.blit(BOOK, x + 3, y, w - 6, h, 173, v, 194, 20, TEX, TEX);
        g.blit(BOOK, x + w - 3, y, 3, h, 367, v, 3, 20, TEX, TEX);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int color = !enabled ? 0xFF9C8A70 : hover ? RED : b.tone() == El.GOOD ? GREEN : b.tone() == El.BAD ? RED : INK;
        ui.text(resolve(b.text()), x + 3, y, w - 6, h - 1, 0, color);
        if (hover && !b.why().isEmpty()) {
            ui.tooltip(Component.literal(resolve(b.why())));
        } else if (ui.over(x, y, w, h) && !enabled && !b.why().isEmpty()) {
            ui.tooltip(Component.literal(resolve(b.why())));
        }
        if (enabled && live) {
            ui.hit(x, y, w, h, run);
        }
    }

    private void stepper(El s, int x, int y, int w, boolean live) {
        String label = resolve(s.text());
        int lw = font.width(label);
        ui.text(label, x, y + 4, INK);
        int ax = x + lw + 6;
        int vw = w - (ax - x) - 28;
        boolean canDown = s.enabled() && s.num() > s.min();
        boolean canUp = s.enabled() && s.num() < s.max();
        El down = El.button("<", "x", "", canDown, s.why());
        El up = El.button(">", "x", "", canUp, s.why());
        int step = s.step();
        button(down, ax, y, 14, 16, live, () -> click(s.action(), s.arg(), Math.max(s.min(), s.num()
                - (hasShiftDown() ? step * 10 : step))));
        ui.g.fill(ax + 14, y + 1, ax + 14 + vw, y + 15, 0x20806040);
        ui.text(resolve(s.value()), ax + 14, y, vw, 16, 0, INK);
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

    /** RingRealms' icon grid (building types): cells of 20, selected shaded, faded when muted, hover says what. */
    private int grid(List<El> cells, int x, int y, int w, boolean draw, int clipTop, int clipBottom) {
        int cell = 20;
        int cols = Math.max(1, w / cell);
        int rows = (cells.size() + cols - 1) / cols;
        if (draw) {
            GuiGraphics g = ui.g;
            for (int i = 0; i < cells.size(); i++) {
                El c = cells.get(i);
                int cx = x + (i % cols) * cell;
                int cy = y + 2 + (i / cols) * cell;
                boolean live = live(cy, 18, clipTop, clipBottom);
                boolean hover = live && ui.over(cx, cy, 18, 18);
                if (c.tone() == El.GOOD) {
                    g.fill(cx, cy, cx + 18, cy + 18, 0x60806040);
                } else if (hover) {
                    g.fill(cx, cy, cx + 18, cy + 18, 0x30806040);
                }
                icon(c.item(), cx + 1, cy + 1);
                if (c.tone() == El.MUTED) {
                    g.fill(cx, cy, cx + 18, cy + 18, 0x90F4E6C8);
                }
                if (hover) {
                    String name = resolve(c.text());
                    ui.tooltip(Component.literal(c.why().isEmpty() ? name : name + "\n" + resolve(c.why())));
                    if (!c.action().isEmpty()) {
                        ui.hit(cx, cy, 18, 18, () -> click(c.action(), c.arg(), 0));
                    }
                }
            }
        }
        return rows * cell + 4;
    }

    /** An item icon, or {@code tex:<texture>} drawn 16x16 (RingRealms' building icons; unassigned.png if missing). */
    private void icon(String icon, int x, int y) {
        if (!icon.startsWith("tex:")) {
            ui.g.renderItem(new ItemStack(item(icon)), x, y);
            return;
        }
        ResourceLocation tex = ResourceLocation.tryParse(icon.substring(4));
        if (tex == null || !TEXTURES.computeIfAbsent(tex, t -> Minecraft.getInstance().getResourceManager()
                .getResource(t).isPresent())) {
            tex = com.porcupinestudios.lotrport.LotrPort.id("textures/buildings/unassigned.png");
        }
        RenderSystem.enableBlend();
        ui.g.blit(tex, x, y, 0, 0, 16, 16, 16, 16);
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
            int bx = bookX(mx);
            int by = bookY(my);
            int sx = rx + rw + 2;
            if (contentHeight > rh && bx >= sx && bx < sx + 5 && by >= ry && by < ry + rh) {
                dragging = true;
                dragTo(by);
                return true;
            }
            if (ui.click(bx, by)) {
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
            dragTo(bookY(my));
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
        int handle = Math.max(16, rh * rh / Math.max(1, contentHeight));
        double f = (my - ry - handle / 2.0) / Math.max(1, rh - handle);
        scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * Math.max(0, contentHeight - rh));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (bookX(mx) < (LX + PAGE_W + RX) / 2) {
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
                    if (!fieldEl.action().equals("rename")) {
                        field.setValue("");
                    }
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
    }

    Optional<Book> book() {
        return Optional.ofNullable(book);
    }
}
