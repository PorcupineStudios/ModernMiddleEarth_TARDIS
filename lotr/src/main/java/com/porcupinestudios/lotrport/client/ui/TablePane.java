package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Cell;
import com.porcupinestudios.lotrport.ui.Column;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Row;
import com.porcupinestudios.lotrport.ui.TableQuery;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;

/**
 * Draws a {@link Page.Table}: the tool bar (search box, sort and filter drop-downs), the column headings, the rows
 * (only those in view, so thousands cost nothing) with a scroll bar, the "3 of 140 shown" line and the buttons.
 * The search box itself is a real text field owned by the screen; this draws the box behind it.
 */
final class TablePane {
    static final int SEARCH_WIDTH = 120;
    private static final int SORT_WIDTH = 116;
    private static final int FILTER_WIDTH = 86;
    private static final int SCROLL_WIDTH = 8;
    private static final long DOUBLE_CLICK_MS = 400;

    private final UiScreen screen;
    private final TableState state;
    private Page.Table table;
    private List<Row> shown = List.of();
    private String lastClicked;
    private long lastClickAt;
    // the list area of the last frame, for the wheel and the scroll bar drag
    int listX;
    int listY;
    int listW;
    int listH;
    private int contentHeight;
    private int trackY;
    private int trackH;

    TablePane(UiScreen screen, TableState state, Page.Table table) {
        this.screen = screen;
        this.state = state;
        set(table);
    }

    void set(Page.Table t) {
        table = t;
        refilter();
    }

    Page.Table table() {
        return table;
    }

    /** Re-runs search, filters and sort (after the player changed one, or new rows came). */
    void refilter() {
        shown = TableQuery.apply(table.columns(), table.filters(), table.rows(), state.search, state.ticked,
                sortIndex(), sortDescending());
        if (state.selected != null && shown.stream().noneMatch(r -> r.id().equals(state.selected))) {
            state.selected = null;
        }
    }

    int sortIndex() {
        if (state.sortColumn != null) {
            for (int i = 0; i < table.columns().size(); i++) {
                if (table.columns().get(i).id().equals(state.sortColumn)) {
                    return i;
                }
            }
        }
        return table.sortColumn();
    }

    boolean sortDescending() {
        return state.sortColumn != null ? state.descending : table.descending();
    }

    /** Sorts by a column; picking the sorted one again turns the order round. */
    void sortBy(int column) {
        Column c = table.columns().get(column);
        if (column == sortIndex()) {
            state.sortColumn = c.id();
            state.descending = !sortDescending();
        } else {
            state.sortColumn = c.id();
            // numbers read best high to low, names A to Z
            state.descending = c.numeric();
        }
        refilter();
    }

    /** The search box's spot, for the screen to put its text field in. */
    static int[] searchBox(int x, int y, int w) {
        return new int[] {x, y, Math.min(SEARCH_WIDTH, w / 3), UiTheme.FIELD_HEIGHT};
    }

    void draw(Imm ui, int x, int y, int w, int h) {
        int bar = UiTheme.FIELD_HEIGHT;
        int[] sb = searchBox(x, y, w);
        ui.sprite(UiTheme.INSET_LIGHT, sb[0], sb[1], sb[2], sb[3]);

        int sortX = sb[0] + sb[2] + 10;
        int sorted = sortIndex();
        String sortLabel = Component.translatableWithFallback("ui.lotr_port.sort", "Sort: %s",
                sorted >= 0 ? table.columns().get(sorted).label() : Component.literal("-")).getString();
        ui.dropdown(sortX, y - 1, SORT_WIDTH, bar + 2, sortLabel,
                () -> screen.popup(new Popups.SortMenu(this, sortX, y + bar + 2, SORT_WIDTH)));

        if (!table.filters().isEmpty()) {
            int n = TableQuery.tickedCount(table.filters(), state.ticked);
            String label = n == 0 ? Component.translatableWithFallback("ui.lotr_port.filters", "Filters").getString()
                    : Component.translatableWithFallback("ui.lotr_port.filters_n", "Filters (%s)", n).getString();
            int fxr = x + w - FILTER_WIDTH;
            ui.dropdown(fxr, y - 1, FILTER_WIDTH, bar + 2, label,
                    () -> screen.popup(new Popups.FilterMenu(this, state, x + w, y + bar + 4, screen.height)));
        }

        int boxY = y + bar + 6;
        int boxH = h - bar - 6;
        ui.sprite(UiTheme.INSET_DARK, x, boxY, w, boxH);
        drawList(ui, x + 6, boxY + 5, w - 12, boxH - 10);
    }

    private void drawList(Imm ui, int x, int y, int w, int h) {
        List<Column> cols = table.columns();
        int footer = 16;
        int rowsY = y + UiTheme.HEADER_HEIGHT + 2;
        int rowsH = h - UiTheme.HEADER_HEIGHT - 2 - footer;
        int pitch = UiTheme.ROW_HEIGHT + UiTheme.ROW_GAP;
        contentHeight = shown.size() * pitch - UiTheme.ROW_GAP;
        boolean scrolls = contentHeight > rowsH;
        int rowW = scrolls ? w - SCROLL_WIDTH - 4 : w;
        state.scroll = Math.max(0, Math.min(state.scroll, Math.max(0, contentHeight - rowsH)));
        listX = x;
        listY = rowsY;
        listW = w;
        listH = rowsH;

        int[] colX = columnStarts(cols, x + 8, rowW - 16);
        // headings
        int sorted = sortIndex();
        for (int c = 0; c < cols.size(); c++) {
            Column col = cols.get(c);
            int cx = colX[c];
            int cw = colX[c + 1] - cx - 6;
            String label = col.label().getString() + (c == sorted ? (sortDescending() ? " ▼" : " ▲") : "");
            boolean hover = ui.over(cx, y, cw, UiTheme.HEADER_HEIGHT);
            ui.text(label, cx, y, cw, UiTheme.HEADER_HEIGHT, align(col), hover ? UiTheme.TITLE : UiTheme.HEADING);
            int column = c;
            ui.hit(cx, y, cw, UiTheme.HEADER_HEIGHT, () -> sortBy(column));
        }

        // rows: only those in view
        if (shown.isEmpty()) {
            Component msg = table.rows().isEmpty() ? table.empty()
                    : Component.translatableWithFallback("ui.lotr_port.no_matches", "Nothing matches");
            ui.text(msg.getString(), x, rowsY, w, Math.min(rowsH, 40), 0, UiTheme.ON_DARK[1]);
        } else {
            ui.clip(x, rowsY, w, rowsH);
            int first = (int) (state.scroll / pitch);
            for (int i = first; i < shown.size(); i++) {
                int ry = rowsY + i * pitch - (int) state.scroll;
                if (ry >= rowsY + rowsH) {
                    break;
                }
                drawRow(ui, shown.get(i), cols, colX, x, ry, rowW, rowsY, rowsH);
            }
            ui.unclip();
        }
        if (scrolls) {
            drawScrollBar(ui, x + w - SCROLL_WIDTH, rowsY, rowsH);
        }

        // footer: count, then buttons at the right
        int fy = y + h - footer + 3;
        int total = Math.max(table.total(), table.rows().size());
        String count = Component.translatableWithFallback("ui.lotr_port.shown", "%s of %s shown", shown.size(),
                total).getString();
        if (table.total() > table.rows().size()) {
            count += Component.translatableWithFallback("ui.lotr_port.cut", " (first %s sent)",
                    table.rows().size()).getString();
        }
        ui.text(count, x + 2, fy + 3, UiTheme.LIGHT_TEXT);
        int bx = x + w;
        Optional<Row> sel = selectedRow();
        List<Action> all = new java.util.ArrayList<>(table.buttons());
        all.addAll(0, table.rowActions());
        for (int i = all.size() - 1; i >= 0; i--) {
            Action a = all.get(i);
            boolean rowAction = i < table.rowActions().size();
            int bw = ui.font.width(a.label()) + 14;
            bx -= bw;
            boolean enabled = a.enabled() && (!rowAction || sel.isPresent());
            Optional<Component> why = rowAction && sel.isEmpty() && a.enabled()
                    ? Optional.of(Component.translatableWithFallback("ui.lotr_port.select_row", "Pick a row first"))
                    : a.why();
            ui.button(bx, fy - 1, bw, 15, a.label(), enabled, why,
                    () -> screen.act(a, rowAction ? sel.map(Row::id).orElse("") : "", 0));
            bx -= 4;
        }
    }

    private void drawRow(Imm ui, Row row, List<Column> cols, int[] colX, int x, int ry, int rowW, int clipY,
            int clipH) {
        boolean visible = ry + UiTheme.ROW_HEIGHT > clipY && ry < clipY + clipH;
        boolean hover = visible && ui.over(x, Math.max(ry, clipY), rowW,
                Math.min(ry + UiTheme.ROW_HEIGHT, clipY + clipH) - Math.max(ry, clipY));
        boolean selected = row.id().equals(state.selected);
        ui.sprite(hover || selected ? UiTheme.ROW_HOVER : UiTheme.ROW, x, ry, rowW, UiTheme.ROW_HEIGHT);
        for (int c = 0; c < cols.size(); c++) {
            Cell cell = row.cell(c);
            int cx = colX[c];
            int cw = colX[c + 1] - cx - 6;
            ui.text(cell.text(), cx, ry, cw, UiTheme.ROW_HEIGHT - 2, align(cols.get(c)),
                    UiTheme.tone(cell.tone(), true));
        }
        if (visible) {
            int top = Math.max(ry, clipY);
            ui.hit(x, top, rowW, Math.min(ry + UiTheme.ROW_HEIGHT, clipY + clipH) - top, () -> clickRow(row));
        }
    }

    private void clickRow(Row row) {
        long now = Util.getMillis();
        boolean twice = row.id().equals(lastClicked) && now - lastClickAt < DOUBLE_CLICK_MS;
        lastClicked = row.id();
        lastClickAt = now;
        state.selected = row.id();
        if (twice && table.open().isPresent()) {
            screen.send(table.open().get(), row.id(), 0);
        }
    }

    private void drawScrollBar(Imm ui, int x, int y, int h) {
        trackY = y;
        trackH = h;
        ui.sprite(UiTheme.SCROLL_TRACK, x, y, SCROLL_WIDTH, h);
        int handle = Math.max(16, h * h / Math.max(1, contentHeight));
        int max = Math.max(1, contentHeight - h);
        int hy = y + (int) ((h - handle) * (state.scroll / max));
        ui.sprite(UiTheme.SCROLL_HANDLE, x, hy, SCROLL_WIDTH, handle);
        ui.hit(x, y, SCROLL_WIDTH, h, () -> screen.dragScroll(this));
    }

    /** Scrolls so the handle's middle is at the mouse (while dragging the bar). */
    void dragTo(double mouseY) {
        int handle = Math.max(16, trackH * trackH / Math.max(1, contentHeight));
        double f = (mouseY - trackY - handle / 2.0) / Math.max(1, trackH - handle);
        state.scroll = Math.max(0, Math.min(1, f)) * Math.max(0, contentHeight - trackH);
    }

    boolean scroll(double mx, double my, double amount) {
        if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) {
            return false;
        }
        state.scroll -= amount * (UiTheme.ROW_HEIGHT + UiTheme.ROW_GAP) * 2;
        return true;
    }

    Optional<Row> selectedRow() {
        return shown.stream().filter(r -> r.id().equals(state.selected)).findFirst();
    }

    private static int align(Column c) {
        return switch (c.align()) {
            case LEFT -> -1;
            case CENTER -> 0;
            case RIGHT -> 1;
        };
    }

    /** Column left edges for a width, by weight; one more entry for the right end. */
    static int[] columnStarts(List<Column> cols, int x, int w) {
        int total = 0;
        for (Column c : cols) {
            total += Math.max(1, c.weight());
        }
        int[] out = new int[cols.size() + 1];
        int acc = 0;
        for (int i = 0; i < cols.size(); i++) {
            out[i] = x + (int) ((long) w * acc / Math.max(1, total));
            acc += Math.max(1, cols.get(i).weight());
        }
        out[cols.size()] = x + w;
        return out;
    }
}
