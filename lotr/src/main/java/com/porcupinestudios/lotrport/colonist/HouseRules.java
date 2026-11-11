package com.porcupinestudios.lotrport.colonist;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who sleeps in which home, keeping households together (Aaron 2026-11-10: children live in their parents' house until
 * they come of age, and families like a house of their own). Plain rules: data in, data out.
 *
 * <ol>
 *   <li>Everyone keeps a home that still stands and still has their bed, in the order given.</li>
 *   <li>Each household gathers in the home where most of it already sleeps, as far as its beds allow.</li>
 *   <li>The homeless are placed by household, biggest first, so families pick before single settlers: into their
 *       household's home if it has room, else an empty home that fits them all (best first), else any home that fits
 *       them all, else one by one into the best free beds.</li>
 * </ol>
 * Nobody is moved out of a home to make room, so homes don't churn day to day.
 */
public final class HouseRules {
    private HouseRules() {}

    /** A home: its beds and quality. */
    public record Home(UUID id, int quality, int beds) {}

    /** A citizen: their household and the home they have now. */
    public record Resident(UUID person, UUID household, Optional<UUID> home) {}

    /** The home of each resident afterwards (absent = homeless). */
    public static Map<UUID, UUID> assign(List<Home> homes, List<Resident> residents) {
        Map<UUID, Home> byId = new HashMap<>();
        Map<UUID, Integer> free = new HashMap<>();
        for (Home h : homes) {
            byId.put(h.id(), h);
            free.put(h.id(), Math.max(0, h.beds()));
        }
        Map<UUID, UUID> out = new LinkedHashMap<>();
        // 1. keep
        for (Resident r : residents) {
            r.home().filter(h -> free.getOrDefault(h, 0) > 0).ifPresent(h -> {
                out.put(r.person(), h);
                free.merge(h, -1, Integer::sum);
            });
        }
        Map<UUID, List<Resident>> households = new LinkedHashMap<>();
        for (Resident r : residents) {
            households.computeIfAbsent(r.household(), k -> new ArrayList<>()).add(r);
        }
        // 2. gather each household in its main home
        for (List<Resident> members : households.values()) {
            if (members.size() < 2) {
                continue;
            }
            Optional<UUID> anchor = anchor(members, out, byId);
            if (anchor.isEmpty()) {
                continue;
            }
            for (Resident r : members) {
                UUID now = out.get(r.person());
                if (now != null && !now.equals(anchor.get()) && free.getOrDefault(anchor.get(), 0) > 0) {
                    free.merge(now, 1, Integer::sum);
                    free.merge(anchor.get(), -1, Integer::sum);
                    out.put(r.person(), anchor.get());
                }
            }
        }
        // 3. the homeless, biggest households first
        List<List<Resident>> waiting = new ArrayList<>();
        for (List<Resident> members : households.values()) {
            List<Resident> homeless = members.stream().filter(r -> !out.containsKey(r.person())).toList();
            if (!homeless.isEmpty()) {
                waiting.add(homeless);
            }
        }
        waiting.sort(Comparator.comparingInt((List<Resident> l) -> -l.size())
                .thenComparing(l -> l.get(0).household()));
        Comparator<Home> best = Comparator.comparingInt(Home::quality).reversed().thenComparing(Home::id);
        List<Home> ordered = homes.stream().sorted(best).toList();
        for (List<Resident> group : waiting) {
            List<Resident> left = new ArrayList<>(group);
            Optional<UUID> anchor = anchor(households.get(group.get(0).household()), out, byId);
            if (anchor.isPresent()) {
                place(left, anchor.get(), free, out);
            }
            if (left.isEmpty()) {
                continue;
            }
            int need = left.size();
            Optional<Home> pick = ordered.stream()
                    .filter(h -> free.get(h.id()) >= need && free.get(h.id()) == Math.max(0, h.beds())).findFirst();
            if (pick.isEmpty()) {
                pick = ordered.stream().filter(h -> free.get(h.id()) >= need).findFirst();
            }
            if (pick.isPresent()) {
                place(left, pick.get().id(), free, out);
                continue;
            }
            for (Home h : ordered) {
                place(left, h.id(), free, out);
            }
        }
        return out;
    }

    /** The home where most of this household sleeps (ties: the better home, then the lower id). */
    private static Optional<UUID> anchor(List<Resident> members, Map<UUID, UUID> out, Map<UUID, Home> byId) {
        Map<UUID, Integer> count = new HashMap<>();
        for (Resident r : members) {
            UUID h = out.get(r.person());
            if (h != null) {
                count.merge(h, 1, Integer::sum);
            }
        }
        return count.entrySet().stream().max(Comparator.comparingInt((Map.Entry<UUID, Integer> e) -> e.getValue())
                .thenComparingInt(e -> byId.get(e.getKey()).quality())
                .thenComparing(e -> e.getKey(), Comparator.reverseOrder())).map(Map.Entry::getKey);
    }

    private static void place(List<Resident> left, UUID home, Map<UUID, Integer> free, Map<UUID, UUID> out) {
        while (!left.isEmpty() && free.getOrDefault(home, 0) > 0) {
            Resident r = left.remove(0);
            out.put(r.person(), home);
            free.merge(home, -1, Integer::sum);
        }
    }
}
