package com.porcupinestudios.lotrport.ui;

import net.minecraft.network.chat.Component;

/**
 * A table column.
 *
 * @param id stable id: what the client remembers the sort by
 * @param weight share of the table's width, against the other columns' weights
 * @param numeric sort by the cells' {@code sort} value instead of their text
 * @param searchable the search box looks in this column
 */
public record Column(String id, Component label, int weight, Align align, boolean numeric, boolean searchable) {
    public enum Align {
        LEFT, RIGHT, CENTER
    }

    public static Column text(String id, Component label, int weight) {
        return new Column(id, label, weight, Align.LEFT, false, true);
    }

    public static Column number(String id, Component label, int weight) {
        return new Column(id, label, weight, Align.RIGHT, true, false);
    }
}
