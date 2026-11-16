package com.porcupinestudios.tardisrb.gametest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * What a sequence test saw, tick by tick (only when it changed), for its failure message: a waitUntil only reports
 * its last attempt, which hides what went wrong earlier. Keeps the first {@code head} changes (the set-up) and the
 * last {@code tail}.
 */
final class TestTrace {
    private final List<String> first = new ArrayList<>();
    private final Deque<String> last = new ArrayDeque<>();
    private final int head;
    private final int tail;
    private String prev = "";
    private int ticks;
    private int dropped;

    TestTrace(int head, int tail) {
        this.head = head;
        this.tail = tail;
    }

    /** Records {@code state} for this tick, if it differs from the last one recorded. */
    void see(String state) {
        ticks++;
        note(state);
    }

    /** Records a one-off event (not a tick). */
    void note(String state) {
        if (state.equals(prev)) {
            return;
        }
        prev = state;
        String line = "t" + ticks + " " + state;
        if (first.size() < head) {
            first.add(line);
            return;
        }
        last.addLast(line);
        if (last.size() > tail) {
            last.removeFirst();
            dropped++;
        }
    }

    int ticks() {
        return ticks;
    }

    @Override
    public String toString() {
        return " | trace: " + String.join(" ; ", first) + (dropped > 0 ? " ; ..." + dropped + " more... ; " : " ; ")
                + String.join(" ; ", last);
    }
}
