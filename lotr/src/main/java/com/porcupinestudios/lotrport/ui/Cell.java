package com.porcupinestudios.lotrport.ui;

/**
 * One table cell. The server resolves the text (as the Ledger does); {@code sort} orders numeric columns, so "Jobless"
 * can sort below every job and "12 / 40" can sort by 12.
 */
public record Cell(String text, Tone tone, double sort) {
    public static Cell text(String text) {
        return new Cell(text, Tone.NORMAL, 0);
    }

    public static Cell text(String text, Tone tone) {
        return new Cell(text, tone, 0);
    }

    public static Cell number(long value) {
        return new Cell(Long.toString(value), Tone.NORMAL, value);
    }

    public static Cell number(double sort, String text, Tone tone) {
        return new Cell(text, tone, sort);
    }
}
