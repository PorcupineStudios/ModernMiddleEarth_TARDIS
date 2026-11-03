package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.net.QuestNet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Red Book of Westmarch, on LOTR's book pages: the right page lists the open quests (scroll for more), the left
 * page shows the chosen one (giver, task, progress, reward) with a button to abandon it (click twice). The book is
 * drawn at its own size and shrunk on small screens; clicks are mapped back into book pixels.
 */
public final class RedBookScreen extends Screen {
    private static final ResourceLocation BOOK = LotrPort.id("textures/gui/quest/red_book.png");
    private static final ResourceLocation ENTRY = LotrPort.id("textures/gui/quest/red_book_miniquests.png");
    private static final int W = 420;
    private static final int H = 256;
    private static final int INK = 0x3F2A14;
    private static final int FADED = 0x7A6040;
    /** The right page's list: where it starts, one entry's size, and how many fit. */
    private static final int LIST_X = 224;
    private static final int LIST_Y = 18;
    private static final int ENTRY_W = 170;
    private static final int ENTRY_H = 45;
    private static final int SHOWN = 4;
    /** The left page's abandon button. */
    private static final int ABANDON_X = 20;
    private static final int ABANDON_Y = 218;
    private static final int ABANDON_W = 80;
    private static final int ABANDON_H = 16;

    private QuestNet.Book book;
    private int selected;
    private int scroll;
    private boolean confirmAbandon;
    private float scale = 1;
    private int left;
    private int top;

    private RedBookScreen(QuestNet.Book book) {
        super(Component.translatableWithFallback("screen.lotr_port.red_book", "Red Book of Westmarch"));
        this.book = book;
    }

    /** Opens the book, or refreshes it if it is open and {@code book.open()} is false. */
    public static void show(QuestNet.Book book) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RedBookScreen open) {
            open.book = book;
            open.selected = Math.min(open.selected, Math.max(0, book.quests().size() - 1));
            open.scroll = Math.max(0, Math.min(open.scroll, book.quests().size() - SHOWN));
            open.confirmAbandon = false;
        } else if (book.open()) {
            mc.setScreen(new RedBookScreen(book));
        }
    }

    @Override
    protected void init() {
        scale = Math.min(1f, Math.min((width - 8) / (float) W, (height - 8) / (float) H));
        left = (int) ((width - W * scale) / 2);
        top = (int) ((height - H * scale) / 2);
    }

    private double bookX(double mouseX) {
        return (mouseX - left) / scale;
    }

    private double bookY(double mouseY) {
        return (mouseY - top) / scale;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int mx = (int) bookX(mouseX);
        int my = (int) bookY(mouseY);
        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        g.pose().scale(scale, scale, 1);
        g.blit(BOOK, 0, 0, 0, 0, W, H, 512, 512);
        leftPage(g, mx, my);
        rightPage(g, mx, my);
        g.pose().popPose();
    }

    private void leftPage(GuiGraphics g, int mx, int my) {
        centered(g, title, 105, 14, INK);
        g.fill(30, 25, 180, 26, 0x806A5030);
        List<QuestNet.Entry> quests = book.quests();
        centered(g, Component.translatableWithFallback("screen.lotr_port.red_book.stats",
                "%s open, %s finished", quests.size(), book.completed()), 105, 30, FADED);
        if (quests.isEmpty()) {
            int y = 60;
            for (FormattedCharSequence line : font.split(Component.translatableWithFallback(
                    "screen.lotr_port.red_book.empty", "You have no quests. People who need help will ask you"
                            + " when you talk to them; what you take on is written here."), 170)) {
                g.drawString(font, line, 20, y, FADED, false);
                y += font.lineHeight + 2;
            }
            return;
        }
        QuestNet.Entry q = quests.get(Math.min(selected, quests.size() - 1));
        int y = 50;
        g.drawString(font, Component.literal(q.giver()).withStyle(s -> s.withBold(true)), 20, y, INK, false);
        y += font.lineHeight + 1;
        g.drawString(font, q.faction(), 20, y, FADED, false);
        y += font.lineHeight + 8;
        for (FormattedCharSequence line : font.split(capitalised(q.task()), 170)) {
            g.drawString(font, line, 20, y, INK, false);
            y += font.lineHeight + 1;
        }
        y += 6;
        // progress bar
        boolean done = q.progress() >= q.count();
        int filled = q.count() <= 0 ? 0 : 168 * Math.min(q.progress(), q.count()) / q.count();
        g.fill(20, y, 190, y + 8, 0xFF5A4028);
        g.fill(21, y + 1, 189, y + 7, 0xFFD8C8A0);
        g.fill(21, y + 1, 21 + filled, y + 7, done ? 0xFF4C8A2C : 0xFFB08A30);
        y += 11;
        g.drawString(font, done
                ? Component.translatableWithFallback("screen.lotr_port.red_book.ready",
                        "Done: return to %s", q.giver())
                : Component.translatableWithFallback("screen.lotr_port.red_book.progress", "%s of %s",
                        q.progress(), q.count()), 20, y, done ? 0x2F6A1F : INK, false);
        y += font.lineHeight + 8;
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.quest.reward", "Reward:"), 20, y, INK,
                false);
        y += font.lineHeight + 2;
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.quest.coins", "%s coins",
                q.coins()), 26, y, 0x7A5A10, false);
        y += font.lineHeight + 1;
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.quest.alignment", "+%s alignment",
                q.alignment()), 26, y, 0x2F6A1F, false);
        boolean over = inside(mx, my, ABANDON_X, ABANDON_Y, ABANDON_W, ABANDON_H);
        g.fill(ABANDON_X, ABANDON_Y, ABANDON_X + ABANDON_W, ABANDON_Y + ABANDON_H, over ? 0xFF8A2A1A : 0xFF6A2014);
        g.fill(ABANDON_X + 1, ABANDON_Y + 1, ABANDON_X + ABANDON_W - 1, ABANDON_Y + ABANDON_H - 1,
                over ? 0xFFE8D0A8 : 0xFFDCC498);
        centered(g, confirmAbandon
                ? Component.translatableWithFallback("screen.lotr_port.red_book.confirm", "Really abandon?")
                : Component.translatableWithFallback("screen.lotr_port.red_book.abandon", "Abandon"),
                ABANDON_X + ABANDON_W / 2, ABANDON_Y + 4, 0x8A2A1A);
    }

    private void rightPage(GuiGraphics g, int mx, int my) {
        List<QuestNet.Entry> quests = book.quests();
        for (int i = 0; i < SHOWN && scroll + i < quests.size(); i++) {
            int index = scroll + i;
            QuestNet.Entry q = quests.get(index);
            int x = LIST_X;
            int y = LIST_Y + i * (ENTRY_H + 2);
            boolean over = inside(mx, my, x, y, ENTRY_W, ENTRY_H);
            g.blit(ENTRY, x, y, 0, index == selected || over ? ENTRY_H : 0, ENTRY_W, ENTRY_H, 256, 256);
            if (index == selected) {
                g.blit(ENTRY, x, y, 0, ENTRY_H * 2, ENTRY_W, ENTRY_H, 256, 256);
            }
            g.drawString(font, q.giver(), x + 6, y + 8, INK, false);
            FormattedCharSequence task = font.split(capitalised(q.task()), 140).get(0);
            g.drawString(font, task, x + 6, y + 29, FADED, false);
            // the little box fills as the quest is done
            int fill = q.count() <= 0 ? 0 : 15 * Math.min(q.progress(), q.count()) / q.count();
            g.fill(x + 149, y + 39 - fill, x + 164, y + 39,
                    q.progress() >= q.count() ? 0xFF4C8A2C : 0xFFB08A30);
        }
        if (quests.size() > SHOWN) {
            centered(g, Component.translatableWithFallback("screen.lotr_port.red_book.scroll",
                    "%s-%s of %s (scroll)", scroll + 1, Math.min(quests.size(), scroll + SHOWN), quests.size()),
                    LIST_X + ENTRY_W / 2, LIST_Y + SHOWN * (ENTRY_H + 2) + 4, FADED);
        }
    }

    private static Component capitalised(Component text) {
        String s = text.getString();
        return Component.literal(s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1));
    }

    private void centered(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }

    private static boolean inside(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double x = bookX(mouseX);
        double y = bookY(mouseY);
        List<QuestNet.Entry> quests = book.quests();
        for (int i = 0; i < SHOWN && scroll + i < quests.size(); i++) {
            if (inside(x, y, LIST_X, LIST_Y + i * (ENTRY_H + 2), ENTRY_W, ENTRY_H)) {
                selected = scroll + i;
                confirmAbandon = false;
                return true;
            }
        }
        if (!quests.isEmpty() && inside(x, y, ABANDON_X, ABANDON_Y, ABANDON_W, ABANDON_H)) {
            if (confirmAbandon) {
                PacketDistributor.sendToServer(new QuestNet.Abandon(selected));
                confirmAbandon = false;
            } else {
                confirmAbandon = true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, book.quests().size() - SHOWN);
        scroll = Math.max(0, Math.min(max, scroll + (scrollY > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
