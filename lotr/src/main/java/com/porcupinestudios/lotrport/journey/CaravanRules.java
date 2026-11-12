package com.porcupinestudios.lotrport.journey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Plain rules for what a caravan carries (no world: unit-tested). Goods are counted per item id; a caravan carries
 * {@code capacity} items in all (its stacks times 64).
 */
public final class CaravanRules {
    private CaravanRules() {}

    /**
     * An item and how many: a fixed {@code count}, or with {@code over} 0 or more, everything above {@code over}
     * (capped at {@code count} when that is above 0): "send the excess over 200".
     */
    public record Amount(String item, int count, int over) {
        public Amount(String item, int count) {
            this(item, count, -1);
        }

        public boolean excess() {
            return over >= 0;
        }
    }

    /** "When {@code colony}'s {@code item} is below (or above) {@code threshold}": a route that goes on its own. */
    public record Trigger(java.util.UUID colony, String item, boolean above, int threshold) {
        public boolean met(int have) {
            return above ? have > threshold : have < threshold;
        }
    }

    /**
     * A standing trade a colony offers caravans from friendly colonies: for every {@code want} delivered it gives
     * {@code give}, while it keeps at least {@code keep} of the given item itself. {@code limit} caps how many times
     * per visit (0 = no cap).
     */
    public record Offer(Amount give, Amount want, int keep, int limit) {}

    /**
     * What to load from a stock: each wanted amount in turn, never taking a stock below its floor, until the
     * capacity is full.
     *
     * @param have how many of an item the stock holds
     * @param keep the floor for an item (0 for none)
     */
    public static Map<String, Integer> load(List<Amount> want, ToIntFunction<String> have, ToIntFunction<String> keep,
            int capacity) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int room = Math.max(0, capacity);
        for (Amount a : want) {
            if (room <= 0) {
                break;
            }
            int floor = Math.max(keep.applyAsInt(a.item()), a.excess() ? a.over() : 0);
            int spare = Math.max(0, have.applyAsInt(a.item()) - out.getOrDefault(a.item(), 0) - floor);
            int want = a.excess() ? (a.count() > 0 ? a.count() : Integer.MAX_VALUE) : a.count();
            int n = Math.min(Math.min(want, spare), room);
            if (n > 0) {
                out.merge(a.item(), n, Integer::sum);
                room -= n;
            }
        }
        return out;
    }

    /** The result of trading at a colony: what the caravan hands over and what it gets. */
    public record Trade(Map<String, Integer> paid, Map<String, Integer> got) {}

    /**
     * Trades a caravan's cargo against a colony's offers, in order. Each offer runs as many times as the caravan has
     * the wanted goods, the colony has the given goods above its floor, the limit allows, and the caravan has room
     * for what it gets (what it pays frees room first).
     *
     * @param cargo what the caravan carries (not changed)
     * @param have the colony's stock of an item
     */
    public static Trade trade(Map<String, Integer> cargo, List<Offer> offers, ToIntFunction<String> have,
            int capacity) {
        Map<String, Integer> left = new LinkedHashMap<>(cargo);
        Map<String, Integer> paid = new LinkedHashMap<>();
        Map<String, Integer> got = new LinkedHashMap<>();
        Map<String, Integer> given = new LinkedHashMap<>();
        for (Offer o : offers) {
            if (o.want().count() <= 0 || o.give().count() <= 0) {
                continue;
            }
            int times = left.getOrDefault(o.want().item(), 0) / o.want().count();
            int spare = have.applyAsInt(o.give().item()) - given.getOrDefault(o.give().item(), 0)
                    - o.keep();
            times = Math.min(times, Math.max(0, spare) / o.give().count());
            if (o.limit() > 0) {
                times = Math.min(times, o.limit());
            }
            // room: carried now minus what it pays plus what it gets must stay within capacity
            int carried = left.values().stream().mapToInt(Integer::intValue).sum();
            int net = o.give().count() - o.want().count();
            if (net > 0) {
                times = Math.min(times, Math.max(0, capacity - carried) / net);
            }
            if (times <= 0) {
                continue;
            }
            int pay = times * o.want().count();
            int get = times * o.give().count();
            left.merge(o.want().item(), -pay, Integer::sum);
            left.remove(o.want().item(), 0);
            left.merge(o.give().item(), get, Integer::sum);
            paid.merge(o.want().item(), pay, Integer::sum);
            got.merge(o.give().item(), get, Integer::sum);
            given.merge(o.give().item(), get, Integer::sum);
        }
        return new Trade(paid, got);
    }

    /** How many items a cargo holds. */
    public static int size(Map<String, Integer> cargo) {
        return cargo.values().stream().mapToInt(Integer::intValue).sum();
    }
}
