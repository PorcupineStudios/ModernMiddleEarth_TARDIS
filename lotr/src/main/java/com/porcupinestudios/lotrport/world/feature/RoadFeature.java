package com.porcupinestudios.lotrport.world.feature;

import com.porcupinestudios.lotrport.world.map.Roads;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Feature {@code lotr_port:roads}: lays LOTR's roads through the chunk in each biome's road style ({@link Roads.Style},
 * LOTR's LOTRRoadType): weighted blocks per column, gaps on old roads (repair), a half slab where the road steps up
 * to a higher road column, and a wooden bridge (deck, beam edges, fences) where the road meets water. Placed once per
 * chunk (no placement modifiers), from the chunk's corner.
 */
public final class RoadFeature extends Feature<NoneFeatureConfiguration> {
    private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();

    public RoadFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        Roads roads = Roads.get();
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int x0 = origin.getX() & ~15;
        int z0 = origin.getZ() & ~15;
        if (roads.near(x0, z0).isEmpty() && roads.near(x0 + 15, z0 + 15).isEmpty()) {
            return false;
        }
        boolean placed = false;
        List<BlockPos> fences = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                if (!onRoad(roads, x, z)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                pos.set(x, y, z);
                BlockState top = level.getBlockState(pos);
                while (top.getFluidState().isEmpty() && isPlant(top) && y > level.getMinBuildHeight()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    pos.setY(--y);
                    top = level.getBlockState(pos);
                }
                Roads.Style style = roads.style(level.getBiome(pos).unwrapKey()
                        .map(k -> k.location().toString()).orElse(""));
                if (style == null) {
                    continue;
                }
                long hash = hash(x, y, z);
                if (!top.getFluidState().isEmpty()) {
                    bridge(level, roads, style, x, y, z, fences);
                    placed = true;
                    continue;
                }
                if (unit(hash) >= style.repair() || top.is(BlockTags.LEAVES) || top.is(BlockTags.LOGS)
                        || !top.isSolidRender(level, pos)) {
                    continue;
                }
                level.setBlock(pos, state(pick(style.top(), unit(hash >>> 21))), Block.UPDATE_CLIENTS);
                placed = true;
                if (stepsUp(level, roads, x, z, y)) {
                    BlockState slab = state(pick(style.slab(), unit(hash >>> 42)));
                    if (slab.getBlock() instanceof SlabBlock && level.getBlockState(pos.above()).isAir()) {
                        level.setBlock(pos.above(), slab, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        for (BlockPos fence : fences) {  // join the fences now they are all there
            level.setBlock(fence, Block.updateFromNeighbourShapes(level.getBlockState(fence), level, fence),
                    Block.UPDATE_CLIENTS);
        }
        return placed;
    }

    /** The road's ragged edge: a fixed per-column jitter of up to half a block on its width. */
    private static boolean onRoad(Roads roads, int x, int z) {
        double jitter = (((x * 73428767L) ^ (z * 912931L)) & 15) / 32.0;
        return roads.distance(x, z) <= roads.width() + jitter;
    }

    /**
     * LOTR's bridge column (LOTRRoadGenerator), over water whose surface is at y: the deck rises half a block for each
     * block of water under the surface, so a bridge arches over the deep middle (at most 5 above the water; a half
     * step is a slab). Columns at the road's edge (a non-road block among their 8 neighbours) are a beam with a fence
     * on it, a beam under it when raised and a fence hanging from it as a brace; every 8 blocks along the diagonals the
     * edge is a pillar from the river bed: beams, a deck block and a fence on top, 4 above the deck.
     */
    private static void bridge(WorldGenLevel level, Roads roads, Roads.Style style, int x, int y, int z,
                               List<BlockPos> fences) {
        int base = y + 1;
        float rise = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y - 1, z);
        while (p.getY() > level.getMinBuildHeight() && !level.getBlockState(p).getFluidState().isEmpty()) {
            rise += 0.5F;
            p.move(Direction.DOWN);
        }
        int top = Math.min(base + (int) Math.floor(rise), y + 6);
        boolean slab = top >= y + 6 || rise - (int) Math.floor(rise) < 0.5F;
        BlockState deck = state(style.deck());
        BlockState edge = state(style.edge());
        BlockState fence = state(style.fence());
        if (fenceAt(roads, x, z)) {
            if (pillarAt(roads, x, z)) {
                for (int j = top + 4; j > level.getMinBuildHeight(); j--) {
                    p.set(x, j, z);
                    if (level.getBlockState(p).isSolidRender(level, p)) {
                        break;
                    }
                    level.setBlock(p, j >= top + 4 ? fence : j >= top + 3 ? deck : edge, Block.UPDATE_CLIENTS);
                    if (j >= top + 4) {
                        fences.add(p.immutable());
                    }
                }
            } else {
                set(level, x, top, z, edge);
                set(level, x, top + 1, z, fence);
                fences.add(new BlockPos(x, top + 1, z));
                if (top > base) {
                    set(level, x, top - 1, z, edge);
                }
                int support = base + 2;
                if (top - 1 > support) {
                    set(level, x, support, z, fence);
                    fences.add(new BlockPos(x, support, z));
                }
            }
        } else {
            set(level, x, top, z, slab ? state(style.deckSlab()) : deck);
            if (top > base) {
                set(level, x, top - 1, z, deck);
            }
        }
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    /** LOTR: a road column with a non-road block among its 8 neighbours is the bridge's edge. */
    private static boolean fenceAt(Roads roads, int x, int z) {
        for (int i = -1; i <= 1; i++) {
            for (int k = -1; k <= 1; k++) {
                if ((i != 0 || k != 0) && !onRoad(roads, x + i, z + k)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** LOTR: edge columns on every 8th diagonal are pillars, unless a diagonal neighbour already is one. */
    private static boolean pillarAt(Roads roads, int x, int z) {
        if (Math.floorMod(Math.floorMod(x, 8) + Math.floorMod(z, 8), 8) != 0) {
            return false;
        }
        return !edgePillar(roads, x + 1, z - 1) && !edgePillar(roads, x + 1, z + 1);
    }

    private static boolean edgePillar(Roads roads, int x, int z) {
        return onRoad(roads, x, z) && fenceAt(roads, x, z) && pillarAt(roads, x, z);
    }

    /** A road column next to this one is a block higher: LOTR smooths the step with a half slab. */
    private static boolean stepsUp(WorldGenLevel level, Roads roads, int x, int z, int y) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int nx = x + dir.getStepX();
            int nz = z + dir.getStepZ();
            if (onRoad(roads, nx, nz)
                    && level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, nx, nz) - 1 == y + 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlant(BlockState state) {
        return state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS);
    }

    private static String pick(List<Roads.Pick> picks, double r) {
        double total = 0;
        for (Roads.Pick p : picks) {
            total += p.weight();
        }
        r *= total;
        for (Roads.Pick p : picks) {
            r -= p.weight();
            if (r < 0) {
                return p.block();
            }
        }
        return picks.get(picks.size() - 1).block();
    }

    private static BlockState state(String id) {
        return STATES.computeIfAbsent(id, k -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse(k)).defaultBlockState());
    }

    private static long hash(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 9183457L;
        h = h * h * 42317861L + h * 11L;
        return h ^ (h >>> 29) ^ (h << 17);
    }

    /** 21 bits of the hash as a number in [0, 1). */
    private static double unit(long bits) {
        return (bits & 0x1FFFFF) / (double) 0x200000;
    }
}
