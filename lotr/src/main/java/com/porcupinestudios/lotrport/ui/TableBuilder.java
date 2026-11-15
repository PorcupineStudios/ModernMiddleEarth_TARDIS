package com.porcupinestudios.lotrport.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.chat.Component;

/**
 * Makes a {@link Page.Table} from any list on the server: name the columns once with how to get each cell, and the
 * tags each item has.
 *
 * <pre>{@code
 * TableBuilder.of(people, p -> p.id().toString())
 *     .column(Column.text("name", NAME, 3), p -> Cell.text(p.name()))
 *     .column(Column.number("happy", HAPPY, 2), p -> Cell.number(p.happiness()))
 *     .tags(p -> p.homeless() ? List.of("homeless") : List.of())
 *     .filters(STATUS)
 *     .sortBy("happy", false)
 *     .open("citizen")
 *     .build();
 * }</pre>
 */
public final class TableBuilder<T> {
    private final Collection<T> items;
    private final Function<T, String> id;
    private final List<Column> columns = new ArrayList<>();
    private final List<Function<T, Cell>> cells = new ArrayList<>();
    private final List<Function<T, ? extends Collection<String>>> tags = new ArrayList<>();
    private final List<FilterGroup> filters = new ArrayList<>();
    private final List<Action> rowActions = new ArrayList<>();
    private final List<Action> buttons = new ArrayList<>();
    private int sortColumn = -1;
    private boolean descending;
    private Optional<String> open = Optional.empty();
    private Component empty = Component.translatableWithFallback("ui.lotr_port.empty", "Nothing here yet");
    private int limit = UiCodecs.MAX_ROWS;

    private TableBuilder(Collection<T> items, Function<T, String> id) {
        this.items = items;
        this.id = id;
    }

    public static <T> TableBuilder<T> of(Collection<T> items, Function<T, String> id) {
        return new TableBuilder<>(items, id);
    }

    public TableBuilder<T> column(Column column, Function<T, Cell> cell) {
        columns.add(column);
        cells.add(cell);
        return this;
    }

    /** Tags for the filters; may be called several times, the tags add up. */
    public TableBuilder<T> tags(Function<T, ? extends Collection<String>> tagger) {
        tags.add(tagger);
        return this;
    }

    public TableBuilder<T> filters(FilterGroup... groups) {
        filters.addAll(List.of(groups));
        return this;
    }

    /**
     * A filter group with one tick box per distinct value: tags {@code prefix + value}, labels from {@code label}.
     * Use it for things only known from the rows (races present, item kinds stocked). The items also get the tag.
     */
    public TableBuilder<T> filterBy(String groupId, Component groupLabel, String prefix, Function<T, String> value,
            Function<String, Component> label) {
        Map<String, Component> seen = new LinkedHashMap<>();
        items.stream().map(value).sorted().forEach(v -> seen.computeIfAbsent(v, label));
        List<FilterGroup.Filter> list = new ArrayList<>();
        seen.forEach((v, l) -> list.add(new FilterGroup.Filter(prefix + v, l)));
        filters.add(new FilterGroup(groupId, groupLabel, list));
        tags.add(t -> List.of(prefix + value.apply(t)));
        return this;
    }

    /** The column the table starts sorted by (an unknown id leaves the order as given). */
    public TableBuilder<T> sortBy(String columnId, boolean desc) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).id().equals(columnId)) {
                sortColumn = i;
            }
        }
        descending = desc;
        return this;
    }

    /** The action a click on a row sends. */
    public TableBuilder<T> open(String action) {
        open = Optional.of(action);
        return this;
    }

    public TableBuilder<T> rowAction(Action action) {
        rowActions.add(action);
        return this;
    }

    public TableBuilder<T> button(Action action) {
        buttons.add(action);
        return this;
    }

    public TableBuilder<T> empty(Component message) {
        empty = message;
        return this;
    }

    /** Most rows to send; the table says how many more there were. */
    public TableBuilder<T> limit(int rows) {
        limit = Math.max(0, Math.min(rows, UiCodecs.MAX_ROWS));
        return this;
    }

    public Page.Table build() {
        List<Row> rows = new ArrayList<>();
        int total = 0;
        for (T item : items) {
            total++;
            if (rows.size() >= limit) {
                continue;
            }
            List<Cell> row = new ArrayList<>(cells.size());
            for (Function<T, Cell> f : cells) {
                row.add(f.apply(item));
            }
            List<String> t = new ArrayList<>();
            for (Function<T, ? extends Collection<String>> f : tags) {
                t.addAll(f.apply(item));
            }
            rows.add(new Row(id.apply(item), row, t));
        }
        return new Page.Table(columns, filters, rows, sortColumn, descending, open, rowActions, buttons, total, empty);
    }
}
