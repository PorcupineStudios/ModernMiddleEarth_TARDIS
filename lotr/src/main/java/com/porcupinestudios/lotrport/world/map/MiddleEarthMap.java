package com.porcupinestudios.lotrport.world.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/**
 * The map of Middle-earth: one biome per map pixel (LOTR's own map image, 128 blocks a pixel), and per biome a ground
 * height and a hilliness in blocks. Heights are read smoothly between pixel centres (optionally box-blurred first), so
 * terrain changes gradually from one biome to the next; positions are wobbled with a cheap value noise so biome
 * borders and slopes don't follow the pixel grid.
 *
 * <p>Files (written by {@code converter/generate.py}): {@code /lotr_port/map/map.json} (settings and the biome table,
 * in pixel-index order) and {@code /lotr_port/map/biomes.bin.gz} (width x height bytes, row by row, each a biome
 * index; 255 = outside the map, read as the ocean biome).
 */
public final class MiddleEarthMap {
    private static volatile MiddleEarthMap instance;

    private final int width;
    private final int height;
    private final int blocksPerPixel;
    private final int originX;
    private final int originZ;
    private final int blur;
    private final int borderWobble;
    private final int oceanIndex;
    private final byte[] pixels;
    private final List<Entry> biomes;
    private final float[] biomeHeights;
    private final float[] biomeVariations;
    private final Rivers rivers;

    /**
     * Small rivers LOTR drew with its river layer rather than on the map: lines where a fixed noise crosses zero, cut
     * down to {@code bed} and given the river biome. They fade out on high ground and stay out of water.
     *
     * @param scale noise scale in blocks (bigger = longer, straighter rivers)
     * @param width noise distance that counts as the river itself
     * @param bank how many river widths the valley sides reach
     * @param bed y of the river bed
     * @param maxHeight ground above this has no rivers
     * @param minHeight ground below this (lakes, sea) has no rivers
     * @param biomeWidth river widths from the middle that get the river biome
     */
    record Rivers(int biome, double scale, double width, double bank, double bed, double maxHeight,
            double minHeight, double biomeWidth, ImprovedNoise noise) {
        static Rivers read(JsonObject o, List<Entry> biomes) {
            if (o == null) {
                return null;
            }
            String id = o.get("biome").getAsString();
            int index = -1;
            for (int i = 0; i < biomes.size(); i++) {
                if (biomes.get(i).id().equals(id)) {
                    index = i;
                }
            }
            if (index < 0) {
                throw new IllegalArgumentException("river biome " + id + " is not in the biome table");
            }
            return new Rivers(index, o.get("scale").getAsDouble(), o.get("width").getAsDouble(),
                    o.get("bank").getAsDouble(), o.get("bed").getAsDouble(), o.get("max_height").getAsDouble(),
                    o.get("min_height").getAsDouble(), o.get("biome_width").getAsDouble(),
                    new ImprovedNoise(RandomSource.create(o.get("seed").getAsLong())));
        }

        /** Distance from the river's middle, in river widths. */
        double distance(double x, double z) {
            return Math.abs(noise.noise(x / scale, 0.37, z / scale)) / width;
        }

        /** How strongly a river can cut here, from the ground height without rivers. */
        double strength(double ground) {
            if (ground < minHeight) {
                return 0;
            }
            return Math.max(0, Math.min(1, (maxHeight - ground) / 25.0)) * Math.min(1, (ground - minHeight) / 3.0);
        }

        /** 1 in the river and its near banks, falling to 0 at the valley edge. */
        double cut(double distance) {
            if (distance <= 0.5) {
                return 1;
            }
            if (distance >= bank) {
                return 0;
            }
            return 1 - smooth((distance - 0.5) / (bank - 0.5));
        }
    }

    /** One biome of the map: its id, ground height (y of the surface) and hilliness (blocks up or down). */
    public record Entry(String id, float height, float variation) {}

    MiddleEarthMap(JsonObject settings, byte[] pixels) {
        this.width = settings.get("width").getAsInt();
        this.height = settings.get("height").getAsInt();
        this.blocksPerPixel = settings.get("blocks_per_pixel").getAsInt();
        this.originX = settings.get("origin_x").getAsInt();
        this.originZ = settings.get("origin_z").getAsInt();
        this.blur = settings.get("blur").getAsInt();
        this.borderWobble = settings.get("border_wobble").getAsInt();
        this.pixels = pixels;
        if (pixels.length != width * height) {
            throw new IllegalArgumentException("map has " + pixels.length + " pixels, expected " + width * height);
        }
        List<Entry> list = new ArrayList<>();
        for (JsonElement e : settings.getAsJsonArray("biomes")) {
            JsonObject o = e.getAsJsonObject();
            list.add(new Entry(o.get("id").getAsString(), o.get("height").getAsFloat(),
                    o.get("variation").getAsFloat()));
        }
        this.biomes = Collections.unmodifiableList(list);
        this.biomeHeights = new float[256];
        this.biomeVariations = new float[256];
        String ocean = settings.get("ocean").getAsString();
        int oceanAt = -1;
        for (int i = 0; i < list.size(); i++) {
            biomeHeights[i] = list.get(i).height();
            biomeVariations[i] = list.get(i).variation();
            if (list.get(i).id().equals(ocean)) {
                oceanAt = i;
            }
        }
        if (oceanAt < 0) {
            throw new IllegalArgumentException("ocean biome " + ocean + " is not in the biome table");
        }
        this.oceanIndex = oceanAt;
        biomeHeights[255] = biomeHeights[oceanAt];
        biomeVariations[255] = biomeVariations[oceanAt];
        this.rivers = Rivers.read(settings.has("rivers") ? settings.getAsJsonObject("rivers") : null, list);
    }

    public static MiddleEarthMap get() {
        MiddleEarthMap map = instance;
        if (map == null) {
            synchronized (MiddleEarthMap.class) {
                map = instance;
                if (map == null) {
                    map = load();
                    instance = map;
                }
            }
        }
        return map;
    }

    private static MiddleEarthMap load() {
        try (InputStream json = open("map.json");
                DataInputStream bin = new DataInputStream(new GZIPInputStream(open("biomes.bin.gz")))) {
            JsonObject settings = JsonParser.parseReader(new InputStreamReader(json, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            byte[] pixels = new byte[settings.get("width").getAsInt() * settings.get("height").getAsInt()];
            bin.readFully(pixels);
            return new MiddleEarthMap(settings, pixels);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the Middle-earth map", e);
        }
    }

    private static InputStream open(String name) throws IOException {
        InputStream in = MiddleEarthMap.class.getResourceAsStream("/lotr_port/map/" + name);
        if (in == null) {
            throw new IOException("missing /lotr_port/map/" + name);
        }
        return in;
    }

    public List<Entry> biomes() {
        return biomes;
    }

    public int blocksPerPixel() {
        return blocksPerPixel;
    }

    /** The biome index at a block position: the map pixel under it, with borders wobbled. */
    public int biomeIndexAt(int blockX, int blockZ) {
        double wx = wobbledX(blockX, blockZ);
        double wz = wobbledZ(blockX, blockZ);
        if (rivers != null && rivers.distance(wx, wz) < rivers.biomeWidth()
                && rivers.strength(smoothed(wx, wz, biomeHeights)) > 0.5) {
            return rivers.biome();
        }
        return pixelIndex((int) Math.floor(wx / blocksPerPixel + originX),
                (int) Math.floor(wz / blocksPerPixel + originZ));
    }

    private double wobbledX(int blockX, int blockZ) {
        return blockX + 0.5 + wobble(blockX, blockZ, 0x5DEECE66DL) * borderWobble;
    }

    private double wobbledZ(int blockX, int blockZ) {
        return blockZ + 0.5 + wobble(blockX, blockZ, 0x2545F4914F6CDD1DL) * borderWobble;
    }

    /**
     * A block position inside each biome, by biome index: the centre of a pixel whose four neighbours are the same
     * biome where there is one, else any pixel of it. Null for a biome that isn't on the map. For tests and tools.
     */
    public int[][] samplePositions() {
        int[][] best = new int[biomes.size()][];
        int[] score = new int[biomes.size()];
        for (int pz = 0; pz < height; pz++) {
            for (int px = 0; px < width; px++) {
                int b = pixels[pz * width + px] & 0xFF;
                if (b >= biomes.size() || score[b] == 25) {
                    continue;
                }
                int same = 0; // pixels of the same biome in the 5x5 around this one: deep inside is best
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (pixelIndex(px + dx, pz + dz) == b) {
                            same++;
                        }
                    }
                }
                if (best[b] == null || same > score[b]) {
                    best[b] = new int[] { (px - originX) * blocksPerPixel + blocksPerPixel / 2,
                            (pz - originZ) * blocksPerPixel + blocksPerPixel / 2 };
                    score[b] = same;
                }
            }
        }
        return best;
    }

    /** The biome index of a map pixel; outside the map is ocean. */
    public int pixelIndex(int px, int pz) {
        if (px < 0 || pz < 0 || px >= width || pz >= height) {
            return oceanIndex;
        }
        int v = pixels[pz * width + px] & 0xFF;
        return v == 255 ? oceanIndex : v;
    }

    /** Ground height (surface y) at a block position, smoothed across biome borders. */
    public double heightAt(int blockX, int blockZ) {
        double wx = wobbledX(blockX, blockZ);
        double wz = wobbledZ(blockX, blockZ);
        double ground = smoothed(wx, wz, biomeHeights);
        double cut = riverCut(wx, wz, ground);
        return cut == 0 ? ground : ground - (ground - rivers.bed()) * cut;
    }

    /** Hilliness (how far the ground rises and falls, in blocks) at a block position, smoothed like the height. */
    public double variationAt(int blockX, int blockZ) {
        double wx = wobbledX(blockX, blockZ);
        double wz = wobbledZ(blockX, blockZ);
        double variation = smoothed(wx, wz, biomeVariations);
        double cut = riverCut(wx, wz, smoothed(wx, wz, biomeHeights));
        return variation * (1 - cut);
    }

    private double riverCut(double wx, double wz, double ground) {
        if (rivers == null) {
            return 0;
        }
        double strength = rivers.strength(ground);
        return strength == 0 ? 0 : strength * rivers.cut(rivers.distance(wx, wz));
    }

    /**
     * Read between the four nearest pixel centres with smoothstep weights (no creases along the pixel grid), at the
     * same wobbled position the biome lookup uses, so a river's water lines up with the river biome.
     */
    private double smoothed(double wx, double wz, float[] table) {
        double fx = wx / blocksPerPixel + originX - 0.5;
        double fz = wz / blocksPerPixel + originZ - 0.5;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = smooth(fx - x0);
        double tz = smooth(fz - z0);
        double a = blurred(x0, z0, table);
        double b = blurred(x0 + 1, z0, table);
        double c = blurred(x0, z0 + 1, table);
        double d = blurred(x0 + 1, z0 + 1, table);
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
    }

    private double blurred(int px, int pz, float[] table) {
        if (blur == 0) {
            return table[pixelIndex(px, pz)];
        }
        double sum = 0;
        for (int dz = -blur; dz <= blur; dz++) {
            for (int dx = -blur; dx <= blur; dx++) {
                sum += table[pixelIndex(px + dx, pz + dz)];
            }
        }
        int side = blur * 2 + 1;
        return sum / (side * side);
    }

    /** Smooth value noise in [-1, 1]: a 64-block and a 16-block octave. */
    private static double wobble(int x, int z, long salt) {
        return valueNoise(x / 64.0, z / 64.0, salt) * 0.7 + valueNoise(x / 16.0, z / 16.0, salt * 31) * 0.3;
    }

    private static double valueNoise(double x, double z, long salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = smooth(x - x0);
        double tz = smooth(z - z0);
        double a = hash(x0, z0, salt);
        double b = hash(x0 + 1, z0, salt);
        double c = hash(x0, z0 + 1, salt);
        double d = hash(x0 + 1, z0 + 1, salt);
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double hash(int x, int z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    /** Test hook: a map built from a JSON settings object and pixels, without the bundled files. */
    public static MiddleEarthMap of(JsonObject settings, byte[] pixels) {
        return new MiddleEarthMap(settings, pixels);
    }
}
