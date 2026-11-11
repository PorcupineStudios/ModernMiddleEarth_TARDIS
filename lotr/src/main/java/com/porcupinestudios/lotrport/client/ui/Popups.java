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

    /**
     * "Show only": a tick box per filter under its group's heading, and Clear all. Groups flow into more columns when
     * one column would be taller than the screen.
     */
    static final class FilterMenu extends Popup {
        private static final int LINE = 14;
        private static final int COLUMN = 140;
        private static final int GROUP_GAP = 5;
        private final TablePane pane;
        private final TableState state;
        /** The first group of each column. */
        private final java.util.List<Integer> columnStarts = new java.util.ArrayList<>();

        FilterMenu(TablePane pane, TableState state, int right, int y, int screenHeight) {
            this.pane = pane;
            this.state = state;
            this.y = y;
            int top = 6 + LINE;
            int bottom = UiTheme.BUTTON_HEIGHT + 12;
            int limit = Math.max(LINE * 4, screenHeight - 8 - top - bottom);
            int used = 0;
            int tallest = 0;
            List<FilterGroup> groups = pane.table().filters();
            for (int i = 0; i < groups.size(); i++) {
                int gh = groupHeight(groups.get(i));
                if (i == 0 || used + gh > limit) {
                    columnStarts.add(i);
                    used = 0;
                }
                used += gh;
                tallest = Math.max(tallest, used);
            }
            this.w = 10 + columnStarts.size() * COLUMN;
            this.x = right - w;
            this.h = top + Math.min(tallest, limit) + bottom;
        }

        private static int groupHeight(FilterGroup g) {
            return LINE - 2 + g.filters().size() * LINE + GROUP_GAP;
        }

        @Override
        void draw(Imm ui, UiScreen screen) {
            ui.sprite(UiTheme.POPUP, x, y, w, h);
            ui.text(Component.translatableWithFallback("ui.lotr_port.show_only", "Show only")
                    .withStyle(ChatFormatting.BOLD), x + 8, y + 6, UiTheme.DARK_TEXT);
            List<FilterGroup> groups = pane.table().filters();
            int top = y + 6 + LINE;
            int bottomEdge = y + h - UiTheme.BUTTON_HEIGHT - 10;
            ui.clip(x, top, w, bottomEdge - top);
            for (int c = 0; c < columnStarts.size(); c++) {
                int end = c + 1 < columnStarts.size() ? columnStarts.get(c + 1) : groups.size();
                int cx = x + 8 + c * COLUMN;
                int ly = top;
                for (int i = columnStarts.get(c); i < end; i++) {
                    FilterGroup g = groups.get(i);
                    ui.text(g.label().copy().withStyle(ChatFormatting.BOLD), cx, ly + 1, UiTheme.ON_LIGHT[1]);
                    ly += LINE - 2;
                    for (FilterGroup.Filter f : g.filters()) {
                        if (ly + UiTheme.CHECK_SIZE <= bottomEdge) {
                            ui.checkbox(cx + 2, ly + 1, COLUMN - 12, state.isTicked(g.id(), f.tag()),
                                    f.label().getString(), () -> {
                                        state.toggle(g.id(), f.tag());
                                        pane.refilter();
                                    });
                        }
                        ly += LINE;
                    }
                    ly += GROUP_GAP;
                }
            }
            ui.unclip();
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
