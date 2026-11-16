package com.porcupinestudios.lotrport.client.ui;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the player did to one table: search text, ticks, sort, scroll and the selected row. Kept per view, context
 * and tab for the session, so refreshes, tab switches and reopening the screen keep them.
 */
public final class TableState {
    private static final Map<String, TableState> STATES = new HashMap<>();

    public String search = "";
    public final Map<String, Set<String>> ticked = new LinkedHashMap<>();
    /** The sort the player picked (column id), or null for the table's own. */
    public String sortColumn;
    public boolean descending;
    public double scroll;
    public String selected;

    public static TableState of(String key) {
        return STATES.computeIfAbsent(key, k -> new TableState());
    }

    public boolean isTicked(String group, String tag) {
        return ticked.getOrDefault(group, Set.of()).contains(tag);
    }

    public void toggle(String group, String tag) {
        Set<String> set = ticked.computeIfAbsent(group, g -> new LinkedHashSet<>());
        if (!set.remove(tag)) {
            set.add(tag);
        }
        scroll = 0;
    }

    public void clearTicks() {
        ticked.clear();
        scroll = 0;
    }
}
