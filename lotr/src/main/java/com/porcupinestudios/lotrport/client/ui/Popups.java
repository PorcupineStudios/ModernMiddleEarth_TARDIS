package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Column;
import com.porcupinestudios.lotrport.ui.FilterGroup;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * What can open over the page: the sort menu, the filter box and the "are you sure?" box. A popup is drawn last, on
 * layer 1; a click outside it closes it.
 */
final class Popups {
    private Popups() {}

    abstract static class Popup {
        int x;
        int y;
        int w;
        int h;

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        abstract void draw(Imm ui, UiScreen screen);

        /** Keeps the box on screen. */
        void fit(int screenW, int screenH) {
            x = Math.max(2, Math.min(x, screenW - w - 2));
            y = Math.max(2, Math.min(y, screenH - h - 2));
        }
    }

    /** One line per column; picking the sorted one turns the order round. */
    static final class SortMenu extends Popup {
        private static final int LINE = 12;
        private final TablePane pane;

        SortMenu(TablePane pane, int x, int y, int w) {
            this.pane = pane;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = pane.table().columns().size() * LINE + 8;
        }

        @Override
        void draw(Imm ui, UiScreen screen) {
            ui.sprite(UiTheme.POPUP, x, y, w, h);
            List<Column> cols = pane.table().columns();
            int sorted = pane.sortIndex();
            for (int i = 0; i < cols.size(); i++) {
                int ly = y + 4 + i * LINE;
                boolean hover = ui.over(x + 3, ly, w - 6, LINE);
                if (hover) {
                    ui.g.fill(x + 3, ly, x + w - 3, ly + LINE, 0x30000000);
                }
                String mark = i == sorted ? (pane.sortDescending() ? " ▼" : " ▲") : "";
                ui.text(cols.get(i).label().getString() + mark, x + 7, ly, w - 14, LINE, -1, UiTheme.DARK_TEXT);
                int column = i;
                ui.hit(x + 3, ly, w - 6, LINE, () -> {
                    pane.sortBy(column);
                    screen.popup(null);
                });
            }
        }
    }

    /** "Show only": a tick box per filter under its group's heading, and Clear all. */
    static final class FilterMenu extends Popup {
        private static final int LINE = 14;
        private final TablePane pane;
        private final TableState state;

        FilterMenu(TablePane pane, TableState state, int right, int y) {
            this.pane = pane;
            this.state = state;
            this.w = 150;
            this.x = right - w;
            this.y = y;
            int lines = 0;
            for (FilterGroup g : pane.table().filters()) {
                lines += 1 + g.filters().size();
            }
            this.h = 8 + LINE + lines * LINE + pane.table().filters().size() * 3 + UiTheme.BUTTON_HEIGHT + 6;
        }

        @Override
        void draw(Imm ui, UiScreen screen) {
            ui.sprite(UiTheme.POPUP, x, y, w, h);
            int ly = y + 6;
            ui.text(Component.translatableWithFallback("ui.lotr_port.show_only", "Show only")
                    .withStyle(ChatFormatting.BOLD), x + 8, ly, UiTheme.DARK_TEXT);
            ly += LINE;
            for (FilterGroup g : pane.table().filters()) {
                ui.text(g.label().copy().withStyle(ChatFormatting.BOLD), x + 8, ly + 1, UiTheme.ON_LIGHT[1]);
                ly += LINE - 2;
                for (FilterGroup.Filter f : g.filters()) {
                    ui.checkbox(x + 10, ly + 1, w - 18, state.isTicked(g.id(), f.tag()), f.label().getString(), () -> {
                        state.toggle(g.id(), f.tag());
                        pane.refilter();
                    });
                    ly += LINE;
                }
                ly += 5;
            }
            int bw = 64;
            ui.button(x + w - bw - 8, y + h - UiTheme.BUTTON_HEIGHT - 6, bw, UiTheme.BUTTON_HEIGHT,
                    Component.translatableWithFallback("ui.lotr_port.clear_all", "Clear all"), () -> {
                        state.clearTicks();
                        pane.refilter();
                    });
        }
    }

    /** "Are you sure?" before an action marked {@code confirm}. */
    static final class Confirm extends Popup {
        private final Action action;
        private final String row;
        private final int value;

        Confirm(Action action, String row, int value, int screenW, int screenH) {
            this.action = action;
            this.row = row;
            this.value = value;
            this.w = 170;
            this.h = 62;
            this.x = (screenW - w) / 2;
            this.y = (screenH - h) / 2;
        }

        @Override
        void draw(Imm ui, UiScreen screen) {
            ui.sprite(UiTheme.POPUP, x, y, w, h);
            ui.text(action.label().getString(), x + 8, y + 8, w - 16, 10, 0, UiTheme.DARK_TEXT);
            ui.text(Component.translatableWithFallback("ui.lotr_port.sure", "Are you sure?").getString(), x + 8,
                    y + 20, w - 16, 10, 0, UiTheme.ON_LIGHT[1]);
            int bw = 60;
            ui.button(x + w / 2 - bw - 4, y + h - UiTheme.BUTTON_HEIGHT - 8, bw, UiTheme.BUTTON_HEIGHT,
                    Component.translatableWithFallback("gui.yes", "Yes"), () -> {
                        screen.popup(null);
                        screen.send(action.id(), row, value);
                    });
            ui.button(x + w / 2 + 4, y + h - UiTheme.BUTTON_HEIGHT - 8, bw, UiTheme.BUTTON_HEIGHT,
                    Component.translatableWithFallback("gui.no", "No"), () -> screen.popup(null));
        }
    }
}
