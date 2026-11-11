package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import java.util.List;

/**
 * Draws a {@link Page.Stats}: headed sections of label/value lines in one column, or two when there is room, with
 * bars and arrows for values the player may change. Buttons sit at the bottom right.
 */
final class StatsPane {
    private static final int LINE = 13;
    private static final int TWO_COLUMNS_FROM = 300;

    private final UiScreen screen;
    private Page.Stats stats;

    StatsPane(UiScreen screen, Page.Stats stats) {
        this.screen = screen;
        this.stats = stats;
    }

    void set(Page.Stats s) {
        stats = s;
    }

    void draw(Imm ui, int x, int y, int w, int h) {
        int buttonsH = stats.buttons().isEmpty() ? 0 : UiTheme.BUTTON_HEIGHT + 6;
        ui.sprite(UiTheme.INSET_LIGHT, x, y, w, h - buttonsH);
        int columns = w >= TWO_COLUMNS_FROM && stats.sections().size() > 1 ? 2 : 1;
        int colW = (w - 16 - (columns - 1) * 14) / columns;
        int[] colY = new int[columns];
        for (int i = 0; i < columns; i++) {
            colY[i] = y + 7;
        }
        ui.clip(x, y, w, h - buttonsH);
        for (Page.Section section : stats.sections()) {
            // the shorter column takes the next section
            int c = columns == 2 && colY[1] < colY[0] ? 1 : 0;
            int cx = x + 8 + c * (colW + 14);
            colY[c] = drawSection(ui, section, cx, colY[c], colW) + 8;
        }
        ui.unclip();

        int bx = x + w;
        List<Action> buttons = stats.buttons();
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Action a = buttons.get(i);
            int bw = ui.font.width(a.label()) + 16;
            bx -= bw;
            ui.button(bx, y + h - UiTheme.BUTTON_HEIGHT, bw, UiTheme.BUTTON_HEIGHT, a.label(), a.enabled(), a.why(),
                    () -> screen.act(a, "", 0));
            bx -= 4;
        }
    }

    private int drawSection(Imm ui, Page.Section section, int x, int y, int w) {
        ui.text(section.title().copy().withStyle(net.minecraft.ChatFormatting.BOLD), x, y, UiTheme.DARK_TEXT);
        y += LINE + 1;
        for (Stat s : section.stats()) {
            ui.text(ui.fit(s.label().getString(), w / 2), x + 2, y + 2, UiTheme.ON_LIGHT[1]);
            int valueColor = UiTheme.tone(s.tone(), false);
            if (s.stepper().isPresent()) {
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
                ui.text(s.value(), x + w / 2, y, w / 2, 11, 1, valueColor);
            }
            y += LINE;
            if (s.bar() >= 0) {
                ui.bar(x + 2, y - 2, w - 2, 3, s.bar());
                y += 4;
            }
        }
        return y;
    }

    private static void drawArrow(Imm ui, int x, int y, int size, net.minecraft.resources.ResourceLocation sprite,
            boolean enabled, Runnable click) {
        if (!enabled) {
            return;
        }
        boolean hover = ui.over(x, y, size, size);
        ui.sprite(sprite, x - (hover ? 1 : 0), y - (hover ? 1 : 0), size + (hover ? 2 : 0), size + (hover ? 2 : 0));
        ui.hit(x, y, size, size, click);
    }
}
