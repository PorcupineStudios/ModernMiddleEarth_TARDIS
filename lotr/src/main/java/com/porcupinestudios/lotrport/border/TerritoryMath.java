package com.porcupinestudios.lotrport.border;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * Who holds each map pixel, worked out on top of the original map without changing it. Pure (ints only, unit
 * tested): owners are small numbers the caller maps to factions, -1 = nobody.
 *
 * <p>Each claim (a colony) reaches {@code radius} map pixels from its Hall; its strength at a pixel is radius minus
 * distance. The strongest claim takes the pixel (ties: the older claim, lower {@code order}). The result holds only
 * pixels whose holder differs from the original map's: that is the overlay forwarded onto the player map.
 */
public final class TerritoryMath {
    /** A colony's reach: Hall pixel, radius in map pixels, holder, and age order (older first). */
    public record Claim(int px, int pz, double radius, int owner, long order) {}

    private TerritoryMath() {}

    /**
     * @param base the original map's holder of a pixel index ({@code pz * width + px}), -1 for nobody
     * @param land whether a pixel index can be held at all (not sea)
     * @return pixel index -> holder, only where it differs from {@code base}
     */
    public static Map<Integer, Integer> overlay(int width, int height, List<Claim> claims, IntUnaryOperator base,
            IntPredicate land) {
        Map<Integer, Double> best = new HashMap<>();
        Map<Integer, Claim> winner = new HashMap<>();
        for (Claim c : claims) {
            int r = (int) Math.ceil(c.radius());
            for (int z = Math.max(0, c.pz() - r); z <= Math.min(height - 1, c.pz() + r); z++) {
                for (int x = Math.max(0, c.px() - r); x <= Math.min(width - 1, c.px() + r); x++) {
                    double d = Math.hypot(x - c.px(), z - c.pz());
                    if (d > c.radius()) {
                        continue;
                    }
                    int i = z * width + x;
                    if (!land.test(i)) {
                        continue;
                    }
                    double s = c.radius() - d;
                    Double old = best.get(i);
                    Claim held = winner.get(i);
                    if (old == null || s > old || (s == old && c.order() < held.order())) {
                        best.put(i, s);
                        winner.put(i, c);
                    }
                }
            }
        }
        Map<Integer, Integer> out = new HashMap<>();
        winner.forEach((i, c) -> {
            if (base.applyAsInt(i) != c.owner()) {
                out.put(i, c.owner());
            }
        });
        return out;
    }

    /**
     * A colony's radius: {@code base} plus one pixel per whole {@code perPeople} people, at most {@code max}. Whole
     * steps, so a citizen more or less doesn't move borders (and write history) every recompute.
     */
    public static double radius(int people, double base, int perPeople, double max) {
        return Math.min(max, base + (perPeople > 0 ? people / perPeople : 0));
    }
}
