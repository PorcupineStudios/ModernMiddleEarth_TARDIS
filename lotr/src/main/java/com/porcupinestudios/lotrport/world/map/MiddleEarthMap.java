package com.porcupinestudios.lotrport.world.map;

import net.minecraft.core.BlockPos;
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
    /** The world seed the mountain ranges use (set as a server starts, before terrain is made). */
    private static volatile long seed;

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
    /** Per biome: the tallest of its "downs" (round hills, {@link #downs}), in blocks; 0 for none. */
    private final float[] biomeDowns;
    private final Rivers rivers;
    private final Variants variants;
    /** Per biome with variants: a table that is 1 for it, smoothed like heights to fade its variants' terrain out. */
    private final java.util.Map<Integer, float[]> variantMasks = new java.util.LinkedHashMap<>();

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
     * @param maskScale scale (blocks) of a second noise where rivers run at all (0: everywhere); where it is low they
     *     fade out to dry valleys, so whole stretches of the network are missing (Aaron, 2026-11-14: fewer rivers)
     * @param keep biome indexes whose rivers keep the land's own biome (Mordor, deserts): no green river banks there
     */
    record Rivers(int biome, double scale, double width, double bank, double bed, double maxHeight,
            double minHeight, double biomeWidth, boolean[] keep, ImprovedNoise noise, double maskScale,
            ImprovedNoise mask) {
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
            boolean[] keep = new boolean[256];
            if (o.has("keep_biome")) {
                for (JsonElement k : o.getAsJsonArray("keep_biome")) {
                    for (int i = 0; i < biomes.size(); i++) {
                        if (biomes.get(i).id().equals(k.getAsString())) {
                            keep[i] = true;
                        }
                    }
                }
            }
            return new Rivers(index, o.get("scale").getAsDouble(), o.get("width").getAsDouble(),
                    o.get("bank").getAsDouble(), o.get("bed").getAsDouble(), o.get("max_height").getAsDouble(),
                    o.get("min_height").getAsDouble(), o.get("biome_width").getAsDouble(), keep,
                    new ImprovedNoise(RandomSource.create(o.get("seed").getAsLong())),
                    o.has("mask_scale") ? o.get("mask_scale").getAsDouble() : 0,
                    new ImprovedNoise(RandomSource.create(o.get("seed").getAsLong() + 1)));
        }

        /** Distance from the river's middle, in river widths. */
        double distance(double x, double z) {
            return Math.abs(noise.noise(x / scale, 0.37, z / scale)) / width;
        }

        /** How strongly a river can cut, from the ground height without rivers. */
        double strength(double ground) {
            if (ground < minHeight) {
                return 0;
            }
            return Math.max(0, Math.min(1, (maxHeight - ground) / 25.0)) * Math.min(1, (ground - minHeight) / 3.0);
        }

        /**
         * Distance from the river's middle in river widths, narrowed by the mask: where the mask fades, the river
         * gets narrower (not shallower) and ends, about a third of the network (2026-11-14; fading the depth left
         * short slits of water).
         */
        double reach(double x, double z) {
            double d = distance(x, z);
            if (maskScale <= 0) {
                return d;
            }
            double m = smooth(Math.max(0, Math.min(1, (mask.noise(x / maskScale, 0.71, z / maskScale) + 0.12) / 0.18)));
            return m < 0.02 ? Double.MAX_VALUE : d / m;
        }

        /**
         * The ground a river leaves at distance {@code d} (river widths) from its middle: its bed in the channel
         * (inside half a width), rising to one block over sea level ({@code minHeight}) at a width, then up its banks
         * to the land at {@code bank} widths. Only the channel holds water: before 2026-11-14 the whole valley was cut
         * towards the bed, and on low ground that flooded 60-80 blocks wide (lakes, not rivers).
         */
        double carve(double ground, double d) {
            double target;
            if (d <= 0.5) {
                target = bed;
            } else if (d <= 1) {
                target = bed + (minHeight - bed) * smooth((d - 0.5) / 0.5);
            } else if (d < bank) {
                target = minHeight + (ground - minHeight) * smooth((d - 1) / (bank - 1));
            } else {
                return ground;
            }
            return Math.min(ground, target);
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

    /**
     * One biome of the map: its id, ground height (y of the surface), hilliness (blocks up or down, from noise) and
     * downs (the tallest of its round hills, {@link #downs}; 0 for none).
     */
    public record Entry(String id, float height, float variation, float downs) {}

    /**
     * LOTRMountains: the ground rises in a cone to {@code peak} (a y level) at x, z over {@code range} blocks; hills
     * flatten by 1 + LOTR's height {@code boost} there. A volcano (Mount Doom) has a flat crater floor at {@code floor}
     * within {@code crater} blocks, filled with lava up to {@code lava} within crater + 6 (LOTR's getLavaHeight).
     */
    public record Mountain(int x, int z, int range, int peak, double boost, int crater, int floor, int lava) {
        double distance(int blockX, int blockZ) {
            return Math.sqrt((double) (blockX - x) * (blockX - x) + (double) (blockZ - z) * (blockZ - z));
        }
    }

    private final List<Mountain> mountains = new ArrayList<>();
    /** The noise band (NoiseTop): above, below, hill bias, sea level, top of the world; null if the map has none. */
    final double[] noiseBand;
    /** The mountain ranges' massifs (Misty Mountains...); null if the map has none. */
    private final Ranges ranges;
    /** Per biome: its height scale if it is a range, else 0. */
    private final float[] rangeScale = new float[256];
    /** Per biome: 1 for a range (its noise hilliness is replaced by the massif), else 0. */
    private final float[] rangeShare = new float[256];

    /**
     * How LOTRMountains peaks are roughed up so they aren't round cones: the distance to the peak is stretched by a
     * low noise ({@code warp}, share of the distance) so the foot spreads into lobes and spurs, ridged noise
     * ({@code ridges}, share of the peak's rise, at {@code scale} blocks) cuts crests and gullies down the flanks, and
     * hills flatten by only {@code flatten} x LOTR's boost. All zero gives the old smooth cone.
     */
    record MountainShape(double warp, double ridges, double scale, double flatten, ImprovedNoise noise) {
        static MountainShape read(JsonObject o) {
            if (o == null) {
                return new MountainShape(0, 0, 1, 1, new ImprovedNoise(RandomSource.create(0)));
            }
            return new MountainShape(o.get("warp").getAsDouble(), o.get("ridges").getAsDouble(),
                    o.get("scale").getAsDouble(), o.get("flatten").getAsDouble(),
                    new ImprovedNoise(RandomSource.create(o.get("seed").getAsLong())));
        }

        /** The distance to the peak, stretched by the warp noise. */
        double distance(Mountain m, int x, int z) {
            double d = m.distance(x, z);
            if (warp == 0) {
                return d;
            }
            double n = noise.noise(x / (scale * 3), 11.3, z / (scale * 3)) + 0.5 * noise.noise(x / scale, 17.9,
                    z / scale);
            return d * (1 + warp * n);
        }

        /** Ridged noise around 0 (sharp crests up, broad gullies down), two octaves. */
        double ridged(int x, int z) {
            double a = 1 - Math.abs(noise.noise(x / scale, 3.7, z / scale)) * 2;
            double b = 1 - Math.abs(noise.noise(x / (scale / 2.3), 5.1, z / (scale / 2.3))) * 2;
            return (a * 0.65 + b * 0.35) - 0.3;
        }
    }

    private final MountainShape mountainShape;

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
                    o.get("variation").getAsFloat(), o.has("downs") ? o.get("downs").getAsFloat() : 0));
        }
        this.biomes = Collections.unmodifiableList(list);
        this.biomeHeights = new float[256];
        this.biomeVariations = new float[256];
        this.biomeDowns = new float[256];
        String ocean = settings.get("ocean").getAsString();
        int oceanAt = -1;
        for (int i = 0; i < list.size(); i++) {
            biomeHeights[i] = list.get(i).height();
            biomeVariations[i] = list.get(i).variation();
            biomeDowns[i] = list.get(i).downs();
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
        if (settings.has("mountains")) {
            for (JsonElement e : settings.getAsJsonArray("mountains")) {
                JsonObject o = e.getAsJsonObject();
                mountains.add(new Mountain(o.get("x").getAsInt(), o.get("z").getAsInt(), o.get("range").getAsInt(),
                        o.get("peak").getAsInt(), o.has("boost") ? o.get("boost").getAsDouble() : 0,
                        o.has("crater") ? o.get("crater").getAsInt() : 0, o.has("floor") ? o.get("floor").getAsInt() : 0,
                        o.has("lava") ? o.get("lava").getAsInt() : 0));
            }
        }
        JsonObject band = settings.has("noise_band") ? settings.getAsJsonObject("noise_band") : null;
        this.noiseBand = band == null ? null : new double[] {band.get("above").getAsDouble(),
                band.get("below").getAsDouble(), band.get("hill_bias").getAsDouble(),
                band.get("sea_level").getAsDouble(), band.get("top").getAsDouble()};
        this.ranges = settings.has("ranges") ? new Ranges(settings.getAsJsonObject("ranges"), list, pixels, width,
                height, blocksPerPixel, originX, originZ) : null;
        for (int i = 0; i < list.size(); i++) {
            if (ranges != null && ranges.isRange(i)) {
                rangeScale[i] = ranges.scale(i);
                rangeShare[i] = 1;
            }
        }
        this.mountainShape = MountainShape.read(
                settings.has("mountain_shape") ? settings.getAsJsonObject("mountain_shape") : null);
        this.rivers = Rivers.read(settings.has("rivers") ? settings.getAsJsonObject("rivers") : null, list);
        this.variants = new Variants(settings.has("variants") ? settings.getAsJsonObject("variants") : null, list);
        for (int i = 0; i < list.size(); i++) {
            if (variants.has(i)) {
                float[] mask = new float[256];
                mask[i] = 1;
                variantMasks.put(i, mask);
            }
        }
    }

    /**
     * The world seed for the seeded parts of the terrain (the mountain ranges; Aaron 2026-11-15: they "should change
     * depending on seed"). Called as a server starts, before its levels make any terrain.
     */
    /** The noise band: above, below, hill bias, sea level, top of the world (map.json noise_band); null if none. */
    public double[] noiseBand() {
        return noiseBand == null ? null : noiseBand.clone();
    }

    /** LOTR's single peaks (Erebor, the Lonely Mountain, Mount Doom...), from map.json {@code mountains}. */
    public List<Mountain> mountains() {
        return java.util.Collections.unmodifiableList(mountains);
    }

    public static void setSeed(long worldSeed) {
        seed = worldSeed;
        MiddleEarthMap map = instance;
        if (map != null && map.ranges != null) {
            map.ranges.seed(worldSeed);
        }
    }

    public static MiddleEarthMap get() {
        MiddleEarthMap map = instance;
        if (map == null) {
            synchronized (MiddleEarthMap.class) {
                map = instance;
                if (map == null) {
                    map = load();
                    if (map.ranges != null) {
                        map.ranges.seed(seed);
                    }
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

    /** The most hilliness any biome's noise gets (the ranges' is replaced by their massif). */
    public double mostHilliness() {
        double most = 0;
        for (int i = 0; i < biomes.size(); i++) {
            if (rangeShare[i] == 0) {
                most = Math.max(most, biomes.get(i).variation());
            }
        }
        return most;
    }

    public List<Entry> biomes() {
        return biomes;
    }

    /** The block at a map pixel position (pixel centres at .5). */
    public BlockPos blockOfPixel(double px, double pz) {
        return new BlockPos((int) Math.floor((px - originX) * blocksPerPixel), 0,
                (int) Math.floor((pz - originZ) * blocksPerPixel));
    }

    /** The map pixel x (fractional) under a block x: the inverse of {@link #blockOfPixel}. */
    public double pixelX(double blockX) {
        return blockX / blocksPerPixel + originX;
    }

    /** The map pixel y (fractional) under a block z. */
    public double pixelZ(double blockZ) {
        return blockZ / blocksPerPixel + originZ;
    }

    /** Map size in pixels. */
    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int blocksPerPixel() {
        return blocksPerPixel;
    }

    /** The biome index of rivers (the river layer and painted rivers), or -1 without one. */
    public int riverBiome() {
        return rivers == null ? -1 : rivers.biome();
    }

    /** The biome index at a block position: the map pixel under it, with borders wobbled. */
    public int biomeIndexAt(int blockX, int blockZ) {
        double wx = wobbledX(blockX, blockZ);
        double wz = wobbledZ(blockX, blockZ);
        int land = pixelIndex((int) Math.floor(wx / blocksPerPixel + originX),
                (int) Math.floor(wz / blocksPerPixel + originZ));
        if (rivers != null && !rivers.keep()[land] && rivers.reach(wx, wz) < rivers.biomeWidth()
                && rivers.strength(smoothed(wx, wz, biomeHeights)) > 0.5) {
            return rivers.biome();
        }
        return land;
    }

    private double wobbledX(int blockX, int blockZ) {
        return blockX + 0.5 + wobble(blockX, blockZ, 0x5DEECE66DL) * borderWobble;
    }

    private double wobbledZ(int blockX, int blockZ) {
        return blockZ + 0.5 + wobble(blockX, blockZ, 0x2545F4914F6CDD1DL) * borderWobble;
    }

    /**
     * The biome variant (LOTR's variant patches, {@link Variants}) at a column: its name, or null in the plain biome,
     * a river, or a biome without variants. Plants follow it ({@code lotr_port:in_variant}).
     */
    public String variantAt(int blockX, int blockZ) {
        Variants.Variant v = variants.at(biomeIndexAt(blockX, blockZ), blockX, blockZ);
        return v == null ? null : v.name();
    }

    /** How the variant patches scale downs or hilliness here, faded in over their biome's smoothed pixels. */
    private double variantFactor(double wx, double wz, int blockX, int blockZ, boolean downs) {
        double f = 1;
        for (var e : variantMasks.entrySet()) {
            double share = smoothed(wx, wz, e.getValue());
            if (share > 0) {
                double v = downs ? variants.downsFactor(e.getKey(), blockX, blockZ)
                        : variants.variationFactor(e.getKey(), blockX, blockZ);
                f += share * (v - 1);
            }
        }
        return f;
    }

    /** The block centres of up to {@code limit} map pixels of a biome, in map order. For tests and tools. */
    public List<int[]> pixelCentres(int biome, int limit) {
        List<int[]> out = new ArrayList<>();
        for (int pz = 0; pz < height && out.size() < limit; pz++) {
            for (int px = 0; px < width && out.size() < limit; px++) {
                if ((pixels[pz * width + px] & 0xFF) == biome) {
                    out.add(new int[] { (px - originX) * blocksPerPixel + blocksPerPixel / 2,
                            (pz - originZ) * blocksPerPixel + blocksPerPixel / 2 });
                }
            }
        }
        return out;
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
        double downs = smoothed(wx, wz, biomeDowns) * variantFactor(wx, wz, blockX, blockZ, true);
        if (downs > 0) {
            // flattened near roads, as LOTR flattened its hills there
            double road = Math.min(1, Roads.get().distance(blockX, blockZ) / Roads.FLATTEN);
            ground += downs * downs(blockX, blockZ) * (0.3 + 0.7 * road);
        }
        if (ranges != null) {
            double k = smoothed(wx, wz, rangeScale);
            if (k > 0) {
                ground = ranges.ceiling(ground + ranges.massif(blockX, blockZ, k));
            }
        }
        for (Mountain m : mountains) {
            double d = m.distance(blockX, blockZ);
            if (d < m.crater) {
                ground = Math.max(ground, m.floor);
                continue;
            }
            if (m.crater > 0) { // volcanoes keep their clean cone and crater rim
                if (d < m.range && m.peak > ground) {
                    ground += (m.peak - ground) * (1 - d / m.range);
                }
                continue;
            }
            double wd = mountainShape.distance(m, blockX, blockZ);
            if (wd < m.range && m.peak > ground) {
                double t = 1 - wd / m.range;
                double rise = m.peak - ground;
                // concave flanks (steeper near the top), crests and gullies strongest mid-slope
                ground += rise * (t * t * (1.6 - 0.6 * t))
                        + rise * mountainShape.ridges() * 4 * t * (1 - t) * mountainShape.ridged(blockX, blockZ);
            }
        }
        if (rivers == null) {
            return ground;
        }
        double strength = rivers.strength(ground);
        return strength == 0 ? ground : ground - strength * (ground - rivers.carve(ground, rivers.reach(wx, wz)));
    }

    /** Hilliness (how far the ground rises and falls, in blocks) at a block position, smoothed like the height. */
    public double variationAt(int blockX, int blockZ) {
        double wx = wobbledX(blockX, blockZ);
        double wz = wobbledZ(blockX, blockZ);
        double variation = smoothed(wx, wz, biomeVariations) * variantFactor(wx, wz, blockX, blockZ, false);
        double cut = riverCut(wx, wz, smoothed(wx, wz, biomeHeights));
        // LOTR flattens the land within 32 blocks of a road (LOTRChunkProvider roadNear)
        double road = Math.min(1, Roads.get().distance(blockX, blockZ) / Roads.FLATTEN);
        double boost = 0; // LOTR: a mountain's hills flatten by 1 + its height boost (LOTRChunkProvider)
        for (Mountain m : mountains) {
            double d = m.distance(blockX, blockZ);
            if (d < m.range) {
                boost += m.boost * (1 - d / m.range) * (m.crater > 0 ? 1 : mountainShape.flatten());
            }
        }
        // a range's own massif replaces its noise hilliness: noise's 3D part overhung and floated rock (2026-11-15)
        double share = ranges == null ? 0 : smoothed(wx, wz, rangeShare);
        return variation * (1 - share) * (1 - cut) * (0.25 + 0.75 * road) / (1 + boost);
    }

    /** Grid spacing of the downs (blocks): about one hill per cell. */
    static final int DOWNS_CELL = 112;

    /**
     * The Shire's own hills (Aaron, 2026-11-14): round "downs" that only rise from the land, 0 on the flat to about 1
     * on a hilltop. One hill per {@link #DOWNS_CELL} grid cell, its middle jittered within the cell, each its own size
     * (radius 0.5-0.8 of a cell), height (0.55-1) and oval, turned shape (up to 1.6 times as long as wide); one cell
     * in five is left flat for meadows. A hill's profile is (height x (1 - r^2)^2), a bell with a gentle foot; where
     * hills overlap they are blended as the root of the sum of squares, so they merge into ridges without plateaus or
     * creases. At 30 blocks tall (the Shire's) the steepest slope is about 0.7 blocks per block: steep, round hills
     * whose every step is a grass edge (Aaron 2026-11-14). Unlike noise hills it never dips
     * under the biome's ground, so it makes no ponds, and it needs no seed: the same hills in every world, and reliable.
     * Tuned on a shaded preview of 1.5 km (2026-11-14).
     */
    static double downs(int x, int z) {
        int cx = Math.floorDiv(x, DOWNS_CELL);
        int cz = Math.floorDiv(z, DOWNS_CELL);
        double sum = 0;
        // 5 x 5 cells: a long hill reaches up to 1.44 cells from its middle, which can be in the next cell but one
        for (int i = -2; i <= 2; i++) {
            for (int k = -2; k <= 2; k++) {
                long h = mix((cx + i) * 0x9E3779B97F4A7C15L ^ (cz + k) * 0xC2B2AE3D27D4EB4FL ^ 0x5348495245L);
                if (unit(h, 0) < 0.2) {
                    continue; // a meadow
                }
                double hx = (cx + i + 0.15 + 0.7 * unit(h, 1)) * DOWNS_CELL;
                double hz = (cz + k + 0.15 + 0.7 * unit(h, 2)) * DOWNS_CELL;
                double radius = DOWNS_CELL * (0.5 + 0.3 * unit(h, 3));
                double height = 0.55 + 0.45 * unit(h, 4);
                double turn = Math.PI * unit(h, 5);
                double stretch = 1 + 0.6 * unit(h, 6);
                double dx = x - hx;
                double dz = z - hz;
                double along = (dx * Math.cos(turn) + dz * Math.sin(turn)) / (radius * stretch);
                double across = (-dx * Math.sin(turn) + dz * Math.cos(turn)) / radius;
                double r2 = along * along + across * across;
                if (r2 < 1) {
                    double u = 1 - r2;
                    double hill = height * u * u;
                    sum += hill * hill;
                }
            }
        }
        return Math.sqrt(sum);
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }

    /** The {@code n}th number in [0, 1) drawn from hash {@code h}. */
    private static double unit(long h, int n) {
        return (mix(h + n * 0x632BE59BD9B4E019L) >>> 11) * 0x1.0p-53;
    }

    /** The lava level of a volcano's crater at a block column (LOTR's getLavaHeight), or 0. */
    public int lavaAt(int blockX, int blockZ) {
        for (Mountain m : mountains) {
            if (m.lava > 0 && m.distance(blockX, blockZ) < m.crater + 6) {
                return m.lava;
            }
        }
        return 0;
    }

    private double riverCut(double wx, double wz, double ground) {
        if (rivers == null) {
            return 0;
        }
        double strength = rivers.strength(ground);
        return strength == 0 ? 0 : strength * rivers.cut(rivers.reach(wx, wz));
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
