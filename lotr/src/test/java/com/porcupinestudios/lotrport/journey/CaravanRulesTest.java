package com.porcupinestudios.lotrport.journey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** What caravans load and how they trade. */
class CaravanRulesTest {
    private static CaravanRules.Amount a(String item, int n) {
        return new CaravanRules.Amount(item, n);
    }

    @Test
    void loadingKeepsFloorsAndCapacity() {
        Map<String, Integer> have = Map.of("bread", 100, "iron", 30);
        Map<String, Integer> keep = Map.of("bread", 20);
        Map<String, Integer> l = CaravanRules.load(List.of(a("bread", 200), a("iron", 50)),
                i -> have.getOrDefault(i, 0), i -> keep.getOrDefault(i, 0), 100);
        assertEquals(80, l.get("bread"), "never below the floor");
        assertEquals(20, l.get("iron"), "only what fits");
        assertEquals(Map.of(), CaravanRules.load(List.of(), i -> 5, i -> 0, 100), "a pick-up loads nothing");
    }

    @Test
    void tradesByOfferStockLimitAndRoom() {
        CaravanRules.Offer wool = new CaravanRules.Offer(a("wool", 2), a("bread", 1), 0, 0);
        CaravanRules.Trade t = CaravanRules.trade(Map.of("bread", 10), List.of(wool), i -> i.equals("wool") ? 10 : 0,
                1000);
        assertEquals(5, t.paid().get("bread"), "5 bread for all 10 wool it has");
        assertEquals(10, t.got().get("wool"));
        CaravanRules.Offer capped = new CaravanRules.Offer(a("wool", 2), a("bread", 1), 4, 2);
        t = CaravanRules.trade(Map.of("bread", 10), List.of(capped), i -> 10, 1000);
        assertEquals(2, t.paid().get("bread"), "at most 2 a visit");
        t = CaravanRules.trade(Map.of("bread", 10), List.of(wool), i -> 100, 13);
        assertEquals(3, t.paid().get("bread"), "room: 10 carried, each trade adds one, capacity 13");
        assertEquals(Map.of(), CaravanRules.trade(Map.of("bread", 10), List.of(wool), i -> 1, 100).paid(),
                "not enough to give even once");
    }
}
