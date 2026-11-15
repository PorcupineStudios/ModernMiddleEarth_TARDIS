package com.porcupinestudios.lotrport.ui;

import java.util.List;

/**
 * One table row.
 *
 * @param id what an action on this row sends back (a person's or a building's id, an item id...)
 * @param cells one per column, in column order
 * @param tags what the filters match: a row shows under a filter when it has the filter's tag
 */
public record Row(String id, List<Cell> cells, List<String> tags) {
    public Row {
        cells = List.copyOf(cells);
        tags = List.copyOf(tags);
    }

    public Cell cell(int column) {
        return column >= 0 && column < cells.size() ? cells.get(column) : Cell.text("");
    }
}
