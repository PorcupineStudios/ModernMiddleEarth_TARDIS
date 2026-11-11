package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws headed sections of {@link Stat} lines (the stats page and the profile page both use it): label on the left,
 * the value on the right, with a bar under it, arrows round it or a drop-down for it.
 */
final class Sections {
    static final int LINE = 13;
    /** A line with a drop-down is a little taller, to fit the button. */
    static final int PICKER_LINE = 16;

    private Sections() {}

    /** Draws one section from {@code y}; returns the y under it. */
    static int draw(Imm ui, UiScreen screen, Page.Section section, int x, int y, int w) {
        ui.text(section.title().copy().withStyle(ChatFormatting.BOLD), x, y, UiTheme.DARK_TEXT);
        y += LINE + 1;
        for (Stat s : section.stats()) {
            int valueColor = UiTheme.tone(s.tone(), false);
            if (s.picker().isPresent()) {
                Stat.Picker pk = s.picker().get();
                int bw = Math.min(w / 2 + 10, Math.max(70, ui.font.width(s.value()) + 26));
                ui.text(ui.fit(s.label().getString(), w - bw - 4), x + 2, y + 4, UiTheme.ON_LIGHT[1]);
                int bx = x + w - bw;
                int by = y;
                ui.dropdown(bx, by, bw, PICKER_LINE - 2, s.value(),
                        () -> screen.popup(new Popups.ChoiceMenu(pk, bx, by + PICKER_LINE - 2, bw)));
                y += PICKER_LINE;
                continue;
            }
            if (s.stepper().isPresent()) {
                ui.text(ui.fit(s.label().getString(), w / 2), x + 2, y + 2, UiTheme.ON_LIGHT[1]);
                Stat.Stepper st = s.stepper().get();
                int a = 9;
                int right = x + w;
                boolean up = st.value() < st.max();
                boolean down = st.value() > st.min();
                drawArrow(ui, right - a, y + 1, a, UiTheme.ARROW_RIGHT, up,
                        () -> screen.send(st.action(), "", Math.min(st.max(), st.value() + st.step())));
                int vw = Math.max(24, ui.font.width(s.value()) + 6);
                ui.text(s.value(), right - a - 2 - vw, y, vw, 11, 0, valueColor);
                drawArrow(ui, right - a - 4 - vw - a, y + 1, a, UiTheme.ARROW_LEFT, down,
                        () -> screen.send(st.action(), "", Math.max(st.min(), st.value() - st.step())));
            } else {
                // a short value leaves the label more room (thoughts are long, their numbers short)
                int vw = Math.min(w / 2, ui.font.width(s.value()) + 4);
                String label = s.label().getString();
                String shown = ui.fit(label, w - vw - 6);
                ui.text(shown, x + 2, y + 2, UiTheme.ON_LIGHT[1]);
                if (!shown.equals(label) && ui.over(x, y, w - vw, LINE)) {
                    ui.tooltip(s.label());
                }
                ui.text(s.value(), x + w - vw, y, vw, 11, 1, valueColor);
            }
            y += LINE;
            if (s.bar() >= 0) {
                ui.bar(x + 2, y - 2, w - 2, 3, s.bar());
                y += 4;
            }
        }
        return y;
    }

    /** How tall {@link #draw} makes a section. */
    static int height(Page.Section section) {
        int h = LINE + 1;
        for (Stat s : section.stats()) {
            h += s.picker().isPresent() ? PICKER_LINE : LINE + (s.bar() >= 0 ? 4 : 0);
        }
        return h;
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
