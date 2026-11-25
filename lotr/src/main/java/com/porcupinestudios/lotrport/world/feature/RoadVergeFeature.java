package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.world.map.Roads;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Feature {@code lotr_port:road_verge}: keeps plants off the roads. Runs after all vegetation (top layer step) and
 * clears grass, flowers, ferns, bushes, cacti and the like from every road column ({@link RoadFeature#onRoad}) in the
 * chunk and {@code margin} blocks round it, so patches a neighbouring chunk spreads onto the road are cleared too.
 * Well-kept roads (LOTR's repair 1) stay bare. Run-down roads (repair under 1) let a few plants through: a column is
 * green with chance wear (1 - repair) x stretch x edge x {@code gap} (no road block laid there: the old ground) or
 * {@code laid} (on a road block), where stretch is a smooth per-{@code cell} factor between {@code stretch_min} and
 * {@code stretch_max} (some stretches clean, others overgrown) and edge runs from {@code middle} at the road's middle
 * to 1 at its edge. A green column keeps what grew there or, bare, is sown with one of {@code plants} with chance
 * {@code sow} (if it can live on the block). Every choice is a hash of the column, so the passes of neighbouring chunks
 * over the margin agree.
 */
public final class RoadVergeFeature extends Feature<RoadVergeFeature.Config> {
    public record Plant(String block, int weight) {
        public static final Codec<Plant> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("block").forGetter(Plant::block),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Plant::weight)).apply(i, Plant::new));
    }

    public record Config(int margin, int cell, float stretchMin, float stretchMax, float middle, float gap, float laid,
                         float sow, List<Plant> plants) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 8).optionalFieldOf("margin", 8).forGetter(Config::margin),
                Codec.intRange(1, 4096).optionalFieldOf("cell", 24).forGetter(Config::cell),
                Codec.FLOAT.optionalFieldOf("stretch_min", 0f).forGetter(Config::stretchMin),
                Codec.FLOAT.optionalFieldOf("stretch_max", 2f).forGetter(Config::stretchMax),
                Codec.FLOAT.optionalFieldOf("middle", 0.3f).forGetter(Config::middle),
                Codec.FLOAT.optionalFieldOf("gap", 0.5f).forGetter(Config::gap),
                Codec.FLOAT.optionalFieldOf("laid", 0.08f).forGetter(Config::laid),
                Codec.FLOAT.optionalFieldOf("sow", 0.6f).forGetter(Config::sow),
                Plant.CODEC.listOf().optionalFieldOf("plants", List.of()).forGetter(Config::plants))
                .apply(i, Config::new));
    }

    /** Each road style's own blocks (top and slab), to tell a laid road block from the old ground in a gap. */
    private static final Map<Roads.Style, Set<String>> LAID = new ConcurrentHashMap<>();

    public RoadVergeFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Roads roads = Roads.get();
        Config config = context.config();
        WorldGenLevel level = context.level();
        int m = config.margin();
        int x0 = (context.origin().getX() & ~15) - m;
        int z0 = (context.origin().getZ() & ~15) - m;
        int size = 16 + 2 * m;
        if (roads.near(x0, z0).isEmpty() && roads.near(x0 + size - 1, z0 + size - 1).isEmpty()
                && roads.near(x0 + size - 1, z0).isEmpty() && roads.near(x0, z0 + size - 1).isEmpty()) {
            return false;
        }
        boolean changed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                if (!RoadFeature.onRoad(roads, x, z)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                pos.set(x, y - 1, z);
                BlockState ground = level.getBlockState(pos);
                while (isTallPlant(ground) && pos.getY() > level.getMinBuildHeight()) {  // cacti, cane on the road
                    pos.move(0, -1, 0);
                    ground = level.getBlockState(pos);
                }
                if (!ground.getFluidState().isEmpty()) {
                    continue;  // a bridge's water: nothing grows there
                }
                Roads.Style style = roads.style(level.getBiome(pos).unwrapKey()
                        .map(k -> k.location().toString()).orElse(""));
                if (style == null) {
                    continue;
                }
                BlockPos plantAt = pos.above();
                long hash = hash(x, z);
                if (green(config, roads, style, ground, x, z, hash)) {
                    if (level.getBlockState(plantAt).isAir() && unit(hash >>> 21) < config.sow()) {
                        changed |= sow(level, config, plantAt, unit(hash >>> 42));
                    }
                    continue;
                }
                pos.set(plantAt);
                for (int n = 0; n < 4 && isPlant(level.getBlockState(pos)); n++) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    pos.move(0, 1, 0);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Whether this road column may hold a plant (see the class comment). */
    private static boolean green(Config config, Roads roads, Roads.Style style, BlockState ground, int x, int z,
                                 long hash) {
        double wear = 1 - style.repair();
        if (wear <= 0) {
            return false;
        }
        boolean laid = laid(style).contains(BuiltInRegistries.BLOCK.getKey(ground.getBlock()).toString());
        double edge = config.middle() + (1 - config.middle())
                * Math.min(1, roads.distance(x, z) / Math.max(0.5, roads.width()));
        double p = wear * stretch(config, x, z) * edge * (laid ? config.laid() : config.gap());
        return unit(hash) < p;
    }

    private static Set<String> laid(Roads.Style style) {
        return LAID.computeIfAbsent(style, s -> {
            Set<String> ids = new HashSet<>();
            s.top().forEach(p -> ids.add(p.block()));
            s.slab().forEach(p -> ids.add(p.block()));
            return ids;
        });
    }

    /** A smooth factor between stretch_min and stretch_max: value noise on a grid of {@code cell} blocks. */
    private static double stretch(Config config, int x, int z) {
        double fx = x / (double) config.cell();
        double fz = z / (double) config.cell();
        int cx = (int) Math.floor(fx);
        int cz = (int) Math.floor(fz);
        double tx = smooth(fx - cx);
        double tz = smooth(fz - cz);
        double a = cellValue(cx, cz) + (cellValue(cx + 1, cz) - cellValue(cx, cz)) * tx;
        double b = cellValue(cx, cz + 1) + (cellValue(cx + 1, cz + 1) - cellValue(cx, cz + 1)) * tx;
        double v = a + (b - a) * tz;
        return config.stretchMin() + (config.stretchMax() - config.stretchMin()) * v;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double cellValue(int cx, int cz) {
        return unit(hash(cx * 7919 + 13, cz * 104729 - 7) >>> 11);
    }

    private static boolean sow(WorldGenLevel level, Config config, BlockPos at, double r) {
        int total = 0;
        for (Plant p : config.plants()) {
            total += Math.max(0, p.weight());
        }
        if (total <= 0) {
            return false;
        }
        double left = r * total;
        Plant chosen = config.plants().get(config.plants().size() - 1);
        for (Plant p : config.plants()) {
            left -= Math.max(0, p.weight());
            if (left < 0) {
                chosen = p;
                break;
            }
        }
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(chosen.block()));
        BlockState state = block.defaultBlockState();
        if (block == Blocks.AIR || !state.canSurvive(level, at)) {
            return false;
        }
        if (block instanceof DoublePlantBlock) {
            if (!level.getBlockState(at.above()).isAir()) {
                return false;
            }
            DoublePlantBlock.placeAt(level, state, at, Block.UPDATE_CLIENTS);
        } else {
            level.setBlock(at, state, Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /** What the verge clears: grass, flowers, saplings, bushes and tall plants; never water, snow or air. */
    private static boolean isPlant(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getBlock() instanceof SnowLayerBlock) {
            return false;
        }
        return state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                || state.getBlock() instanceof BushBlock || isTallPlant(state);
    }

    /** Plants that block motion (so stand above the heightmap's ground). */
    private static boolean isTallPlant(BlockState state) {
        Block b = state.getBlock();
        return b instanceof CactusBlock || b instanceof SugarCaneBlock || b instanceof BambooStalkBlock;
    }

    private static long hash(int x, int z) {
        long h = x * 341873128712L ^ z * 132897987541L ^ 0x5DEECE66DL;
        h = h * h * 42317861L + h * 11L;
        return h ^ (h >>> 29) ^ (h << 17);
    }

    /** 21 bits of the hash as a number in [0, 1). */
    private static double unit(long bits) {
        return (bits & 0x1FFFFF) / (double) 0x200000;
    }
}
