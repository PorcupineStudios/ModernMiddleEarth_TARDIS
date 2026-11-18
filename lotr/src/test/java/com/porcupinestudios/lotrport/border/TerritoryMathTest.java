package com.porcupinestudios.lotrport.border;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TerritoryMathTest {
    private static final int W = 20;
    private static final int H = 20;

    /** Left half holder 0, right half holder 1; column 19 is sea. */
    private static int base(int i) {
        return i % W < 10 ? 0 : 1;
    }

    private static boolean land(int i) {
        return i % W != 19;
    }

    @Test
    void onlyChangesAreKept() {
        // holder 0 claims round (5, 5): its own land, so nothing differs from the base
        Map<Integer, Integer> o = TerritoryMath.overlay(W, H, List.of(new TerritoryMath.Claim(5, 5, 3, 0, 0)),
                TerritoryMathTest::base, TerritoryMathTest::land);
        assertTrue(o.isEmpty(), "own land showed as changed: " + o);
    }

    @Test
    void aClaimOnOtherLandIsForwarded() {
        Map<Integer, Integer> o = TerritoryMath.overlay(W, H, List.of(new TerritoryMath.Claim(15, 5, 2, 0, 0)),
                TerritoryMathTest::base, TerritoryMathTest::land);
        assertEquals(0, o.get(5 * W + 15));
        assertEquals(0, o.get(5 * W + 17));
        assertFalse(o.containsKey(5 * W + 18), "beyond the radius");
        assertFalse(o.containsKey(5 * W + 8), "holder 0's own land isn't a change");
    }

    @Test
    void strongerClaimWinsAndTiesGoToTheOlder() {
        // two claims 4 apart, radius 3 each: the middle pixel ties (strength 1 each) and goes to the older (order 0)
        List<TerritoryMath.Claim> claims = List.of(new TerritoryMath.Claim(12, 10, 3, 2, 5),
                new TerritoryMath.Claim(16, 10, 3, 3, 0));
        Map<Integer, Integer> o = TerritoryMath.overlay(W, H, claims, TerritoryMathTest::base, TerritoryMathTest::land);
        assertEquals(3, o.get(10 * W + 14), "tie to the older claim");
        assertEquals(2, o.get(10 * W + 13), "nearer the first Hall");
        assertEquals(3, o.get(10 * W + 15), "nearer the second Hall");
    }

    @Test
    void seaIsNeverHeld() {
        Map<Integer, Integer> o = TerritoryMath.overlay(W, H, List.of(new TerritoryMath.Claim(18, 5, 3, 0, 0)),
                TerritoryMathTest::base, TerritoryMathTest::land);
        assertFalse(o.containsKey(5 * W + 19));
    }

    @Test
    void radiusGrowsWithPeopleUpToTheCap() {
        assertEquals(2, TerritoryMath.radius(0, 2, 10, 6));
        assertEquals(4, TerritoryMath.radius(25, 2, 10, 6));
        assertEquals(4, TerritoryMath.radius(29, 2, 10, 6));
        assertEquals(6, TerritoryMath.radius(1000, 2, 10, 6));
        assertEquals(2, TerritoryMath.radius(1000, 2, 0, 6));
    }

    @Test
    void othersLandAndSteepTerrainAreNotSettled() {
        // claimable only where the base is nobody (row 0 marked nobody) or the claimer's own: holder 2 claims nothing
        // of holder 0's or 1's land; a terrain cost of 2 on (3, 0) keeps a radius-3 claim from 2 away off it
        TerritoryMath.Claimable onlyNobodys = (i, o) -> i / W == 0;
        Map<Integer, Integer> o = TerritoryMath.overlay(W, H, List.of(new TerritoryMath.Claim(5, 0, 3, 2, 0)),
                TerritoryMathTest::base, TerritoryMathTest::land, i -> i == 3 ? 2 : 0, onlyNobodys);
        assertEquals(2, o.get(5));
        assertEquals(2, o.get(4), "strength 2 >= no cost");
        assertFalse(o.containsKey(3), "strength 1 < cost 2: the steep square isn't settled");
        assertFalse(o.containsKey(W + 5), "row 1 is someone's land: taken by conquest, not settling");
    }
}
