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
 *     ({@link Rules#earY});</li>
 * <li>goblin ears and nose ({@link NpcModels} layout, under y 32) are painted in the face's own skin colour (the face
 *     pixel of median brightness, so eyes and mouth don't count), the ears with a darker hollow, the nose shaded; the
 *     vanilla hat stays (goblins have no hair box);</li>
 * <li>orcs and Uruks ({@link Orcish}, 2026-11-26, Aaron: the player still looks like themselves, but orcish): every
 *     pixel coloured like the face's skin (same hue and saturation within {@link Orcish#tolerance}, half to 1.7 times
 *     as bright) takes the orc skin tone at its own relative brightness, so shading and features stay; everything
 *     else (clothes, hair) is made grimier; eye whites glow in the orc eye colour, lower tusks stand at the corners of
 *     the mouth, scars cross the skin, and Uruks can carry the White Hand on their face.</li>
 * </ul>
 * Pixels are 64 x 64 ints, row by row, in NativeImage's order: alpha in the top byte, then blue, green, red in the
 * low byte (ABGR). Only the orc colouring reads the colour bytes by name; the rest works on either order. Pure Java
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
     * @param goblin paint goblin ears and nose
     * @param orcish orc colouring, or null
     */
    public record Rules(int hairRows, boolean feet, boolean ears, boolean hang, int earY, boolean goblin,
                        Orcish orcish) {}

    /**
     * How a skin is made orcish (from {@code race_skin/<kind>.json}, block {@code orcish}).
     *
     * @param hue skin tone hue (0-1)
     * @param saturation skin tone saturation
     * @param value skin tone brightness at the face's own brightness
     * @param eyes eye colour for eye whites (ABGR), 0 = keep
     * @param tusk tusk colour (ABGR), 0 = none
     * @param hand White Hand colour (ABGR), 0 = none
     * @param grime how much greyer and darker clothes and hair get (0-1)
     * @param tolerance how far from the face's hue and saturation a pixel still counts as skin (chromaticity)
     * @param scars how many scars cross the skin
     */
    public record Orcish(float hue, float saturation, float value, int eyes, int tusk, int hand, float grime,
                         float tolerance, int scars) {}

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
        if (rules.goblin()) {
            goblin(out);
        }
        if (rules.orcish() != null) {
            orcish(out, rules.orcish());
        }
        return out;
    }

    /** The orc colouring ({@link Orcish}); the ear corner (0, 0) filled from the head side is coloured too. */
    private static void orcish(int[] out, Orcish o) {
        int ref = faceColour(out);
        if (alpha(ref) < 128) {
            return;
        }
        float refBright = Math.max(1, brightness(ref));
        float refValue = hsv(ref)[2];
        // eye whites: nearly colourless and brighter than the face (a pale face's lit cheek is neither), rows 1-5
        boolean[] eye = new boolean[SIZE * SIZE];
        for (int y = 9; y <= 13; y++) {
            for (int x = 8; x < 16; x++) {
                int p = out[y * SIZE + x];
                float[] hsv = hsv(p);
                eye[y * SIZE + x] = alpha(p) >= 128 && hsv[1] < 0.2f && hsv[2] > Math.max(0.72f, refValue * 1.05f);
            }
        }
        boolean[] skin = new boolean[SIZE * SIZE];
        for (int i = 0; i < SIZE * SIZE; i++) {
            int p = out[i];
            if (alpha(p) < 128) {
                continue;
            }
            float ratio = brightness(p) / refBright;
            if (!eye[i] && chromaDistance(p, ref) <= o.tolerance() && ratio >= 0.5f && ratio <= 1.7f) {
                skin[i] = true;
                float jitter = ((hash(i, ref) & 0xFF) / 255f - 0.5f) * 0.06f;
                out[i] = fromHsv(p, o.hue(), o.saturation(), clamp01(o.value() * ratio + jitter));
            } else {
                float[] hsv = hsv(p);
                out[i] = fromHsv(p, hsv[0], hsv[1] * (1 - o.grime()), hsv[2] * (1 - o.grime() / 2));
            }
        }
        scars(out, skin, o, ref);
        int face = 8 * SIZE + 8;
        if (o.hand() != 0) {
            // the White Hand: the palm over the nose, three fingers up across the brow, the thumb out to the side
            int[][] hand = {{1, 1}, {1, 2}, {1, 3}, {3, 0}, {3, 1}, {3, 2}, {3, 3}, {5, 1}, {5, 2}, {5, 3},
                {1, 4}, {2, 4}, {3, 4}, {4, 4}, {5, 4}, {2, 5}, {3, 5}, {4, 5}, {6, 5}, {7, 4}};
            for (int[] h : hand) {
                out[face + h[1] * SIZE + h[0]] = o.hand();
            }
        }
        if (o.tusk() != 0) {
            out[face + 6 * SIZE + 2] = o.tusk();
            out[face + 6 * SIZE + 5] = o.tusk();
        }
        if (o.eyes() != 0) {
            int pupil = fromHsv(0xFF000000, 0f, 0f, 0.08f);
            for (int i = 0; i < SIZE * SIZE; i++) {
                if (eye[i]) {
                    out[i] = o.eyes();
                    // the iris beside an eye white turns to a dark pupil in a glowing eye
                    for (int n : new int[] {i - 1, i + 1}) {
                        if (!eye[n] && !skin[n] && alpha(out[n]) >= 128 && n / SIZE == i / SIZE
                                && n % SIZE >= 8 && n % SIZE < 16) {
                            out[n] = pupil;
                        }
                    }
                }
            }
        }
    }

    /** Short dark-red diagonal scars on skin, placed from the skin's own colour so a skin always gets the same. */
    private static void scars(int[] out, boolean[] skin, Orcish o, int ref) {
        // where a scar may start: a cheek, the chest, the front of the right arm, the head's right side
        int[][] spots = {{9, 12}, {13, 11}, {21, 21}, {24, 23}, {44, 22}, {2, 10}};
        int scar = fromHsv(0xFF000000, 0.98f, 0.5f, clamp01(o.value() * 0.55f));
        for (int k = 0; k < o.scars(); k++) {
            int[] s = spots[Math.floorMod(hash(k, ref), spots.length)];
            for (int t = 0; t < 3; t++) {
                int i = (s[1] + t) * SIZE + s[0] + t;
                if (skin[i]) {
                    out[i] = scar;
                }
            }
        }
    }

    private static int hash(int a, int b) {
        int h = a * 0x9E3779B1 ^ b * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        return h ^ h >>> 13;
    }

    /** Distance between two colours' chromaticity (each byte over their sum), ignoring brightness. */
    private static float chromaDistance(int a, int b) {
        float sa = Math.max(1, brightness(a));
        float sb = Math.max(1, brightness(b));
        float dr = (a & 0xFF) / sa - (b & 0xFF) / sb;
        float dg = (a >> 8 & 0xFF) / sa - (b >> 8 & 0xFF) / sb;
        return (float) Math.sqrt(dr * dr + dg * dg);
    }

    /** Hue, saturation, value (0-1) of an ABGR pixel. */
    static float[] hsv(int p) {
        float r = (p & 0xFF) / 255f;
        float g = (p >> 8 & 0xFF) / 255f;
        float b = (p >> 16 & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) {
                h = ((g - b) / d) / 6f;
            } else if (max == g) {
                h = ((b - r) / d + 2) / 6f;
            } else {
                h = ((r - g) / d + 4) / 6f;
            }
            h = h < 0 ? h + 1 : h;
        }
        return new float[] {h, max == 0 ? 0 : d / max, max};
    }

    /** An ABGR pixel of the given hue, saturation and value, keeping {@code p}'s alpha. */
    static int fromHsv(int p, float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int sector = (int) hh % 6;
        float f = hh - (int) hh;
        float a = v * (1 - s);
        float b = v * (1 - s * f);
        float c = v * (1 - s * (1 - f));
        float r;
        float g;
        float bl;
        switch (sector) {
            case 0 -> { r = v; g = c; bl = a; }
            case 1 -> { r = b; g = v; bl = a; }
            case 2 -> { r = a; g = v; bl = c; }
            case 3 -> { r = a; g = b; bl = v; }
            case 4 -> { r = c; g = a; bl = v; }
            default -> { r = v; g = a; bl = b; }
        }
        return (p & 0xFF000000) | Math.round(clamp01(bl) * 255) << 16 | Math.round(clamp01(g) * 255) << 8
                | Math.round(clamp01(r) * 255);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /**
     * Goblin ears (base 4 x 4 x 1 at (0, 32), tip 3 x 2 x 1 at (0, 37)) and nose (bridge 2 x 3 x 2 at (16, 32), tip
     * 2 x 1 x 1 at (16, 37)) in the face's skin colour. A box w x h x d at (u, v) has its front face at (u + d, v + d)
     * and its back at (u + 2d + w, v + d).
     */
    private static void goblin(int[] out) {
        int skin = faceColour(out);
        if (alpha(skin) < 128) {
            return; // no face to take a colour from: leave them clear
        }
        int shade = shade(skin, 0.85f);
        fill(out, 0, 32, 10, 5, shade); // ear base, every face
        fill(out, 1, 33, 4, 4, skin); // front (the hollow faces forward)
        fill(out, 2, 34, 2, 2, shade(skin, 0.6f));
        fill(out, 6, 33, 4, 4, skin); // back
        fill(out, 0, 37, 8, 3, shade); // ear tip
        fill(out, 1, 38, 3, 2, skin);
        fill(out, 5, 38, 3, 2, skin);
        fill(out, 16, 32, 8, 5, shade); // nose bridge: sides and top shaded
        fill(out, 18, 34, 2, 3, skin); // front
        fill(out, 16, 37, 6, 2, shade(skin, 0.75f)); // hooked tip
    }

    /** The front face's (8 x 8 at (8, 8)) opaque pixel of median brightness, or 0 if it has none. */
    static int faceColour(int[] img) {
        java.util.List<Integer> px = new java.util.ArrayList<>();
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                int p = img[y * SIZE + x];
                if (alpha(p) >= 128) {
                    px.add(p);
                }
            }
        }
        if (px.isEmpty()) {
            return 0;
        }
        px.sort(java.util.Comparator.comparingInt(RaceSkinRemap::brightness));
        return px.get(px.size() / 2);
    }

    /** Sum of the three colour bytes (the same for ARGB and ABGR). */
    private static int brightness(int p) {
        return (p & 0xFF) + (p >> 8 & 0xFF) + (p >> 16 & 0xFF);
    }

    /** The colour bytes scaled by {@code f}, alpha kept. */
    static int shade(int p, float f) {
        int a = p & 0xFF000000;
        int c0 = Math.round((p & 0xFF) * f);
        int c1 = Math.round((p >> 8 & 0xFF) * f);
        int c2 = Math.round((p >> 16 & 0xFF) * f);
        return a | c2 << 16 | c1 << 8 | c0;
    }

    private static void fill(int[] img, int x0, int y0, int w, int h, int c) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                img[y * SIZE + x] = c;
            }
        }
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
