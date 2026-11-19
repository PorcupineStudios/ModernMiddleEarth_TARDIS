package com.porcupinestudios.lotrport.border;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WaterFillTest {
    // biomes: 0 = land of holder 0, 1 = land of holder 1, 2 = water (fill), 3 = wilds (nobody's)
    private static int holder(int biome) {
        return biome == 2 ? WaterFill.FILL : biome == 3 ? WaterFill.NONE : biome;
    }

    private static WaterFill fill(String... rows) {
        return WaterFill.of(rows[0].length(), rows.length, 4, (x, z) -> rows[z].charAt(x) - '0',
                WaterFillTest::holder);
    }

    @Test
    void aRiverThroughOneLandIsThatLands() {
        WaterFill f = fill(
                "00200",
                "00200",
                "00200");
        assertEquals(0, f.ownerOf(2));
        assertEquals(0, f.ownerOf(7));
        assertEquals(0, f.ownerOf(12));
    }

    @Test
    void aWideLakeFillsFromItsShores() {
        WaterFill f = fill(
                "0000000",
                "0222220",
                "0222220",
                "0222220",
                "0000000");
        assertEquals(0, f.ownerOf(3 + 2 * 7), "the middle of the lake");
    }

    @Test
    void aBorderRiverIsSplitDownTheMiddle() {
        WaterFill f = fill(
                "0022211",
                "0022211");
        assertEquals(0, f.ownerOf(2), "the bank by holder 0");
        assertEquals(1, f.ownerOf(4), "the bank by holder 1");
    }

    @Test
    void waterInTheWildsIsNobodys() {
        WaterFill f = fill(
                "33333",
                "32223",
                "33333");
        assertEquals(WaterFill.NONE, f.ownerOf(7));
    }

    @Test
    void landIsNotFilled() {
        WaterFill f = fill("020");
        assertEquals(WaterFill.NONE, f.ownerOf(0), "a land pixel isn't water");
    }
}
