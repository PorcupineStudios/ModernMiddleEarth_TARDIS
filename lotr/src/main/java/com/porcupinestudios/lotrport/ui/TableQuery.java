package com.porcupinestudios.lotrport.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a table shows for a search, ticked filters and a sort: plain rules, no game classes, unit-tested.
 *
 * <ul>
 *   <li>Search: every word must appear in some searchable column, ignoring case.</li>
 *   <li>Filters: within a group, a row needs any one ticked tag; across groups, it needs every group that has a tick.
 *       Ticks of a group not in the table are ignored.</li>
 *   <li>Sort: numeric columns by the cells' {@code sort}, others by text ignoring case; ties by the first column,
 *       then by row id, so the order never jumps between refreshes.</li>
 * </ul>
 */
public final class TableQuery {
    private TableQuery() {}

    /**
     * @param ticked ticked filter tags by group id
     * @param sortColumn column index, or -1 to keep the order sent
     */
    public static List<Row> apply(List<Column> columns, List<FilterGroup> groups, List<Row> rows, String search,
            Map<String, Set<String>> ticked, int sortColumn, boolean descending) {
        String[] words = search == null ? new String[0] : search.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<Row> out = new ArrayList<>();
        for (Row row : rows) {
            if (matchesSearch(columns, row, words) && matchesFilters(groups, row, ticked)) {
                out.add(row);
            }
        }
        if (sortColumn >= 0 && sortColumn < columns.size()) {
            out.sort(comparator(columns, sortColumn, descending));
        }
        return out;
    }

    static boolean matchesSearch(List<Column> columns, Row row, String[] words) {
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (int c = 0; c < columns.size() && !found; c++) {
                found = columns.get(c).searchable() && row.cell(c).text().toLowerCase(Locale.ROOT).contains(word);
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    static boolean matchesFilters(List<FilterGroup> groups, Row row, Map<String, Set<String>> ticked) {
        for (FilterGroup group : groups) {
            Set<String> on = ticked.getOrDefault(group.id(), Set.of());
            if (on.isEmpty()) {
                continue;
            }
            boolean any = false;
            for (FilterGroup.Filter f : group.filters()) {
                if (on.contains(f.tag()) && row.tags().contains(f.tag())) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    /** How many filters are ticked, counting only those the table has. */
    public static int tickedCount(List<FilterGroup> groups, Map<String, Set<String>> ticked) {
        int n = 0;
        for (FilterGroup group : groups) {
            Set<String> on = ticked.getOrDefault(group.id(), Set.of());
            for (FilterGroup.Filter f : group.filters()) {
                if (on.contains(f.tag())) {
                    n++;
                }
            }
        }
        return n;
    }

    static Comparator<Row> comparator(List<Column> columns, int sortColumn, boolean descending) {
        Column col = columns.get(sortColumn);
        Comparator<Row> by = col.numeric()
                ? Comparator.comparingDouble((Row r) -> r.cell(sortColumn).sort())
                : Comparator.comparing((Row r) -> r.cell(sortColumn).text().toLowerCase(Locale.ROOT));
        if (descending) {
            by = by.reversed();
        }
        if (sortColumn != 0) {
            by = by.thenComparing(r -> r.cell(0).text().toLowerCase(Locale.ROOT));
        }
        return by.thenComparing(Row::id);
    }
}
