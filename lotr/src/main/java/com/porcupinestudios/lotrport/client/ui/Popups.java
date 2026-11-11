package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Column;
import com.porcupinestudios.lotrport.ui.FilterGroup;
import com.porcupinestudios.lotrport.ui.Stat;
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

    /** The choices of a drop-down on a stats line; the one in force is ticked, a disabled one says why. */
    static final class ChoiceMenu extends Popup {
        private static final int LINE = 12;
        private static final int MAX_LINES = 14;
        private final Stat.Picker picker;
        private int scroll;

        ChoiceMenu(Stat.Picker picker, int x, int y, int w) {
            this.picker = picker;
            this.x = x;
            this.y = y;
            this.w = Math.max(w, 90);
            this.h = Math.min(picker.choices().size(), MAX_LINES) * LINE + 8;
        }

        @Override
        void draw(Imm ui, UiScreen screen) {
            ui.sprite(UiTheme.POPUP, x, y, w, h);
            List<Stat.Choice> choices = picker.choices();
            int shown = Math.min(choices.size(), MAX_LINES);
            scroll = Math.max(0, Math.min(scroll, choices.size() - shown));
            for (int i = 0; i < shown; i++) {
                Stat.Choice c = choices.get(i + scroll);
                int ly = y + 4 + i * LINE;
                boolean hover = ui.over(x + 3, ly, w - 6, LINE);
                if (hover && c.enabled()) {
                    ui.g.fill(x + 3, ly, x + w - 3, ly + LINE, 0x30000000);
                }
                if (hover && !c.enabled()) {
                    c.why().ifPresent(ui::tooltip);
                }
                String mark = c.id().equals(picker.current()) ? "✔ " : "   ";
                ui.text(mark + c.label().getString(), x + 5, ly, w - 10, LINE, -1,
                        c.enabled() ? UiTheme.DARK_TEXT : UiTheme.DISABLED_TEXT);
                if (c.enabled()) {
                    ui.hit(x + 3, ly, w - 6, LINE, () -> {
                        screen.popup(null);
                        if (!c.id().equals(picker.current())) {
                            screen.send(picker.action(), c.id(), 0);
                        }
                    });
                }
            }
        }

        /** Scrolls a long list; true if it moved. */
        boolean scroll(double amount) {
            int before = scroll;
            scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount),
                    picker.choices().size() - Math.min(picker.choices().size(), MAX_LINES)));
            return scroll != before;
        }
    }

    /**
     * "Show only": a tick box per filter under its group's heading, and Clear all. Groups (even one long group) flow
     * into more columns when one column would be taller than the screen.
     */
    static final class FilterMenu extends Popup {
        private static final int LINE = 14;
        private static final int COLUMN = 110;
        private static final int GROUP_GAP = 5;
        private final TablePane pane;
        private final TableState state;
        /** Where each header and checkbox goes, worked out once: long groups carry on in the next column. */
        private final java.util.List<Slot> slots = new java.util.ArrayList<>();

        private record Slot(FilterGroup group, FilterGroup.Filter filter, int column, int dy) {}

        FilterMenu(TablePane pane, TableState state, int right, int y, int screenHeight) {
            this.pane = pane;
            this.state = state;
            this.y = y;
            int top = 6 + LINE;
            int bottom = UiTheme.BUTTON_HEIGHT + 12;
            int limit = Math.max(LINE * 4, screenHeight - 8 - top - bottom);
            int column = 0;
            int used = 0;
            int tallest = 0;
            for (FilterGroup g : pane.table().filters()) {
                // a header never sits alone at the foot of a column
                if (used > 0 && used + (LINE - 2) + LINE > limit) {
                    column++;
                    used = 0;
                }
                slots.add(new Slot(g, null, column, used));
                used += LINE - 2;
                for (FilterGroup.Filter f : g.filters()) {
                    if (used + LINE > limit) {
                        column++;
                        used = 0;
                    }
                    slots.add(new Slot(g, f, column, used));
                    used += LINE;
                    tallest = Math.max(tallest, used);
                }
                used += GROUP_GAP;
            }
            this.w = 10 + (column + 1) * COLUMN;
            this.x = Math.max(4, right - w);
            this.h = top + tallest + bottom;
        }

        @Override
        void draw(Imm ui, UiScreen screen) {
            ui.sprite(UiTheme.POPUP, x, y, w, h);
            ui.text(Component.translatableWithFallback("ui.lotr_port.show_only", "Show only")
                    .withStyle(ChatFormatting.BOLD), x + 8, y + 6, UiTheme.DARK_TEXT);
            int top = y + 6 + LINE;
            int bottomEdge = y + h - UiTheme.BUTTON_HEIGHT - 10;
            ui.clip(x, top, w, bottomEdge - top);
            for (Slot s : slots) {
                int cx = x + 8 + s.column() * COLUMN;
                int ly = top + s.dy();
                FilterGroup g = s.group();
                FilterGroup.Filter f = s.filter();
                if (f == null) {
                    ui.text(g.label().copy().withStyle(ChatFormatting.BOLD), cx, ly + 1, UiTheme.ON_LIGHT[1]);
                } else {
                    ui.checkbox(cx + 2, ly + 1, COLUMN - 12, state.isTicked(g.id(), f.tag()), f.label().getString(),
                            () -> {
                                state.toggle(g.id(), f.tag());
                                pane.refilter();
                            });
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
