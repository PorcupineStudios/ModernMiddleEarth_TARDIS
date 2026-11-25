package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.world.map.Enclosures;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Feature {@code lotr_port:enclosure}: builds the enclosures of one kind ({@link Enclosures}: the Shire's fenced
 * orchards and Farmer Maggot's fields) in the chunk it runs for. Placed once per chunk (no placement modifiers); the
 * map says whether the chunk's cell has an enclosure of this {@code kind} (kind names are unique across biomes).
 *
 * <ul>
 * <li>The fence runs round the enclosure's rectangle on the ground (never over water); with chance
 * {@code hedge_chance} a whole enclosure gets a hedge {@code hedge_height} high instead (the Marish's hedgerows,
 * Tolkien's "hedges, gates and dikes"). {@code post} stands at the corners. Gates ({@code gate}, facing out of the
 * side they are on) sit where the map planned them, with {@code path} on the ground either side.</li>
 * <li>Inside, {@code headland} blocks next to the fence are left as they are (the strip the plough turned on).</li>
 * <li>{@code trees}: a configured tree feature grown on a grid {@code spacing} blocks apart (rows centred in the
 * plot), each spot with chance {@code fill}: orchard rows.</li>
 * <li>{@code crop}: {@code soil} (farmland) under a stack of {@code block} {@code min_height}-{@code max_height}
 * high; boolean property {@code flag} is set with {@code flag_chance} on the stalks above the first (corn cobs);
 * every {@code water_every}th row along the plot's length is a water channel (0: none).</li>
 * </ul>
 * Fences and gates are joined at once and again when the chunk is finished (post-processing), so joins across chunk
 * edges are right too.
 */
public final class EnclosureFeature extends Feature<EnclosureFeature.Config> {
    public record Crop(BlockState block, int minHeight, int maxHeight, Optional<String> flag, float flagChance,
            BlockState soil, int waterEvery) {
        public static final Codec<Crop> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("block").forGetter(Crop::block),
                Codec.intRange(1, 8).fieldOf("min_height").forGetter(Crop::minHeight),
                Codec.intRange(1, 8).fieldOf("max_height").forGetter(Crop::maxHeight),
                Codec.STRING.optionalFieldOf("flag").forGetter(Crop::flag),
                Codec.floatRange(0, 1).optionalFieldOf("flag_chance", 0f).forGetter(Crop::flagChance),
                BlockState.CODEC.optionalFieldOf("soil", Blocks.FARMLAND.defaultBlockState()).forGetter(Crop::soil),
                Codec.intRange(0, 64).optionalFieldOf("water_every", 0).forGetter(Crop::waterEvery))
                .apply(i, Crop::new));
    }

    public record Config(String kind, BlockState fence, BlockState gate, Optional<BlockState> post,
            Optional<BlockState> hedge, float hedgeChance, int hedgeHeight, Optional<BlockState> path, int headland,
            Optional<Holder<ConfiguredFeature<?, ?>>> trees, int spacing, float fill, Optional<Crop> crop)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Config::kind),
                BlockState.CODEC.fieldOf("fence").forGetter(Config::fence),
                BlockState.CODEC.fieldOf("gate").forGetter(Config::gate),
                BlockState.CODEC.optionalFieldOf("post").forGetter(Config::post),
                BlockState.CODEC.optionalFieldOf("hedge").forGetter(Config::hedge),
                Codec.floatRange(0, 1).optionalFieldOf("hedge_chance", 0f).forGetter(Config::hedgeChance),
                Codec.intRange(1, 4).optionalFieldOf("hedge_height", 1).forGetter(Config::hedgeHeight),
                BlockState.CODEC.optionalFieldOf("path").forGetter(Config::path),
                Codec.intRange(0, 8).optionalFieldOf("headland", 1).forGetter(Config::headland),
                ConfiguredFeature.CODEC.optionalFieldOf("trees").forGetter(Config::trees),
                Codec.intRange(2, 32).optionalFieldOf("spacing", 6).forGetter(Config::spacing),
                Codec.floatRange(0, 1).optionalFieldOf("fill", 1f).forGetter(Config::fill),
                Crop.CODEC.optionalFieldOf("crop").forGetter(Config::crop))
                .apply(i, Config::new));
    }

    public EnclosureFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        Config config = context.config();
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        Enclosures.Enclosure e = MiddleEarthMap.get().enclosureInCellOf(x0, z0);
        if (e == null || !e.kind().name().equals(config.kind()) || e.maxX() < x0 || e.minX() > x0 + 15
                || e.maxZ() < z0 || e.minZ() > z0 + 15) {
            return false;
        }
        RandomSource random = context.random();
        boolean hedged = config.hedge().isPresent() && e.roll(0) < config.hedgeChance();
        List<BlockPos> joins = new ArrayList<>();
        int inset = 1 + config.headland();
        for (int x = Math.max(x0, e.minX()); x <= Math.min(x0 + 15, e.maxX()); x++) {
            for (int z = Math.max(z0, e.minZ()); z <= Math.min(z0 + 15, e.maxZ()); z++) {
                int y = ground(level, x, z);
                BlockPos at = new BlockPos(x, y, z);
                BlockState top = level.getBlockState(at);
                if (!top.getFluidState().isEmpty()) {
                    continue; // never fence or plant over water
                }
                if (e.onFence(x, z)) {
                    fence(level, config, e, at, hedged, joins);
                    continue;
                }
                if (x < e.minX() + inset || x > e.maxX() - inset || z < e.minZ() + inset || z > e.maxZ() - inset) {
                    continue; // the headland
                }
                if (config.crop().isPresent()) {
                    crop(level, config.crop().get(), e, at, top, random);
                }
                if (config.trees().isPresent() && onGrid(e, config, inset, x, z) && random.nextFloat() < config.fill()
                        && top.is(BlockTags.DIRT)) {
                    clear(level, at.above());
                    config.trees().get().value().place(level, context.chunkGenerator(), random, at.above());
                }
            }
        }
        for (BlockPos p : joins) {
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), Block.UPDATE_CLIENTS);
            level.getChunk(p).markPosForPostprocessing(p);
        }
        return true;
    }

    /** The fence line: post at corners, gate where planned (path either side), else fence or hedge. */
    private static void fence(WorldGenLevel level, Config config, Enclosures.Enclosure e, BlockPos ground,
            boolean hedged, List<BlockPos> joins) {
        BlockPos at = ground.above();
        clear(level, at);
        Enclosures.Gate gate = e.gateAt(at.getX(), at.getZ());
        boolean corner = (at.getX() == e.minX() || at.getX() == e.maxX()) && (at.getZ() == e.minZ()
                || at.getZ() == e.maxZ());
        if (gate != null) {
            Direction facing = switch (gate.side()) {
                case 0 -> Direction.NORTH;
                case 1 -> Direction.SOUTH;
                case 2 -> Direction.WEST;
                default -> Direction.EAST;
            };
            BlockState state = config.gate();
            if (state.hasProperty(FenceGateBlock.FACING)) {
                state = state.setValue(FenceGateBlock.FACING, facing);
            }
            level.setBlock(at, state, Block.UPDATE_CLIENTS);
            joins.add(at);
            config.path().ifPresent(path -> {
                setPath(level, path, ground);
                BlockPos out = ground.relative(facing);
                BlockPos in = ground.relative(facing.getOpposite());
                setPath(level, path, out.atY(ground(level, out.getX(), out.getZ())));
                setPath(level, path, in.atY(ground(level, in.getX(), in.getZ())));
            });
            return;
        }
        if (corner && config.post().isPresent()) {
            level.setBlock(at, config.post().get(), Block.UPDATE_CLIENTS);
            if (hedged) {
                for (int k = 1; k < config.hedgeHeight(); k++) {
                    setIfOpen(level, at.above(k), config.hedge().get());
                }
            }
            return;
        }
        if (hedged) {
            for (int k = 0; k < config.hedgeHeight(); k++) {
                setIfOpen(level, at.above(k), config.hedge().get());
            }
            return;
        }
        level.setBlock(at, config.fence(), Block.UPDATE_CLIENTS);
        joins.add(at);
    }

    /** Soil and a crop stack on one column, or a water channel row. */
    private static void crop(WorldGenLevel level, Crop crop, Enclosures.Enclosure e, BlockPos ground, BlockState top,
            RandomSource random) {
        if (!top.is(BlockTags.DIRT) && !top.is(Blocks.FARMLAND)) {
            return;
        }
        if (crop.waterEvery() > 0) {
            boolean alongX = e.sizeX() >= e.sizeZ();
            int across = alongX ? ground.getZ() - e.minZ() : ground.getX() - e.minX();
            if (across % crop.waterEvery() == crop.waterEvery() / 2) {
                clear(level, ground.above());
                level.setBlock(ground, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                return;
            }
        }
        clear(level, ground.above());
        level.setBlock(ground, crop.soil(), Block.UPDATE_CLIENTS);
        int height = crop.minHeight() + random.nextInt(Math.max(1, crop.maxHeight() - crop.minHeight() + 1));
        Property<?> flag = crop.flag().map(f -> crop.block().getBlock().getStateDefinition().getProperty(f))
                .orElse(null);
        for (int k = 1; k <= height; k++) {
            BlockPos p = ground.above(k);
            if (!level.getBlockState(p).canBeReplaced()) {
                break;
            }
            BlockState state = crop.block();
            if (k > 1 && flag instanceof BooleanProperty b && random.nextFloat() < crop.flagChance()) {
                state = state.setValue(b, true);
            }
            level.setBlock(p, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Whether a column is one of the plot's tree spots: a grid {@code spacing} apart, centred in the planted area. */
    private static boolean onGrid(Enclosures.Enclosure e, Config config, int inset, int x, int z) {
        int s = config.spacing();
        int spanX = e.sizeX() - 2 * inset;
        int spanZ = e.sizeZ() - 2 * inset;
        int ox = e.minX() + inset + Math.floorMod(spanX - 1, s) / 2;
        int oz = e.minZ() + inset + Math.floorMod(spanZ - 1, s) / 2;
        return x >= ox && z >= oz && (x - ox) % s == 0 && (z - oz) % s == 0;
    }

    /**
     * The y of a column's ground: its top block that isn't air, a plant, snow, leaves or a log (a neighbour's crown
     * may hang over the plot). Reads WORLD_SURFACE: while features are placed only the final heightmaps are kept up
     * to date (the _WG ones stop at the carvers' terrain, under every tree).
     */
    static int ground(WorldGenLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x,
                level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
        while (p.getY() > level.getMinBuildHeight()) {
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty()) {
                return p.getY();
            }
            if (!(s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS))) {
                return p.getY();
            }
            p.move(Direction.DOWN);
        }
        return p.getY();
    }

    /** Takes plants (grass, flowers, snow) out of a block so something can stand there. */
    private static void clear(WorldGenLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!s.isAir() && s.canBeReplaced() && s.getFluidState().isEmpty()) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockState up = level.getBlockState(p.above());
            if (!up.isAir() && up.canBeReplaced() && up.getFluidState().isEmpty()) { // a tall plant's top half
                level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void setIfOpen(WorldGenLevel level, BlockPos p, BlockState state) {
        BlockState s = level.getBlockState(p);
        if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) {
            level.setBlock(p, state, Block.UPDATE_CLIENTS);
        }
    }

    private static void setPath(WorldGenLevel level, BlockState path, BlockPos ground) {
        BlockState s = level.getBlockState(ground);
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT)) {
            level.setBlock(ground, path, Block.UPDATE_CLIENTS);
        }
    }
}
