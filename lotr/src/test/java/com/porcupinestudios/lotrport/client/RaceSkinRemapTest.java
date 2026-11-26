package com.porcupinestudios.lotrport.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RaceSkinRemapTest {
    private static final int S = RaceSkinRemap.SIZE;
    private static final int HAT = 0xFF0000AA, LEG = 0xFF00AA00, PANTS = 0xFFAA0000, HEAD = 0xFF333333;
    private static final int FACE = 0xFF8899AA, EYE = 0xFF101010;

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
        int[] out = RaceSkinRemap.convert(skin(), new RaceSkinRemap.Rules(12, true, false, true, 2, false, null));
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
        int[] out = RaceSkinRemap.convert(skin(), new RaceSkinRemap.Rules(12, false, true, false, 2, false, null));
        assertEquals(HEAD, at(out, 1, 3), "ear filled");
        assertEquals(0, at(out, 26, 50), "hair doesn't hang with hang off");
        assertEquals(0, at(out, 44, 36), "no feet for elves");
    }

    @Test
    void goblinGetsEarsAndNoseInTheFacesColour() {
        int[] p = skin();
        fill(p, 8, 8, 8, 8, FACE);
        fill(p, 9, 12, 2, 1, EYE); // eyes and mouth are darker and don't set the colour
        fill(p, 13, 12, 2, 1, EYE);
        fill(p, 10, 14, 4, 1, EYE);
        int[] out = RaceSkinRemap.convert(p, new RaceSkinRemap.Rules(0, false, false, true, 2, true, null));
        assertEquals(FACE, at(out, 2, 33), "ear front in the face colour");
        assertEquals(FACE, at(out, 7, 34), "ear back in the face colour");
        assertEquals(RaceSkinRemap.shade(FACE, 0.6f), at(out, 2, 34), "darker hollow");
        assertEquals(FACE, at(out, 2, 38), "ear tip");
        assertEquals(FACE, at(out, 18, 35), "nose front");
        assertEquals(RaceSkinRemap.shade(FACE, 0.75f), at(out, 17, 37), "nose tip");
        assertEquals(HAT, at(out, 40, 8), "goblins keep the vanilla hat");
        assertEquals(0, at(out, 30, 40), "nothing else under y 32");
    }

    @Test
    void goblinWithoutAFaceGetsNoEars() {
        int[] p = skin();
        fill(p, 8, 8, 8, 8, 0);
        int[] out = RaceSkinRemap.convert(p, new RaceSkinRemap.Rules(0, false, false, true, 2, true, null));
        assertEquals(0, at(out, 2, 33));
    }

    /** An opaque ABGR pixel (NativeImage's order) from r, g, b. */
    private static int abgr(int r, int g, int b) {
        return 0xFF000000 | b << 16 | g << 8 | r;
    }

    private static final int TUSK = abgr(230, 220, 190), EYES = abgr(230, 40, 20), HAND = abgr(235, 230, 220);
    private static final RaceSkinRemap.Orcish ORC = new RaceSkinRemap.Orcish(0.27f, 0.2f, 0.5f, EYES, TUSK, 0, 0.3f,
            0.06f, 0);

    /** A human player: tan head with eye whites, a lighter cheek, a blue shirt. */
    private static int[] human() {
        int[] p = skin();
        fill(p, 0, 8, 32, 8, abgr(200, 150, 110)); // face and head sides
        fill(p, 9, 12, 2, 1, abgr(250, 250, 250)); // eye whites
        fill(p, 13, 12, 2, 1, abgr(250, 250, 250));
        p[13 * S + 9] = abgr(230, 173, 127); // a lit cheek: same hue, brighter
        fill(p, 16, 16, 24, 16, abgr(40, 120, 200)); // shirt
        return p;
    }

    @Test
    void orcsTakeTheOrcSkinToneButKeepTheirShading() {
        int[] out = RaceSkinRemap.convert(human(), new RaceSkinRemap.Rules(0, false, false, false, 2, false, ORC));
        float[] face = RaceSkinRemap.hsv(at(out, 11, 9));
        assertEquals(27, Math.round(face[0] * 100), "face hue is the orc tone");
        assertTrue(Math.abs(face[2] - 0.5f) < 0.05f, "face at the orc brightness: " + face[2]);
        float cheek = RaceSkinRemap.hsv(at(out, 9, 13))[2];
        assertTrue(cheek > face[2] + 0.05f, "the lit cheek stays lighter: " + cheek + " vs " + face[2]);
        assertEquals(EYES, at(out, 9, 12), "eye whites glow");
        assertTrue(RaceSkinRemap.hsv(at(out, 9, 13))[0] > 0.2f, "a pale lit cheek is skin, not an eye");
        assertEquals(TUSK, at(out, 10, 14), "a tusk at the left corner of the mouth");
        assertEquals(TUSK, at(out, 13, 14), "and the right");
        float[] shirt = RaceSkinRemap.hsv(at(out, 22, 24));
        float[] was = RaceSkinRemap.hsv(abgr(40, 120, 200));
        assertEquals(Math.round(was[0] * 100), Math.round(shirt[0] * 100), "the shirt keeps its hue");
        assertTrue(shirt[1] < was[1] && shirt[2] < was[2], "but is grimier");
    }

    @Test
    void uruksCarryTheWhiteHand() {
        RaceSkinRemap.Orcish uruk = new RaceSkinRemap.Orcish(0.045f, 0.33f, 0.24f, EYES, TUSK, HAND, 0.3f, 0.06f, 2);
        int[] out = RaceSkinRemap.convert(human(), new RaceSkinRemap.Rules(0, false, true, false, 2, false, uruk));
        assertEquals(HAND, at(out, 11, 13), "the palm over the nose");
        assertEquals(HAND, at(out, 11, 9), "a finger up the brow");
        assertEquals(EYES, at(out, 13, 12), "the eyes show through the hand");
        float[] ear = RaceSkinRemap.hsv(at(out, 1, 3));
        assertEquals(4, Math.round(ear[0] * 100), "the ear, taken from the head side, has the Uruk tone");
    }

    @Test
    void noHairKeepsTheVanillaHat() {
        int[] out = RaceSkinRemap.convert(skin(), new RaceSkinRemap.Rules(0, false, false, true, 2, false, null));
        assertEquals(HAT, at(out, 40, 8));
    }
}
