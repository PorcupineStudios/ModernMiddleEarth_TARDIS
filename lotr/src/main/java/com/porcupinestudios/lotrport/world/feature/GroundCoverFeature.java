package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.lotrport.world.map.Wilds;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Feature {@code lotr_port:ground_cover} (Aaron 2026-11-25, the Lone-lands: "variation in the ground so its not
 * mundane grass through and through ... moss growth, dried up creek beds"), once per chunk, before trees and plants.
 * Every grass, dirt, coarse dirt or podzol top in the listed biomes (dithered over 12 blocks at their border; with
 * {@code variants}, only in those biome variants, feathered over the patch edge) is
 * re-dressed by rules on the real ground round it, in order:
 * <ol>
 * <li>dry creek beds: where a seeded noise crosses zero inside its sparse mask, a bed {@code creek.width} wide is cut a
 * block (two in the middle) into flat ground and laid with gravel, scree and stones, its banks coarse; never beside
 * water, never across steep ground;</li>
 * <li>steep ground (the ground 1 block either side differs by {@code rules.steep} or more) is bare rock down its face,
 * lichened where a patch noise says, and ground a step less steep is rubble;</li>
 * <li>gorge floors ({@link MiddleEarthMap#gorgeAt}) are rubble and gravel;</li>
 * <li>wet hollows (a moisture noise plus how far the column sits below the ground 2 blocks round it) are moorland
 * soil and, wettest, moss;</li>
 * <li>exposed ground: a patch noise plus eastward stoniness ({@code east}: 0 at {@code from} to 1 at {@code to}
 * block x; Tolkien's Lone-lands grow barer and stonier towards the Hoarwell) gives coarse ground, then rubble, then
 * bare rock flush with the ground; the rest stays grass.</li>
 * </ol>
 * Thresholds are jittered a little per column, so patch edges are ragged without salt-and-pepper. Then plants on what
 * is left: tussock everywhere (thicker where a noise says), bracken and heather in their own patches. Every noise is
 * seeded by the world ({@link Wilds}).
 */
public final class GroundCoverFeature extends Feature<GroundCoverFeature.Config> {
    public record Palette(BlockState rock, BlockState lichen, BlockState rubble, BlockState peat, BlockState moss,
            BlockState coarse, BlockState gravel) {
        static final Codec<Palette> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("rock").forGetter(Palette::rock),
                BlockState.CODEC.fieldOf("lichen").forGetter(Palette::lichen),
                BlockState.CODEC.fieldOf("rubble").forGetter(Palette::rubble),
                BlockState.CODEC.fieldOf("peat").forGetter(Palette::peat),
                BlockState.CODEC.fieldOf("moss").forGetter(Palette::moss),
                BlockState.CODEC.fieldOf("coarse").forGetter(Palette::coarse),
                BlockState.CODEC.fieldOf("gravel").forGetter(Palette::gravel)).apply(i, Palette::new));
    }

    /** Patch sizes (blocks) and thresholds of the ground rules. */
    public record Rules(float patch, float coarse, float rubble, float rock, float lichen, float wet, float moss,
            int steep, float east, int eastFrom, int eastTo) {
        static final Codec<Rules> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("patch").forGetter(Rules::patch),
                Codec.FLOAT.fieldOf("coarse").forGetter(Rules::coarse),
                Codec.FLOAT.fieldOf("rubble").forGetter(Rules::rubble),
                Codec.FLOAT.fieldOf("rock").forGetter(Rules::rock),
                Codec.FLOAT.fieldOf("lichen").forGetter(Rules::lichen),
                Codec.FLOAT.fieldOf("wet").forGetter(Rules::wet),
                Codec.FLOAT.fieldOf("moss").forGetter(Rules::moss),
                Codec.INT.fieldOf("steep").forGetter(Rules::steep),
                Codec.FLOAT.fieldOf("east").forGetter(Rules::east),
                Codec.INT.fieldOf("east_from").forGetter(Rules::eastFrom),
                Codec.INT.fieldOf("east_to").forGetter(Rules::eastTo)).apply(i, Rules::new));
    }

    /** Dry creek beds: noise size (blocks), bed width, share of the land the mask keeps (as a noise threshold). */
    public record Creeks(float scale, float width, float mask) {
        static final Codec<Creeks> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("scale").forGetter(Creeks::scale),
                Codec.FLOAT.fieldOf("width").forGetter(Creeks::width),
                Codec.FLOAT.fieldOf("mask").forGetter(Creeks::mask)).apply(i, Creeks::new));
    }

    /** A plant: the block, its chance per column on grass, and the patch noise it needs (-1 = everywhere). */
    public record Plant(BlockState block, float chance, float patch, float above) {
        static final Codec<Plant> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("block").forGetter(Plant::block),
                Codec.FLOAT.fieldOf("chance").forGetter(Plant::chance),
                Codec.FLOAT.fieldOf("patch").forGetter(Plant::patch),
                Codec.FLOAT.fieldOf("above").forGetter(Plant::above)).apply(i, Plant::new));
    }

    public record Config(List<ResourceLocation> biomes, List<String> variants, Palette blocks, Rules rules,
            Creeks creeks, List<Plant> plants) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("biomes").forGetter(Config::biomes),
                Codec.STRING.listOf().optionalFieldOf("variants", List.of()).forGetter(Config::variants),
                Palette.CODEC.fieldOf("blocks").forGetter(Config::blocks),
                Rules.CODEC.fieldOf("rules").forGetter(Config::rules),
                Creeks.CODEC.fieldOf("creeks").forGetter(Config::creeks),
                Plant.CODEC.listOf().fieldOf("plants").forGetter(Config::plants)).apply(i, Config::new));
    }

    private static final int M = 2; // margin of ground heights read round the chunk

    public GroundCoverFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        Config c = context.config();
        MiddleEarthMap map = MiddleEarthMap.get();
        Set<ResourceLocation> biomes = c.biomes().stream().collect(Collectors.toSet());
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        int n = 16 + 2 * M;
        int[] h = new int[n * n];
        boolean[] wet = new boolean[n * n];
        for (int dz = 0; dz < n; dz++) {
            for (int dx = 0; dx < n; dx++) {
                int x = x0 + dx - M;
                int z = z0 + dz - M;
                int y = EnclosureFeature.ground(level, x, z);
                h[dz * n + dx] = y;
                wet[dz * n + dx] = !level.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty();
            }
        }
        // the biome's share over 4 x 4 cells (5 samples 12 blocks apart), for a dithered border
        double[] share = new double[16];
        for (int cz = 0; cz < 4; cz++) {
            for (int cx = 0; cx < 4; cx++) {
                int mx = x0 + cx * 4 + 2;
                int mz = z0 + cz * 4 + 2;
                int[][] at = {{0, 0}, {12, 0}, {-12, 0}, {0, 12}, {0, -12}};
                int in = 0;
                for (int[] a : at) {
                    var key = level.getBiome(new BlockPos(mx + a[0], 64, mz + a[1])).unwrapKey();
                    if (key.isPresent() && biomes.contains(key.get().location())) {
                        in++;
                    }
                }
                share[cz * 4 + cx] = in / 5.0;
            }
        }
        Rules r = c.rules();
        Palette b = c.blocks();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx;
                int z = z0 + dz;
                double s = share[(dz >> 2) * 4 + (dx >> 2)];
                if (s <= 0 || s < 1 && Wilds.unit(x, z, 0x45444745L) >= s || !inVariant(map, c, x, z)) {
                    continue;
                }
                int i = (dz + M) * n + dx + M;
                if (wet[i]) {
                    continue;
                }
                int y = h[i];
                p.set(x, y, z);
                BlockState top = level.getBlockState(p);
                if (!dressable(top)) {
                    continue;
                }
                int east = h[i + 1];
                int west = h[i - 1];
                int south = h[i + n];
                int north = h[i - n];
                int slope = Math.max(Math.abs(east - west), Math.abs(south - north));
                int lowest = Math.min(Math.min(east, west), Math.min(south, north));
                double around = 0;
                boolean nearWater = false;
                for (int k = -2; k <= 2; k++) {
                    for (int l = -2; l <= 2; l++) {
                        around += h[i + k * n + l];
                        nearWater |= wet[i + k * n + l];
                    }
                }
                double hollow = around / 25 - y;
                double jitter = (Wilds.unit(x, z, 0x4A495454L) - 0.5) * 0.08;
                double eastward = r.eastTo() > r.eastFrom()
                        ? Math.max(0, Math.min(1, (x - r.eastFrom()) / (double) (r.eastTo() - r.eastFrom()))) : 0;

                // 1. dry creek beds
                double creek = nearWater || slope > 2 ? 0 : creek(c.creeks(), x, z);
                if (creek > 0) {
                    int depth = creek > 0.55 ? 2 : creek > 0.15 ? 1 : 0;
                    BlockState bed = creekBed(b, x, z, hollow);
                    if (depth == 0) { // bank
                        setTop(level, p, Wilds.unit(x, z, 0x42414E4BL) < 0.6 ? b.coarse() : b.peat());
                    } else {
                        clearAbove(level, p);
                        for (int k = 0; k < depth; k++) {
                            level.setBlock(p.setY(y - k), Blocks.AIR.defaultBlockState(), 2);
                        }
                        level.setBlock(p.setY(y - depth), bed, 2);
                    }
                    changed = true;
                    continue;
                }
                // 2. steep ground: rock down the face (to the lowest neighbour), rubble a step less steep
                double patch = Wilds.fbm(x, z, r.patch(), 0x50415443L);
                if (slope >= r.steep()) {
                    BlockState rock = Wilds.fbm(x, z, 7, 0x4C494348L) > r.lichen() + jitter ? b.lichen() : b.rock();
                    clearAbove(level, p);
                    for (int k = y; k > lowest && k > y - 6; k--) {
                        BlockState was = level.getBlockState(p.setY(k));
                        if (was.is(BlockTags.DIRT) || was.is(Blocks.GRASS_BLOCK)) {
                            level.setBlock(p, k == y ? rock : b.rock(), 2);
                        }
                    }
                    changed = true;
                    continue;
                }
                if (slope == r.steep() - 1 && patch + jitter > -0.2) {
                    setTop(level, p, Wilds.unit(x, z, 0x52554242L) < 0.7 ? b.rubble() : b.coarse());
                    changed = true;
                    continue;
                }
                // 3. gorge floors
                double gorge = map.gorgeAt(x, z);
                if (gorge > 0.5) {
                    setTop(level, p, Wilds.unit(x, z, 0x474F5247L) < 0.6 ? b.rubble() : b.gravel());
                    changed = true;
                    continue;
                }
                // 4. wet hollows
                double damp = Wilds.fbm(x, z, r.patch() * 1.6, 0x44414D50L) + 0.3 * Math.max(0, Math.min(3, hollow))
                        - 0.3 * eastward + jitter;
                if (damp > r.wet()) {
                    setTop(level, p, damp > r.moss() ? b.moss() : b.peat());
                    changed = true;
                    continue;
                }
                // 5. exposed ground, stonier eastwards
                double stony = patch + r.east() * eastward + jitter;
                BlockState cover = stony > r.rock() ? (Wilds.fbm(x, z, 7, 0x4C494348L) > r.lichen() ? b.lichen()
                        : b.rock()) : stony > r.rubble() ? b.rubble() : stony > r.coarse() ? b.coarse() : null;
                if (cover != null) {
                    setTop(level, p, cover);
                    changed = true;
                }
            }
        }
        // plants on what is left
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx;
                int z = z0 + dz;
                double s = share[(dz >> 2) * 4 + (dx >> 2)];
                if (s <= 0 || s < 1 && Wilds.unit(x, z, 0x45444745L) >= s || !inVariant(map, c, x, z)) {
                    continue;
                }
                int y = EnclosureFeature.ground(level, x, z);
                p.set(x, y, z);
                BlockState ground = level.getBlockState(p);
                if (!ground.is(BlockTags.DIRT) || !level.getBlockState(p.setY(y + 1)).isAir()) {
                    continue;
                }
                double roll = Wilds.unit(x, z, 0x504C414EL);
                for (int k = 0; k < c.plants().size(); k++) {
                    Plant plant = c.plants().get(k);
                    double chance = plant.chance();
                    if (plant.patch() > 0) {
                        double pn = Wilds.fbm(x, z, plant.patch(), 0x504C0000L + k);
                        if (pn < plant.above()) {
                            continue;
                        }
                        chance *= Math.min(1, (pn - plant.above()) / 0.15 + 0.3);
                    }
                    if (!ground.is(Blocks.GRASS_BLOCK)) {
                        chance *= 0.5;
                    }
                    if (roll < chance && plant.block().canSurvive(level, p)) {
                        level.setBlock(p, plant.block(), 2);
                        changed = true;
                        break;
                    }
                    roll -= chance;
                }
            }
        }
        return changed;
    }

    /**
     * Whether a column is in one of the config's biome variants (all, if none are listed), looked up a hashed few
     * blocks off up to the biome's feather, so the ground changes raggedly over a patch's edge, as its plants do.
     */
    static boolean inVariant(MiddleEarthMap map, Config c, int x, int z) {
        if (c.variants().isEmpty()) {
            return true;
        }
        int f = map.variantFeather(x, z);
        int ox = (int) ((Wilds.unit(x, z, 0x46455841L) * 2 - 1) * f);
        int oz = (int) ((Wilds.unit(x, z, 0x4645415AL) * 2 - 1) * f);
        String v = map.variantAt(x + ox, z + oz);
        return c.variants().contains(v == null ? "plain" : v);
    }

    /** How far into a dry creek bed a column is: 1 at its middle, 0..0.15 its bank, 0 outside. */
    static double creek(Creeks c, int x, int z) {
        if (Wilds.fbm(x, z, c.scale() * 3, 0x4352454DL) < c.mask()) {
            return 0;
        }
        double v = Wilds.fbm(x, z, c.scale(), 0x43524545L);
        double gx = Wilds.fbm(x + 1, z, c.scale(), 0x43524545L) - v;
        double gz = Wilds.fbm(x, z + 1, c.scale(), 0x43524545L) - v;
        double g = Math.sqrt(gx * gx + gz * gz);
        if (g < 1e-9) {
            return 0;
        }
        double width = c.width() * (0.8 + 0.4 * (Wilds.noise(x / 37.0, z / 37.0, 0x43574944L) + 1) / 2);
        double t = Math.abs(v) / g / (width / 2 + 1.5);
        return t >= 1 ? 0 : 1 - t;
    }

    private static BlockState creekBed(Palette b, int x, int z, double hollow) {
        double u = Wilds.unit(x, z, 0x42454444L);
        double run = Wilds.noise(x / 5.0, z / 5.0, 0x42454445L); // stones lie in runs, not single pixels
        if (run > 0.45) {
            return u < 0.5 ? b.rock() : Blocks.COBBLESTONE.defaultBlockState();
        }
        if (hollow > 0.5 && run < -0.5) {
            return b.moss();
        }
        return u < 0.55 ? b.gravel() : b.rubble();
    }

    private static boolean dressable(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL);
    }

    private static void setTop(WorldGenLevel level, BlockPos.MutableBlockPos p, BlockState state) {
        clearAbove(level, p);
        level.setBlock(p, state, 2);
    }

    /** Takes plants (and a double plant's top) off a column's top block. */
    private static void clearAbove(WorldGenLevel level, BlockPos.MutableBlockPos p) {
        int y = p.getY();
        for (int k = 1; k <= 2; k++) {
            BlockPos a = p.above(k);
            BlockState s = level.getBlockState(a);
            if (s.isAir() || !s.canBeReplaced() || !s.getFluidState().isEmpty()) {
                break;
            }
            level.setBlock(a, Blocks.AIR.defaultBlockState(), 2);
        }
        p.setY(y);
    }
}
