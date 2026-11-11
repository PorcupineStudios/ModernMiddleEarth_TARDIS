package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import java.util.List;

/**
 * Draws a {@link Page.Stats}: headed sections of label/value lines in one column, or two when there is room, with
 * bars and arrows for values the player may change. Buttons sit at the bottom right.
 */
final class StatsPane {
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
            colY[c] = Sections.draw(ui, screen, section, cx, colY[c], colW) + 8;
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
}
