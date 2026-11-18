package com.porcupinestudios.lotrport.client;

/**
 * Converts a vanilla 64x64 player skin to the layout of LOTR's race bodies ({@link NpcModels}). Those bodies read
 * parts where a player skin keeps its second layer, so:
 * <ul>
 * <li>the trouser, jacket and right-sleeve layers (y 32-48) are painted onto the base layer, which the race body
 *     draws; the rows below y 32 are then cleared (the race body mirrors its right limbs for the left ones);</li>
 * <li>hair (8 x rows x 8 box at (0, 32)) comes from the hat layer; past the head it hangs down the sides and back
 *     where the hat's lowest row is opaque ({@link Rules#hang});</li>
 * <li>hobbit feet (4 x 2 x 3 box at (40, 32)) come from the bottom of the leg;</li>
 * <li>elf ears (1 x 4 x 2 box at (0, 0), unused on a player skin) come from the side of the head
 *     ({@link Rules#earY}).</li>
 * </ul>
 * Pixels are 64 x 64 ints, row by row; only the top byte (alpha) is read, so ARGB and ABGR both work. Pure Java
 * (unit tested) so no client classes are needed to run it.
 */
public final class RaceSkinRemap {
    public static final int SIZE = 64;

    /**
     * What to build for one body.
     *
     * @param hairRows hair box height (0: no hair box, the vanilla hat at (32, 0) is kept)
     * @param feet build hobbit feet
     * @param ears build elf ears
     * @param hang let hair hang below the head where the hat's lowest row is opaque
     * @param earY first row of the head's side face (0-7) the ears are taken from
     */
    public record Rules(int hairRows, boolean feet, boolean ears, boolean hang, int earY) {}

    private RaceSkinRemap() {}

    public static int[] convert(int[] src, Rules rules) {
        if (src.length != SIZE * SIZE) {
            throw new IllegalArgumentException("skin must be 64x64, got " + src.length + " pixels");
        }
        int[] out = src.clone();
        over(out, 0, 32, 0, 16, 16, 16); // right trouser layer -> right leg
        over(out, 16, 32, 16, 16, 24, 16); // jacket -> body
        over(out, 40, 32, 40, 16, 16, 16); // right sleeve -> right arm
        for (int y = 32; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                out[y * SIZE + x] = 0;
            }
        }
        if (rules.hairRows() > 0) {
            hair(src, out, rules);
            clear(out, 32, 0, 32, 16); // the race body has no vanilla hat
        }
        if (rules.feet()) {
            feet(out);
        }
        if (rules.ears()) {
            ears(out, rules.earY());
        }
        return out;
    }

    private static void hair(int[] src, int[] out, Rules rules) {
        copy(src, 40, 0, out, 8, 32, 8, 8); // top
        copy(src, 48, 0, out, 16, 32, 8, 8); // bottom (seen only from below)
        int rows = rules.hairRows();
        // faces left to right: right side, front, left side, back (hat at y 8, hair box at y 40)
        for (int face = 0; face < 4; face++) {
            int sx = 32 + face * 8;
            int dx = face * 8;
            copy(src, sx, 8, out, dx, 40, 8, Math.min(8, rows));
            if (face == 1 || !rules.hang()) {
                continue; // never over the face below the chin
            }
            for (int y = 8; y < rows; y++) {
                for (int x = 0; x < 8; x++) {
                    int last = src[15 * SIZE + sx + x];
                    out[(40 + y) * SIZE + dx + x] = alpha(last) >= 128 ? last : 0;
                }
            }
        }
    }

    /** Leg box (0, 16): bottom at (8, 16), sides at y 20 (right 0, front 4, left 8, back 12), 12 rows. */
    private static void feet(int[] out) {
        for (int y = 0; y < 3; y++) {
            copy(out, 4, 31, out, 43, 32 + y, 4, 1); // top: the leg's lowest front row
        }
        copy(out, 8, 16, out, 47, 32, 4, 3); // sole
        copy(out, 0, 30, out, 40, 35, 3, 2); // right side
        copy(out, 4, 30, out, 43, 35, 4, 2); // toes
        copy(out, 9, 30, out, 47, 35, 3, 2); // left side
        copy(out, 12, 30, out, 50, 35, 4, 2); // heel
    }

    /** Ear box (0, 0), 1 x 4 x 2, fits in (0..8, 0..6); filled from the head's right face (0, 8). */
    private static void ears(int[] out, int earY) {
        int from = 8 + Math.max(0, Math.min(2, earY));
        copy(out, 0, from, out, 0, 0, 8, 6);
    }

    private static void over(int[] img, int sx, int sy, int dx, int dy, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img[(sy + y) * SIZE + sx + x];
                if (alpha(p) >= 128) {
                    img[(dy + y) * SIZE + dx + x] = p;
                }
            }
        }
    }

    private static void copy(int[] src, int sx, int sy, int[] dst, int dx, int dy, int w, int h) {
        for (int y = 0; y < h; y++) {
            System.arraycopy(src, (sy + y) * SIZE + sx, dst, (dy + y) * SIZE + dx, w);
        }
    }

    private static void clear(int[] img, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                img[y * SIZE + x] = 0;
            }
        }
    }

    private static int alpha(int p) {
        return p >>> 24;
    }
}
