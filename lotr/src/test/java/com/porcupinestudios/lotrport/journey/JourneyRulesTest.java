package com.porcupinestudios.lotrport.journey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Routes over a made-up land, party speed and load, and what makes a bridge. */
class JourneyRulesTest {
    private static final RoutePlanner.Settings S = new RoutePlanner.Settings(16, 4, 1.6, 400, 50000);

    /** Grassland with a river along x = 200..207, a mountain block, a road on z = 0..2 and a bridge at z 100. */
    static final class Land implements TerrainView {
        boolean bridgeAt100;
        boolean mountains;

        @Override
        public double speed(int x, int z) {
            return mountains && x > 60 && x < 140 && z > -200 && z < 200 ? 0.15 : 1;
        }

        @Override
        public boolean road(int x, int z) {
            return z >= 0 && z <= 2;
        }

        @Override
        public boolean river(int x, int z) {
            return x >= 200 && x <= 207;
        }

        @Override
        public boolean bridge(int x, int z) {
            return bridgeAt100 && z >= 98 && z <= 102 && x >= 196 && x <= 211;
        }
    }

    @Test
    void aRiverIsCrossedOnlyByARoadOrABridge() {
        Land land = new Land();
        Route r = RoutePlanner.plan(land, S, 100, 100, 300, 100);
        assertTrue(r.found(), "the road's bridge at z 0 gets across");
        boolean viaRoad = false;
        for (double d = 0; d <= r.total(); d += 1) {
            double[] p = r.at(d);
            if (p[0] >= 200 && p[0] <= 207) {
                viaRoad |= p[1] <= 16;
            }
        }
        assertTrue(viaRoad, "it went round by the road");
        land.bridgeAt100 = true;
        Route straight = RoutePlanner.plan(land, S, 100, 100, 300, 100);
        assertTrue(straight.total() < r.total(), "a bridge on the way is quicker: " + straight.total() + " vs "
                + r.total());
    }

    @Test
    void mountainsAreGoneRoundAndRoadsAreTaken() {
        Land land = new Land();
        land.mountains = true;
        Route r = RoutePlanner.plan(land, S, 20, 40, 180, 40);
        assertTrue(r.found());
        // straight over the mountains would take 80 / 0.15 = 533 for that stretch; round by the road is far less
        assertTrue(r.total() < 400, "took " + r.total());
        assertTrue(r.roadShare() > 0.3, "most of it on the road: " + r.roadShare());
        double[] mid = r.at(r.total() / 2);
        assertTrue(mid[1] < 60, "halfway it is down on the road, not in the mountains: z " + mid[1]);
    }

    /** Aaron (2026-11-11): a party that follows a road stays on it the whole way, bends and its bridge included. */
    @Test
    void aRouteOnAWindingRoadNeverLeavesIt() {
        RoutePlanner.Settings s = new RoutePlanner.Settings(32, 8, 1.6, 400, 60000);
        TerrainView land = new TerrainView() {
            @Override
            public double speed(int x, int z) {
                return 0.5;
            }

            @Override
            public boolean road(int x, int z) {
                return Math.abs(z - 40 * Math.sin(x / 60.0)) <= 2;
            }

            @Override
            public boolean river(int x, int z) {
                return x >= 500 && x <= 520;
            }

            @Override
            public boolean bridge(int x, int z) {
                return false;
            }
        };
        Route r = RoutePlanner.plan(land, s, 0, 0, 1000, (int) Math.round(40 * Math.sin(1000 / 60.0)));
        assertTrue(r.found(), "no route along the road");
        for (double d = 0; d <= r.total(); d += 1) {
            double[] p = r.at(d);
            assertTrue(Math.abs(p[1] - 40 * Math.sin(p[0] / 60.0)) <= 3, "off the road at " + p[0] + ", " + p[1]);
        }
    }

    /** Aaron (2026-11-11): a marked bridge always gives a way across, however it lies against the planner's grid. */
    @Test
    void aMarkedBridgeAlwaysCrosses() {
        RoutePlanner.Settings s = new RoutePlanner.Settings(32, 8, 1.6, 400, 60000);
        for (int shift = 0; shift < 32; shift += 5) {
            final int o = shift;
            TerrainView land = new TerrainView() {
                @Override
                public double speed(int x, int z) {
                    return 1;
                }

                @Override
                public boolean road(int x, int z) {
                    return false;
                }

                @Override
                public boolean river(int x, int z) {
                    return x >= 500 + o && x <= 540 + o;
                }

                @Override
                public boolean bridge(int x, int z) {
                    double t = (x - 495 - o) / 50.0;
                    return t >= 0 && t <= 1 && Math.abs(z - (301 + o + 32 * t)) <= 3;
                }

                @Override
                public List<int[]> bridgeEnds() {
                    return List.of(new int[] {495 + o, 301 + o, 545 + o, 333 + o});
                }
            };
            assertTrue(RoutePlanner.plan(land, s, 100, 100, 900, 100).found(), "no way over the bridge at shift "
                    + o);
        }
    }

    @Test
    void noWayIsNoRoute() {
        TerrainView sea = new TerrainView() {
            @Override
            public double speed(int x, int z) {
                return x > 50 ? 0 : 1;
            }

            @Override
            public boolean road(int x, int z) {
                return false;
            }

            @Override
            public boolean river(int x, int z) {
                return false;
            }

            @Override
            public boolean bridge(int x, int z) {
                return false;
            }
        };
        assertFalse(RoutePlanner.plan(sea, S, 0, 0, 100, 0).found());
    }

    @Test
    void routesRunBackwardsAndKnowWhereAPartyIs() {
        Route r = RoutePlanner.plan(new Land(), S, 0, 50, 150, 50);
        Route back = r.reversed();
        assertEquals(r.total(), back.total(), 1e-9);
        double[] p = r.at(r.total() * 0.5);
        double done = r.doneAt(p[0], p[1], 0);
        assertEquals(r.total() * 0.5, done, r.total() * 0.05);
    }

    @Test
    void steedsAndWagonsSpeedAndCarry() {
        PartyRules.Steed horse = new PartyRules.Steed(2.2, 4, true);
        PartyRules.Steed donkey = new PartyRules.Steed(1.4, 9, true);
        PartyRules.Steed elk = new PartyRules.Steed(1.9, 4, false);
        assertEquals(1, PartyRules.of(1, List.of(), 0, 0.85, 4, 27).speed(), 1e-9, "on foot");
        assertEquals(4, PartyRules.of(1, List.of(), 0, 0.85, 4, 27).stacks());
        assertEquals(2.2, PartyRules.of(1, List.of(horse), 0, 0.85, 4, 27).speed(), 1e-9, "riding");
        assertEquals(1, PartyRules.of(2, List.of(horse), 0, 0.85, 4, 27).speed(), 1e-9, "one walks");
        PartyRules.Result wagon = PartyRules.of(1, List.of(horse, elk), 1, 0.85, 4, 27);
        assertTrue(wagon.ok());
        assertEquals(2.2 * 0.85, wagon.speed(), 1e-9, "the horse draws, the elk is ridden: the wagon is slower");
        assertEquals(4 + 27 + 4 + 4, wagon.stacks());
        assertFalse(PartyRules.of(1, List.of(elk), 1, 0.85, 4, 27).ok(), "elk can't draw a wagon");
        assertEquals(1.4, PartyRules.of(1, List.of(horse, donkey), 0, 0.85, 4, 27).speed(), 1e-9,
                "a led donkey holds the rider back");
    }

    @Test
    void aBridgeHasADeckAllTheWayDryEndsAndARiverUnder() {
        BridgeRules.Column land = new BridgeRules.Column(true, true, false, false);
        BridgeRules.Column over = new BridgeRules.Column(true, true, true, true);
        assertEquals(Optional.empty(), BridgeRules.check(List.of(land, over, over, land), 64));
        assertEquals(Optional.of("no_river"), BridgeRules.check(List.of(land, land, land), 64));
        assertEquals(Optional.of("gap"), BridgeRules.check(List.of(land, over,
                new BridgeRules.Column(false, true, true, true), land), 64));
        assertEquals(Optional.of("ends_in_water"), BridgeRules.check(List.of(over, over, land), 64));
        assertEquals(Optional.of("blocked"), BridgeRules.check(List.of(land,
                new BridgeRules.Column(true, false, true, true), land), 64));
        assertEquals(Optional.of("too_long"), BridgeRules.check(java.util.Collections.nCopies(70, land), 64));
    }
}
