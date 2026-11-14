package com.porcupinestudios.tardisrb.gametest;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * What a sequence test saw, tick by tick (only when it changed), for its failure message: a waitUntil only reports
 * its last attempt, which hides what went wrong earlier. Keeps the last {@code keep} changes.
 */
final class TestTrace {
    private final Deque<String> lines = new ArrayDeque<>();
    private final int keep;
    private String last = "";
    private int ticks;

    TestTrace(int keep) {
        this.keep = keep;
    }

    /** Records {@code state} for this tick, if it differs from the last one recorded. */
    void see(String state) {
        ticks++;
        if (!state.equals(last)) {
            last = state;
            lines.addLast("t" + ticks + " " + state);
            if (lines.size() > keep) {
                lines.removeFirst();
            }
        }
    }

    @Override
    public String toString() {
        return " | trace: " + String.join(" ; ", lines);
    }
}
