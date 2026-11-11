package com.porcupinestudios.lotrport.ui;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** What one tab shows. Only the open tab is built and sent, so a view with many tabs costs one tab's work. */
public sealed interface Page permits Page.Table, Page.Stats, Page.Profile {
    /** Buttons under the page. */
    List<Action> buttons();

    /**
     * A list the player can search, sort and filter. All of it happens on the client ({@link TableQuery}), so typing
     * in the search box sends nothing.
     *
     * @param sortColumn the column it is sorted by until the player picks another (-1: as sent)
     * @param open the action a click on a row sends (open the citizen's page), if any
     * @param rowActions buttons that act on the selected row (set a role, take items...)
     * @param total how many rows there are; more than {@code rows.size()} means the server cut the list
     * @param empty shown when there are no rows at all
     */
    record Table(List<Column> columns, List<FilterGroup> filters, List<Row> rows, int sortColumn, boolean descending,
            Optional<String> open, List<Action> rowActions, List<Action> buttons, int total, Component empty)
            implements Page {
        public Table {
            columns = List.copyOf(columns);
            filters = List.copyOf(filters);
            rows = List.copyOf(rows);
            rowActions = List.copyOf(rowActions);
            buttons = List.copyOf(buttons);
        }
    }

    /** Headed lists of label and value lines (an overview, the treasury). */
    record Stats(List<Section> sections, List<Action> buttons) implements Page {
        public Stats {
            sections = List.copyOf(sections);
            buttons = List.copyOf(buttons);
        }
    }

    /**
     * One person or thing up close: a portrait, a name the player may change, a line under it, then headed sections
     * in two columns (needs and facts on the left, thoughts and orders on the right).
     *
     * @param portrait the entity whose head and shoulders are drawn, if its body is loaded on the client
     * @param rename the action a new name is sent with (as the row), if this player may rename
     * @param subtitle under the name (race, age, work)
     */
    record Profile(Optional<java.util.UUID> portrait, Component name, Optional<String> rename, Component subtitle,
            List<Section> left, List<Section> right, List<Action> buttons) implements Page {
        public Profile {
            left = List.copyOf(left);
            right = List.copyOf(right);
            buttons = List.copyOf(buttons);
        }
    }

    record Section(Component title, List<Stat> stats) {
        public Section {
            stats = List.copyOf(stats);
        }
    }
}
