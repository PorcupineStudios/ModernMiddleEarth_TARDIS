package com.porcupinestudios.lotrport.ui;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * A heading of filters ("Status", "Race"). Ticked filters in one group widen the list (homeless or jobless); ticked
 * filters in different groups narrow it (and a dwarf). See {@link TableQuery}.
 */
public record FilterGroup(String id, Component label, List<Filter> filters) {
    public FilterGroup {
        filters = List.copyOf(filters);
    }

    /** A tick box: rows with {@code tag} pass it. */
    public record Filter(String tag, Component label) {}
}
