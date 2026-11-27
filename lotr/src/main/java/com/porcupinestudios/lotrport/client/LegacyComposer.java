package com.porcupinestudios.lotrport.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.imageio.ImageIO;

/**
 * Builds the port's textures that are made from several of LOTR's (drinks in their vessels, connected-texture sheets,
 * gate frames, dyed hats) from the player's LOTR jar, as the converter's Python made them (same steps, same integer
 * maths as Pillow), so the result is pixel for pixel the file the converter wrote. Plain Java (no game classes):
 * {@code converter/LegacyAssetMap.java} runs this same code to check every recipe against the converter's output
 * before listing it. A recipe is an op and its arguments (jar paths, numbers); see {@link #compose}.
 */
public final class LegacyComposer {
    /** LOTRDrinkIcons: vessel pixels of this colour (any alpha) show the drink. */
    private static final int MASK = 0xFF00FF;
    /** LOTR's connected-texture pieces, and the quarter looks of a sheet (converter/assets.py SHEET_TILES). */
    private static final List<String> PIECES = List.of("top", "bottom", "left", "right", "topLeft", "topRight",
            "bottomLeft", "bottomRight");
    private static final List<String> CORNERS = List.of("topLeft", "topRight", "bottomLeft", "bottomRight");
    private static final List<List<String>> SHEET_TILES = List.of(
            List.of("left", "right", "top", "bottom", "topLeft", "topRight", "bottomLeft", "bottomRight"),
            List.of("top", "bottom"), List.of("left", "right"),
            List.of("topLeftInv", "topRightInv", "bottomLeftInv", "bottomRightInv"), List.of());

    private LegacyComposer() {}

    /** An image as width, height and ARGB pixels (row by row). */
    public record Img(int w, int h, int[] px) {
        Img copy() {
            return new Img(w, h, px.clone());
        }

        static Img blank(int w, int h) {
            return new Img(w, h, new int[w * h]);
        }

        int get(int x, int y) {
            return px[y * w + x];
        }
    }

    /**
     * Runs a recipe. {@code jar} reads a jar entry's bytes (null if absent). Ops:
     * <ul>
     * <li>{@code drink vessel liquid}: the vessel with its mask pixels taken from the liquid (drinks.py models)</li>
     * <li>{@code tint src rrggbb}: every channel times the colour / 255, alpha kept (generate.py tint: dyed hats)</li>
     * <li>{@code connected stem}: stem_base with every edge and corner piece on top (assets.py _connected)</li>
     * <li>{@code sheet stem}: the 5x2 connected sheet from stem_base and its pieces (assets.py sheet)</li>
     * <li>{@code gate_sheet src}: a gate's sheet, its frame cut from its own bars (assets.py gate_sheet)</li>
     * </ul>
     * Returns null if a jar file is missing or the op is unknown.
     */
    public static Img compose(List<String> recipe, Function<String, byte[]> jar) throws IOException {
        String op = recipe.get(0);
        switch (op) {
            case "drink": {
                Img vessel = read(jar, recipe.get(1)), liquid = read(jar, recipe.get(2));
                if (vessel == null || liquid == null || vessel.w != liquid.w || vessel.h != liquid.h) {
                    return null;
                }
                Img out = vessel.copy();
                for (int i = 0; i < out.px.length; i++) {
                    if ((out.px[i] & 0xFFFFFF) == MASK) {
                        out.px[i] = liquid.px[i];
                    }
                }
                return out;
            }
            case "tint": {
                Img src = read(jar, recipe.get(1));
                if (src == null) {
                    return null;
                }
                int t = Integer.parseInt(recipe.get(2), 16);
                int tr = t >> 16 & 255, tg = t >> 8 & 255, tb = t & 255;
                Img out = src.copy();
                for (int i = 0; i < out.px.length; i++) {
                    int c = out.px[i];
                    out.px[i] = (c & 0xFF000000) | ((c >> 16 & 255) * tr / 255) << 16
                            | ((c >> 8 & 255) * tg / 255) << 8 | (c & 255) * tb / 255;
                }
                return out;
            }
            case "connected": {
                Img base = read(jar, recipe.get(1) + "_base.png");
                if (base == null) {
                    return null;
                }
                Img out = base.copy();
                for (Map.Entry<String, Img> e : pieces(jar, recipe.get(1), base).entrySet()) {
                    if (PIECES.contains(e.getKey())) {
                        over(out, e.getValue(), 0, 0);
                    }
                }
                return out;
            }
            case "sheet": {
                Img base = read(jar, recipe.get(1) + "_base.png");
                if (base == null) {
                    return null;
                }
                Map<String, Img> pieces = pieces(jar, recipe.get(1), base);
                return sheet(base, pieces, pieces);
            }
            case "gate_sheet": {
                Img img = read(jar, recipe.get(1));
                return img == null ? null : gateSheet(img);
            }
            default:
                return null;
        }
    }

    /** The jar's pieces for a connected stem, in LOTR's order, only those the base's size. */
    private static Map<String, Img> pieces(Function<String, byte[]> jar, String stem, Img base) throws IOException {
        Map<String, Img> out = new LinkedHashMap<>();
        for (String n : PIECES) {
            put(out, jar, stem, n, base);
        }
        for (String c : CORNERS) {
            put(out, jar, stem, c + "Inv", base);
        }
        return out;
    }

    private static void put(Map<String, Img> out, Function<String, byte[]> jar, String stem, String n, Img base)
            throws IOException {
        Img layer = read(jar, stem + "_" + n + ".png");
        if (layer != null && layer.w == base.w && layer.h == base.h) {
            out.put(n, layer);
        }
    }

    /** Row 0: each quarter look's pieces over the base; row 1: the open pieces over nothing. */
    private static Img sheet(Img base, Map<String, Img> pieces, Map<String, Img> open) {
        int w = base.w, h = base.h;
        Img img = Img.blank(w * 5, h * 2);
        for (int row = 0; row < 2; row++) {
            Map<String, Img> layers = row == 0 ? pieces : open;
            for (int t = 0; t < SHEET_TILES.size(); t++) {
                Img tile = row == 0 ? base.copy() : Img.blank(w, h);
                for (String n : SHEET_TILES.get(t)) {
                    Img layer = layers.get(n);
                    if (layer != null) {
                        over(tile, layer, 0, 0);
                    }
                }
                paste(img, tile, t * w, row * h);
            }
        }
        return img;
    }

    private static Img gateSheet(Img img) {
        int w = img.w, h = img.h;
        boolean[] solidRows = new boolean[h], solidCols = new boolean[w];
        for (int y = 0; y < h; y++) {
            int n = 0;
            for (int x = 0; x < w; x++) {
                n += (img.get(x, y) >>> 24) > 0 ? 1 : 0;
            }
            solidRows[y] = n >= 0.9 * w;
        }
        for (int x = 0; x < w; x++) {
            int n = 0;
            for (int y = 0; y < h; y++) {
                n += (img.get(x, y) >>> 24) > 0 ? 1 : 0;
            }
            solidCols[x] = n >= 0.9 * h;
        }
        int[] rows = thickestRun(solidRows, h), cols = thickestRun(solidCols, w);
        int eh, ev;
        Img frame;
        Map<String, Img> src = new LinkedHashMap<>();
        if (rows != null && cols != null) {
            eh = rows[1] - rows[0];
            ev = cols[1] - cols[0];
            Img horiz = Img.blank(w, h), vert = Img.blank(w, h);
            Img bar = crop(img, 0, rows[0], w, rows[1]);
            over(horiz, bar, 0, 0);
            over(horiz, bar, 0, h - eh);
            bar = crop(img, cols[0], 0, cols[1], h);
            over(vert, bar, 0, 0);
            over(vert, bar, w - ev, 0);
            frame = horiz.copy();
            over(frame, vert, 0, 0);
            src.put("top", horiz);
            src.put("bottom", horiz);
            src.put("left", vert);
            src.put("right", vert);
        } else {
            eh = ev = Math.max(1, Math.round(w / 16f * 3));
            frame = img;
        }
        Map<String, int[]> boxes = new LinkedHashMap<>();
        boxes.put("left", new int[] {0, 0, ev, h});
        boxes.put("right", new int[] {w - ev, 0, w, h});
        boxes.put("top", new int[] {0, 0, w, eh});
        boxes.put("bottom", new int[] {0, h - eh, w, h});
        boxes.put("topLeft", new int[] {0, 0, ev, eh});
        boxes.put("topRight", new int[] {w - ev, 0, w, eh});
        boxes.put("bottomLeft", new int[] {0, h - eh, ev, h});
        boxes.put("bottomRight", new int[] {w - ev, h - eh, w, h});
        Map<String, Img> pieces = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : boxes.entrySet()) {
            int[] b = e.getValue();
            Img layer = Img.blank(w, h);
            paste(layer, crop(src.getOrDefault(e.getKey(), frame), b[0], b[1], b[2], b[3]), b[0], b[1]);
            pieces.put(e.getKey(), layer);
            if (CORNERS.contains(e.getKey())) {
                pieces.put(e.getKey() + "Inv", layer);
            }
        }
        return sheet(img, Map.of(), pieces);
    }

    /** (start, end) of the longest true run, or null if none or over 3/8 of size (assets.py _thickest_run). */
    private static int[] thickestRun(boolean[] solid, int size) {
        int[] best = null;
        int i = 0;
        while (i < solid.length) {
            int j = i;
            while (j < solid.length && solid[j]) {
                j++;
            }
            if (j > i && (best == null || j - i > best[1] - best[0])) {
                best = new int[] {i, j};
            }
            i = Math.max(j, i + 1);
        }
        return best != null && best[1] - best[0] <= size * 3 / 8 ? best : null;
    }

    private static Img crop(Img img, int x0, int y0, int x1, int y1) {
        Img out = Img.blank(x1 - x0, y1 - y0);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                out.px[(y - y0) * out.w + (x - x0)] = img.get(x, y);
            }
        }
        return out;
    }

    /** Pillow's paste without a mask: replaces the pixels. */
    private static void paste(Img dst, Img src, int ox, int oy) {
        for (int y = 0; y < src.h; y++) {
            for (int x = 0; x < src.w; x++) {
                dst.px[(y + oy) * dst.w + x + ox] = src.get(x, y);
            }
        }
    }

    /** Pillow's alpha_composite (src over dst at ox, oy), with its integer rounding (AlphaComposite.c). */
    static void over(Img dst, Img src, int ox, int oy) {
        for (int y = 0; y < src.h; y++) {
            for (int x = 0; x < src.w; x++) {
                int i = (y + oy) * dst.w + x + ox;
                dst.px[i] = blend(dst.px[i], src.get(x, y));
            }
        }
    }

    private static final int PRECISION_BITS = 7;

    private static long div255(long a) {
        return ((a >> 8) + a) >> 8;
    }

    static int blend(int d, int s) {
        int sa = s >>> 24;
        if (sa == 0) {
            return d;
        }
        int da = d >>> 24;
        long blend = (long) da * (255 - sa);
        long outa255 = (long) sa * 255 + blend;
        long coef1 = (long) sa * 255 * 255 * (1 << PRECISION_BITS) / outa255;
        long coef2 = 255L * (1 << PRECISION_BITS) - coef1;
        int out = (int) div255(outa255 + 0x80) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            long t = (s >> shift & 255) * coef1 + (d >> shift & 255) * coef2;
            out |= (int) (div255(t + (0x80L << PRECISION_BITS)) >> PRECISION_BITS) << shift;
        }
        return out;
    }

    /** A jar PNG as RGBA pixels (palette and grey images converted, like Pillow's convert("RGBA")), or null. */
    public static Img read(Function<String, byte[]> jar, String path) throws IOException {
        byte[] bytes = jar.apply(path);
        if (bytes == null) {
            return null;
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) {
            return null;
        }
        int w = img.getWidth(), h = img.getHeight();
        if (img.getColorModel().getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY) {
            // getRGB would convert linear grey to sRGB; Pillow copies the grey value into r, g and b
            var raster = img.getRaster();
            int bands = raster.getNumBands(), bits = img.getColorModel().getComponentSize(0);
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int g = raster.getSample(x, y, 0) >> Math.max(0, bits - 8);
                    int a = bands > 1 ? raster.getSample(x, y, 1) >> Math.max(0, bits - 8) : 255;
                    px[y * w + x] = a << 24 | g << 16 | g << 8 | g;
                }
            }
            return new Img(w, h, px);
        }
        return new Img(w, h, img.getRGB(0, 0, w, h, null, 0, w));
    }

    public static byte[] png(Img img) throws IOException {
        BufferedImage out = new BufferedImage(img.w, img.h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, img.w, img.h, img.px, 0, img.w);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bytes);
        return bytes.toByteArray();
    }
}
