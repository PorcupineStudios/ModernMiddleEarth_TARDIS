package com.porcupinestudios.lotrport.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RaceSkinRemapTest {
    private static final int S = RaceSkinRemap.SIZE;
    private static final int HAT = 0xFF0000AA, LEG = 0xFF00AA00, PANTS = 0xFFAA0000, HEAD = 0xFF333333;

    /** A skin with a solid hat, head, right leg and a trouser layer over the leg's lower half. */
    private static int[] skin() {
        int[] p = new int[S * S];
        fill(p, 32, 0, 32, 16, HAT);
        fill(p, 0, 0, 32, 16, HEAD);
        fill(p, 0, 0, 8, 8, 0); // the unused corners of the head
        fill(p, 24, 0, 8, 8, 0);
        fill(p, 0, 16, 16, 16, LEG);
        fill(p, 0, 40, 16, 8, PANTS); // lower half of the trouser layer
        return p;
    }

    private static void fill(int[] p, int x0, int y0, int w, int h, int c) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                p[y * S + x] = c;
            }
        }
    }

    private static int at(int[] p, int x, int y) {
        return p[y * S + x];
    }

    @Test
    void hobbitGetsHairFeetAndFlattenedTrousers() {
        int[] out = RaceSkinRemap.convert(skin(), new RaceSkinRemap.Rules(12, true, false, true, 2));
        assertEquals(HAT, at(out, 10, 34), "hair top from the hat");
        assertEquals(HAT, at(out, 26, 45), "hair back, head height");
        assertEquals(HAT, at(out, 26, 50), "hair hangs down the back");
        assertEquals(0, at(out, 10, 50), "no hair over the face below the chin");
        assertEquals(0, at(out, 40, 8), "vanilla hat cleared");
        assertEquals(PANTS, at(out, 4, 28), "trouser layer painted onto the leg");
        assertEquals(LEG, at(out, 4, 20), "leg kept where the trousers are clear");
        assertEquals(PANTS, at(out, 44, 36), "toes from the bottom of the (trousered) leg");
        assertEquals(PANTS, at(out, 44, 33), "foot top");
    }

    @Test
    void elfEarsComeFromTheHeadSide() {
        int[] out = RaceSkinRemap.convert(skin(), new RaceSkinRemap.Rules(12, false, true, false, 2));
        assertEquals(HEAD, at(out, 1, 3), "ear filled");
        assertEquals(0, at(out, 26, 50), "hair doesn't hang with hang off");
        assertEquals(0, at(out, 44, 36), "no feet for elves");
    }

    @Test
    void noHairKeepsTheVanillaHat() {
        int[] out = RaceSkinRemap.convert(skin(), new RaceSkinRemap.Rules(0, false, false, true, 2));
        assertEquals(HAT, at(out, 40, 8));
    }
}
