package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Draws headed sections of {@link Stat} lines (the stats page and the profile page both use it), each in its own
 * outlined box with its title in a caption band: label on the left, the value on the right (on lines of its own under
 * the label when both don't fit), with a bar under it, arrows round it or a drop-down for it.
 */
final class Sections {
    static final int LINE = 13;
    /** A line with a drop-down is a little taller, to fit the button. */
    static final int PICKER_LINE = 16;

    private Sections() {}

    /** Room inside a section's outline, and the caption band its title sits in. */
    static final int PAD = 4;
    static final int CAPTION = 14;
    private static final int OUTLINE = 0x90806040;

    /** Draws one section from {@code y} in its own outlined box; returns the y under it. */
    static int draw(Imm ui, UiScreen screen, Page.Section section, int x, int y, int w) {
        int h = lay(ui, screen, section, x, y, w, false);
        // the box first, then what is in it on top
        var g = ui.g;
        g.fill(x, y, x + w, y + h, 0x18806040);
        g.fill(x, y, x + w, y + CAPTION, 0x30806040);
        g.renderOutline(x, y, w, h, OUTLINE);
        g.fill(x + 1, y + CAPTION, x + w - 1, y + CAPTION + 1, 0x60806040);
        return y + lay(ui, screen, section, x, y, w, true);
    }

    /** How tall {@link #draw} makes a section at width {@code w}. */
    static int height(Imm ui, Page.Section section, int w) {
        return lay(ui, null, section, 0, 0, w, false);
    }

    /**
     * Lays out (and with {@code draw}, draws) one section, so measuring and drawing never disagree. A line whose label
     * and value don't fit side by side puts the value on lines of its own under the label: nothing is cut off and
     * nothing is drawn over anything else.
     */
    private static int lay(Imm ui, UiScreen screen, Page.Section section, int x, int y0, int w, boolean draw) {
        int ix = x + PAD;
        int iw = w - 2 * PAD;
        int y = y0 + CAPTION + 3;
        for (Stat s : section.stats()) {
            y += stat(ui, screen, s, ix, y, iw, draw);
        }
        int h = y - y0 + PAD - 2;
        if (draw) {
            net.minecraft.network.chat.Component title = section.title().copy().withStyle(ChatFormatting.BOLD);
            String t = section.title().getString();
            if (ui.font.width(title) > iw) {
                while (t.length() > 1 && ui.font.width(net.minecraft.network.chat.Component.literal(t + "...")
                        .withStyle(ChatFormatting.BOLD)) > iw) {
                    t = t.substring(0, t.length() - 1);
                }
                title = net.minecraft.network.chat.Component.literal(t + "...").withStyle(ChatFormatting.BOLD);
                if (ui.over(x, y0, w, CAPTION)) {
                    ui.tooltip(section.title());
                }
            }
            ui.text(title, ix, y0 + 3, UiTheme.DARK_TEXT);
        }
        return h;
    }

    /** Lines of {@code text} wrapped to {@code w}. */
    private static List<FormattedCharSequence> wrap(Imm ui, Component text, int w) {
        List<FormattedCharSequence> l = ui.font.split(text, Math.max(10, w));
        return l.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : l;
    }

    private static int stat(Imm ui, UiScreen screen, Stat s, int x, int y, int w, boolean draw) {
        int valueColor = UiTheme.tone(s.tone(), false);
        int labelColor = UiTheme.ON_LIGHT[1];
        String label = s.label().getString();
        int lw = ui.font.width(label);
        int top = y;
        if (s.picker().isPresent()) {
            Stat.Picker pk = s.picker().get();
            int bw = Math.min(w, Math.max(70, ui.font.width(s.value()) + 26));
            if (lw + 6 + bw > w) {
                // the label on its own lines, the drop-down under it
                for (FormattedCharSequence l : wrap(ui, s.label(), w - 2)) {
                    if (draw) {
                        ui.g.drawString(ui.font, l, x + 2, y + 2, labelColor, false);
                    }
                    y += 10;
                }
            } else if (draw) {
                ui.text(label, x + 2, y + 4, labelColor);
            }
            int bx = x + w - bw;
            int by = y;
            if (draw) {
                ui.dropdown(bx, by, bw, PICKER_LINE - 2, ui.fit(s.value(), bw - 20),
                        () -> screen.popup(new Popups.ChoiceMenu(screen, pk, bx, by + PICKER_LINE - 2, bw)));
            }
            return y + PICKER_LINE - top;
        }
        if (s.stepper().isPresent()) {
            Stat.Stepper st = s.stepper().get();
            int a = 9;
            int vw = ui.font.width(s.value()) + 6;
            boolean oneLine = lw + 8 + a + vw + a + 4 <= w;
            if (!oneLine) {
                for (FormattedCharSequence l : wrap(ui, s.label(), w - 2)) {
                    if (draw) {
                        ui.g.drawString(ui.font, l, x + 2, y + 2, labelColor, false);
                    }
                    y += 10;
                }
            } else if (draw) {
                ui.text(label, x + 2, y + 2, labelColor);
            }
            // the value between the arrows, wrapped if it is long (research costs, edict hints)
            int room = w - 2 * a - 8;
            vw = Math.min(room, vw);
            List<FormattedCharSequence> lines = wrap(ui, Component.literal(s.value()), vw);
            if (draw) {
                int right = x + w;
                boolean up = st.value() < st.max();
                boolean down = st.value() > st.min();
                drawArrow(ui, right - a, y + 1, a, UiTheme.ARROW_RIGHT, up,
                        () -> screen.send(st.action(), "", Math.min(st.max(), st.value() + st.step() * fast())));
                int vx = right - a - 2 - vw;
                int ly = y + 2;
                for (FormattedCharSequence l : lines) {
                    ui.g.drawString(ui.font, l, vx + (vw - ui.font.width(l)) / 2, ly, valueColor, false);
                    ly += 10;
                }
                drawArrow(ui, vx - 2 - a, y + 1, a, UiTheme.ARROW_LEFT, down,
                        () -> screen.send(st.action(), "", Math.max(st.min(), st.value() - st.step() * fast())));
            }
            y += Math.max(LINE, lines.size() * 10 + 3);
        } else {
            String value = s.value();
            int vw = ui.font.width(value);
            if (value.isEmpty() || lw + 8 + vw <= w) {
                if (draw) {
                    ui.text(label, x + 2, y + 2, labelColor);
                    ui.text(value, x + w - vw, y + 2, valueColor);
                }
                y += LINE;
            } else {
                // too long for one line: the label, then the value right-aligned on lines of its own
                for (FormattedCharSequence l : wrap(ui, s.label(), w - 2)) {
                    if (draw) {
                        ui.g.drawString(ui.font, l, x + 2, y + 2, labelColor, false);
                    }
                    y += 10;
                }
                for (FormattedCharSequence l : wrap(ui, Component.literal(value), w - 12)) {
                    if (draw) {
                        ui.g.drawString(ui.font, l, x + w - ui.font.width(l), y + 2, valueColor, false);
                    }
                    y += 10;
                }
                y += 3;
            }
        }
        if (s.bar() >= 0) {
            if (draw) {
                ui.bar(x + 2, y - 2, w - 2, 3, s.bar());
            }
            y += 4;
        }
        return y - top;
    }

    /** Steppers move 10 steps with Shift and 64 with Ctrl, so big numbers (goods) are quick to reach. */
    static int fast() {
        return net.minecraft.client.gui.screens.Screen.hasControlDown() ? 64
                : net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 10 : 1;
    }

    private static void drawArrow(Imm ui, int x, int y, int size, ResourceLocation sprite, boolean enabled,
            Runnable click) {
        if (!enabled) {
            return;
        }
        boolean hover = ui.over(x, y, size, size);
        ui.sprite(sprite, x - (hover ? 1 : 0), y - (hover ? 1 : 0), size + (hover ? 2 : 0), size + (hover ? 2 : 0));
        ui.hit(x, y, size, size, click);
    }

    static Optional<Stat.Choice> current(Stat.Picker pk) {
        return pk.choices().stream().filter(c -> c.id().equals(pk.current())).findFirst();
    }
}
