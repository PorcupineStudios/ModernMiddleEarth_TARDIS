package com.porcupinestudios.lotrport.colonist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Homes by household: families under one roof, in a house of their own when one is free, nobody thrown out. */
class HouseRulesTest {
    private static final UUID SMALL = new UUID(9, 1);
    private static final UUID BIG = new UUID(9, 2);
    private static final UUID HOVEL = new UUID(9, 3);

    private static UUID p(int n) {
        return new UUID(0, n);
    }

    private static HouseRules.Resident r(int person, int household, UUID home) {
        return new HouseRules.Resident(p(person), p(household), Optional.ofNullable(home));
    }

    @Test
    void aHomelessFamilyTakesAnEmptyHouseThatFitsThemAll() {
        // the best home (BIG) has a lodger in it; the family of three gets the empty SMALL house instead
        List<HouseRules.Home> homes = List.of(new HouseRules.Home(BIG, 50, 4), new HouseRules.Home(SMALL, 20, 3));
        Map<UUID, UUID> out = HouseRules.assign(homes, List.of(r(1, 1, BIG), r(2, 2, null), r(3, 2, null),
                r(4, 2, null)));
        assertEquals(BIG, out.get(p(1)), "the lodger kept their home");
        assertEquals(SMALL, out.get(p(2)));
        assertEquals(SMALL, out.get(p(3)));
        assertEquals(SMALL, out.get(p(4)));
    }

    @Test
    void familiesChooseBeforeSingles() {
        // one empty house of 2 and one of 1: the couple must get the 2, even though the single comes first in the list
        List<HouseRules.Home> homes = List.of(new HouseRules.Home(BIG, 50, 2), new HouseRules.Home(SMALL, 10, 1));
        Map<UUID, UUID> out = HouseRules.assign(homes, List.of(r(1, 1, null), r(2, 2, null), r(3, 2, null)));
        assertEquals(BIG, out.get(p(2)));
        assertEquals(BIG, out.get(p(3)));
        assertEquals(SMALL, out.get(p(1)));
    }

    @Test
    void aChildMovesIntoTheParentsHomeWhenThereIsABed() {
        // parents in BIG (one bed free), their child housed alone in HOVEL: the child joins them, HOVEL frees up
        List<HouseRules.Home> homes = List.of(new HouseRules.Home(BIG, 50, 3), new HouseRules.Home(HOVEL, 5, 2));
        Map<UUID, UUID> out = HouseRules.assign(homes, List.of(r(1, 1, BIG), r(2, 1, BIG), r(3, 1, HOVEL),
                r(4, 4, null), r(5, 4, null)));
        assertEquals(BIG, out.get(p(3)), "the child stayed apart");
        assertEquals(HOVEL, out.get(p(4)), "the new couple didn't get the freed house");
        assertEquals(HOVEL, out.get(p(5)));
    }

    @Test
    void whenNoHouseFitsAFamilyItsMembersStillGetBeds() {
        List<HouseRules.Home> homes = List.of(new HouseRules.Home(BIG, 50, 2), new HouseRules.Home(SMALL, 20, 2));
        Map<UUID, UUID> out = HouseRules.assign(homes, List.of(r(1, 1, null), r(2, 1, null), r(3, 1, null),
                r(4, 9, null), r(5, 9, null)));
        assertEquals(4, out.size(), "four beds, four housed");
        long inBig = out.values().stream().filter(BIG::equals).count();
        assertEquals(2, inBig);
        // the family of three was placed first: two of them share the best house, the third the next
        assertEquals(BIG, out.get(p(1)));
        assertEquals(BIG, out.get(p(2)));
        assertEquals(SMALL, out.get(p(3)));
        assertTrue(out.containsKey(p(4)) != out.containsKey(p(5)), "exactly one of the second family is housed");
    }

    @Test
    void lostHomesAndFullHomesLeavePeopleHomelessWithoutThrowingAnyoneOut() {
        UUID gone = new UUID(9, 9);
        List<HouseRules.Home> homes = List.of(new HouseRules.Home(SMALL, 20, 1));
        Map<UUID, UUID> out = HouseRules.assign(homes, List.of(r(1, 1, SMALL), r(2, 2, gone)));
        assertEquals(SMALL, out.get(p(1)));
        assertNull(out.get(p(2)), "someone whose home is gone and finds no bed is homeless");
        assertFalse(out.containsKey(p(2)));
    }

    @Test
    void someoneToldWhereToLiveGoesThereFirstAndStaysThere() {
        // 1 lives in BIG but is told to live in the HOVEL; their household (with 2) would gather them in BIG
        List<HouseRules.Home> homes = List.of(new HouseRules.Home(BIG, 50, 4), new HouseRules.Home(HOVEL, 5, 1));
        Map<UUID, UUID> out = HouseRules.assign(homes, List.of(
                new HouseRules.Resident(p(1), p(1), Optional.of(BIG), Optional.of(HOVEL)), r(2, 1, BIG),
                r(3, 3, null)));
        assertEquals(HOVEL, out.get(p(1)), "the order wins over keeping and gathering");
        assertEquals(BIG, out.get(p(2)));
        assertEquals(BIG, out.get(p(3)), "the freed bed in BIG goes to the homeless");
    }

    @Test
    void anOrderTakesTheBedAndWhoeverHadItMoves() {
        List<HouseRules.Home> homes = List.of(new HouseRules.Home(HOVEL, 5, 1), new HouseRules.Home(BIG, 50, 4));
        Map<UUID, UUID> out = HouseRules.assign(homes, List.of(r(1, 1, HOVEL),
                new HouseRules.Resident(p(2), p(2), Optional.of(BIG), Optional.of(HOVEL))));
        // the hovel's one bed: 1 sleeps there, 2 is told to live there; orders come first, 1 finds another bed
        assertEquals(HOVEL, out.get(p(2)));
        assertEquals(BIG, out.get(p(1)), "1 lost the hovel's bed to the order and found one in BIG");
    }
}
