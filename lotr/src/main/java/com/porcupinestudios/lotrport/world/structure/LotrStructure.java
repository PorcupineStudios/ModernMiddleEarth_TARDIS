package com.porcupinestudios.lotrport.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.world.map.Roads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A LOTR structure, placed the way LOTR's biome decorator placed it.
 *
 * <p>The roll is LOTR's own, bit for bit: every chunk seeds one {@link Random} from its position alone (no world
 * seed), takes the biome at the chunk's +16, +16 corner, and rolls that biome's structures in LOTR's order, each 1 in
 * its chance. {@code rolls} holds, per biome, the chances up to and including this structure, so it replays the rolls
 * before its own. As in LOTR, no chunk within {@code axis_margin} blocks of the x or z axis gets one, nor a chunk with a
 * road within {@code chunk_road_distance} of its +8, +8 spot.
 *
 * <p>LOTR's generators then turned many rolls down on its own terrain (their ground checks). {@code lotr_success} holds,
 * per biome, the share of rolls that stood there, measured in LOTR itself (the converter's structure_rates.json), and a
 * roll is kept with that chance (a fixed hash of seed, chunk and salt). So this port's terrain doesn't turn rolls down
 * a second time, the structure then tries up to {@link #TRIES} spots in the chunk for ground that fits: no water at
 * the surface, at most {@code max_slope} blocks between the highest and lowest ground under its footprint, no road
 * within {@code road_distance} blocks. If none fits, or the spot is in another LOTR structure's footprint, it isn't
 * built. LOTR's generators refused anything but natural ground under them, so a later one never stood in an earlier
 * one; here the one that comes first (a fixed order of chunks, then LOTR's order within a chunk) stands. Spots, layout
 * and turn come from a random of this structure's own, so two structures rolling in one chunk don't land together.
 *
 * <p>The template's layer {@code ground} sits at the surface (its top block). Templates hold real air only where
 * LOTR cleared space (cellars, rooms, under roofs); everywhere else the natural ground stays, and
 * {@link LotrStructurePiece} fills under the ground layer down to solid ground, so nothing floats and no ditch is
 * carved around the building.
 */
public final class LotrStructure extends Structure {
    public static final MapCodec<LotrStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            ResourceLocation.CODEC.listOf().fieldOf("templates").forGetter(s -> s.templates),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT.listOf()).fieldOf("rolls").forGetter(s -> s.rolls),
            Codec.INT.optionalFieldOf("salt", 0).forGetter(s -> s.salt),
            Codec.INT.fieldOf("ground").forGetter(s -> s.ground),
            Codec.INT.optionalFieldOf("max_slope", 6).forGetter(s -> s.maxSlope),
            Codec.INT.optionalFieldOf("road_distance", 16).forGetter(s -> s.roadDistance),
            Codec.INT.optionalFieldOf("max_foundation", 24).forGetter(s -> s.maxFoundation),
            Codec.INT.optionalFieldOf("axis_margin", 32).forGetter(s -> s.axisMargin),
            Codec.INT.optionalFieldOf("chunk_road_distance", 16).forGetter(s -> s.chunkRoadDistance),
            Codec.INT.optionalFieldOf("gap", 2).forGetter(s -> s.gap),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT).optionalFieldOf("lotr_success", Map.of())
                    .forGetter(s -> s.success))
            .apply(i, LotrStructure::new));

    /** Ground samples across the footprint, this many blocks apart. */
    private static final int SAMPLE_STEP = 3;
    /** Spots tried in a chunk for ground that fits. */
    private static final int TRIES = 8;
    private static final int NO_FIT = Integer.MIN_VALUE;

    /** The biome of the chunk last asked about on this thread: every LOTR structure asks for the same chunk in turn. */
    private static final ThreadLocal<ChunkBiome> LAST_BIOME = new ThreadLocal<>();
    private static volatile Others others;

    private final List<ResourceLocation> templates;
    private final Map<ResourceLocation, List<Integer>> rolls;
    private final int salt;
    private final int ground;
    private final int maxSlope;
    private final int roadDistance;
    private final int maxFoundation;
    private final int axisMargin;
    private final int chunkRoadDistance;
    private final int gap;
    private final Map<ResourceLocation, Float> success;

    public LotrStructure(StructureSettings settings, List<ResourceLocation> templates,
            Map<ResourceLocation, List<Integer>> rolls, int salt, int ground, int maxSlope, int roadDistance,
            int maxFoundation, int axisMargin, int chunkRoadDistance, int gap, Map<ResourceLocation, Float> success) {
        super(settings);
        this.templates = templates;
        this.rolls = Map.copyOf(rolls);
        this.salt = salt;
        this.ground = ground;
        this.maxSlope = maxSlope;
        this.roadDistance = roadDistance;
        this.maxFoundation = maxFoundation;
        this.axisMargin = axisMargin;
        this.chunkRoadDistance = chunkRoadDistance;
        this.gap = Math.max(0, gap);
        this.success = Map.copyOf(success);
    }

    /** Where a structure would stand for a chunk's roll, before its ground is checked. */
    public record Plan(LotrStructure structure, int chunkX, int chunkZ, int order, int x, int z,
            ResourceLocation template, Rotation rotation, Vec3i size) {
        int minX() {
            return x - size.getX() / 2;
        }

        int minZ() {
            return z - size.getZ() / 2;
        }

        int maxX() {
            return minX() + size.getX() - 1;
        }

        int maxZ() {
            return minZ() + size.getZ() - 1;
        }

        public boolean overlaps(Plan o, int gap) {
            return minX() - gap <= o.maxX() && o.minX() - gap <= maxX() && minZ() - gap <= o.maxZ()
                    && o.minZ() - gap <= maxZ();
        }

        /** Whether this one stands when both would: chunks in a fixed order for the world, then LOTR's list order. */
        boolean before(Plan o, long seed) {
            if (chunkX == o.chunkX && chunkZ == o.chunkZ) {
                return order < o.order;
            }
            return Long.compareUnsigned(chunkOrder(seed, chunkX, chunkZ), chunkOrder(seed, o.chunkX, o.chunkZ)) < 0;
        }
    }

    private record ChunkBiome(BiomeSource source, int chunkX, int chunkZ, ResourceLocation biome) {}

    /** Every LOTR structure in a world's registry, and the widest footprint among them. */
    private record Others(Registry<Structure> registry, List<LotrStructure> all, int widest) {}

    /** LOTR picks a chunk's structures by the chunk's biome (see {@link #rolls}), not the biome under the building. */
    @Override
    public Optional<GenerationStub> findValidGenerationPoint(GenerationContext ctx) {
        return findGenerationPoint(ctx);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        Placed placed = place(ctx);
        if (placed == null) {
            return Optional.empty();
        }
        Plan p = placed.plan();
        BlockPos origin = new BlockPos(p.x(), placed.y(), p.z());
        BlockPos corner = new BlockPos(p.x(), placed.y() - ground, p.z());
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new LotrStructurePiece(
                ctx.structureTemplateManager(), p.template(), corner, p.rotation(), ground, maxFoundation))));
    }

    /** A plan that passed every check, with the surface height it stands on. */
    public record Placed(Plan plan, int y) {}

    /** This structure in the context's chunk, or null if LOTR wouldn't have built it there. */
    public Placed place(GenerationContext ctx) {
        ChunkPos c = ctx.chunkPos();
        Placed placed = stand(ctx, spots(ctx, c.x, c.z, biomeAt(ctx, c.x, c.z)));
        return placed == null || blocked(ctx, placed.plan()) ? null : placed;
    }

    /**
     * How many of this structure LOTR built in the context's chunk on average: its roll's 1 in chance, times the share
     * that stood. What {@link #place} aims for; it falls short where no spot fits or another structure is there.
     */
    public double expected(GenerationContext ctx) {
        ChunkPos c = ctx.chunkPos();
        ResourceLocation biome = biomeAt(ctx, c.x, c.z);
        List<Integer> chances = chancesIn(c.x, c.z, biome);
        if (chances == null || chunkRoadDistance > 0 && nearRoad((c.x << 4) + 8, (c.z << 4) + 8, chunkRoadDistance)) {
            return 0;
        }
        return success.getOrDefault(biome, 1f) / Math.max(1, chances.get(chances.size() - 1));
    }

    /** The biome's chances, if LOTR rolls structures in this chunk at all (it is off the axes); otherwise null. */
    private List<Integer> chancesIn(int chunkX, int chunkZ, ResourceLocation biome) {
        List<Integer> chances = biome == null ? null : rolls.get(biome);
        if (templates.isEmpty() || chances == null || chances.isEmpty() || Math.abs(chunkX << 4) <= axisMargin
                || Math.abs(chunkZ << 4) <= axisMargin) {
            return null;
        }
        return chances;
    }

    /**
     * LOTR's roll and chunk rules for one chunk, and the share of rolls that stood in LOTR: the spots this structure
     * tries there, in order, or none.
     */
    List<Plan> spots(GenerationContext ctx, int chunkX, int chunkZ, ResourceLocation biome) {
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        List<Integer> chances = chancesIn(chunkX, chunkZ, biome);
        if (chances == null || !lotrRoll(chances, bx, bz)
                || chunkRoadDistance > 0 && nearRoad(bx + 8, bz + 8, chunkRoadDistance)
                || unit(ctx.seed(), chunkX, chunkZ, salt) >= success.getOrDefault(biome, 1f)) {
            return List.of();
        }
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(ctx.seed(), chunkX, chunkZ, salt);
        List<Plan> spots = new ArrayList<>(TRIES);
        for (int n = 0; n < TRIES; n++) {
            // LOTR's spot: 8 to 23 blocks into the chunk, the middle of the four chunks it decorated
            int x = bx + 8 + random.nextInt(16);
            int z = bz + 8 + random.nextInt(16);
            ResourceLocation id = templates.get(random.nextInt(templates.size()));
            Rotation rotation = Rotation.getRandom(random);
            Vec3i size = ctx.structureTemplateManager().getOrCreate(id).getSize(rotation);
            if (size.getX() != 0) {
                spots.add(new Plan(this, chunkX, chunkZ, chances.size() - 1, x, z, id, rotation, size));
            }
        }
        return spots;
    }

    /** The first of the spots whose ground fits, with the surface height it stands on; null if none does. */
    private Placed stand(GenerationContext ctx, List<Plan> spots) {
        for (Plan p : spots) {
            int top = fits(ctx, p);
            if (top != NO_FIT) {
                return new Placed(p, top);
            }
        }
        return null;
    }

    /**
     * LOTR's decorator roll: a {@link Random} seeded from the chunk's block corner alone, one {@code nextInt} per
     * structure in the biome's list, and this structure comes when its own (the last given) is 0.
     */
    static boolean lotrRoll(List<Integer> chances, int blockX, int blockZ) {
        Random random = new Random(lotrSeed(blockX, blockZ));
        int last = chances.size() - 1;
        for (int n = 0; n < last; n++) {
            random.nextInt(Math.max(1, chances.get(n)));
        }
        return random.nextInt(Math.max(1, chances.get(last))) == 0;
    }

    /** LOTR's structure seed for a chunk's block corner (int product for x, long for z, as LOTR computed it). */
    static long lotrSeed(int blockX, int blockZ) {
        long seed = (long) (blockX * 1879267) ^ (long) blockZ * 67209689L;
        return seed * seed * 5829687L + seed * 2876L;
    }

    /** The surface height the plan stands on, or {@link #NO_FIT} for water, slopes or roads. */
    private int fits(GenerationContext ctx, Plan plan) {
        Vec3i size = plan.size();
        if (roadDistance > 0 && nearRoad(plan.x(), plan.z(), roadDistance + Math.max(size.getX(), size.getZ()) / 2)) {
            return NO_FIT;
        }
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (int dx = 0; dx < size.getX() + SAMPLE_STEP - 1; dx += SAMPLE_STEP) {
            for (int dz = 0; dz < size.getZ() + SAMPLE_STEP - 1; dz += SAMPLE_STEP) {
                int x = plan.minX() + Math.min(dx, size.getX() - 1);
                int z = plan.minZ() + Math.min(dz, size.getZ() - 1);
                int surface = ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        ctx.heightAccessor(), ctx.randomState());
                int floor = ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                        ctx.heightAccessor(), ctx.randomState());
                if (surface != floor) {
                    return NO_FIT; // water
                }
                lo = Math.min(lo, surface);
                hi = Math.max(hi, surface);
                if (hi - lo > maxSlope) {
                    return NO_FIT;
                }
            }
        }
        return ctx.chunkGenerator().getBaseHeight(plan.x(), plan.z(), Heightmap.Types.WORLD_SURFACE_WG,
                ctx.heightAccessor(), ctx.randomState()) - 1;
    }

    /**
     * Whether another LOTR structure that comes first would stand on this plan's footprint (or within {@code gap}
     * blocks of it). Only that one's own roll and ground are checked, not whether something else blocks it, so where
     * three overlap in a chain the last may be left out though the middle one isn't built; never two that touch. (A
     * structure's spot is its first that fits, never moved for another structure, which keeps this exact.)
     */
    private boolean blocked(GenerationContext ctx, Plan plan) {
        Others o = others(ctx);
        int reach = (Math.max(plan.size().getX(), plan.size().getZ()) + o.widest()) / 2 + gap;
        int r = (reach + 15) / 16 + 1;
        Map<Long, ResourceLocation> biomes = new HashMap<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = plan.chunkX() + dx;
                int cz = plan.chunkZ() + dz;
                ResourceLocation biome = biomes.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> biomeAt(ctx, cx, cz));
                if (biome == null) {
                    continue;
                }
                for (LotrStructure other : o.all()) {
                    if (other == this && dx == 0 && dz == 0) {
                        continue;
                    }
                    List<Plan> spots = other.spots(ctx, cx, cz, biome);
                    // which of its spots fits only matters if one could be on this plan
                    if (spots.isEmpty() || !spots.get(0).before(plan, ctx.seed())
                            || spots.stream().noneMatch(q -> q.overlaps(plan, gap))) {
                        continue;
                    }
                    Placed q = other.stand(ctx, spots);
                    if (q != null && q.plan().overlaps(plan, gap)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Others others(GenerationContext ctx) {
        Registry<Structure> registry = ctx.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Others o = others;
        if (o != null && o.registry() == registry) {
            return o;
        }
        List<LotrStructure> all = new ArrayList<>();
        int widest = 0;
        for (Structure s : registry) {
            if (s instanceof LotrStructure lotr) {
                all.add(lotr);
                for (ResourceLocation id : lotr.templates) {
                    Vec3i size = ctx.structureTemplateManager().getOrCreate(id).getSize();
                    widest = Math.max(widest, Math.max(size.getX(), size.getZ()));
                }
            }
        }
        o = new Others(registry, List.copyOf(all), widest);
        others = o;
        return o;
    }

    /** The biome LOTR decorated a chunk with: the one at the chunk's +16, +16 corner. */
    private static ResourceLocation biomeAt(GenerationContext ctx, int chunkX, int chunkZ) {
        ChunkBiome last = LAST_BIOME.get();
        if (last != null && last.source() == ctx.biomeSource() && last.chunkX() == chunkX && last.chunkZ() == chunkZ) {
            return last.biome();
        }
        int x = (chunkX << 4) + 16;
        int z = (chunkZ << 4) + 16;
        ResourceLocation biome = ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),
                QuartPos.fromBlock(ctx.chunkGenerator().getSeaLevel()), QuartPos.fromBlock(z),
                ctx.randomState().sampler()).unwrapKey().map(ResourceKey::location).orElse(null);
        LAST_BIOME.set(new ChunkBiome(ctx.biomeSource(), chunkX, chunkZ, biome));
        return biome;
    }

    /** A number in [0, 1) fixed by the world seed, chunk and salt: whether a roll is kept. */
    private static double unit(long seed, int chunkX, int chunkZ, int salt) {
        return (mix(chunkOrder(seed, chunkX, chunkZ) ^ salt) >>> 11) * 0x1.0p-53;
    }

    /** A fixed, well-mixed order of chunks for the world: which of two overlapping structures stands. */
    private static long chunkOrder(long seed, int chunkX, int chunkZ) {
        return mix(seed ^ mix(chunkX * 0x9E3779B97F4A7C15L ^ mix(chunkZ * 0xC2B2AE3D27D4EB4FL)));
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static boolean nearRoad(int x, int z, int range) {
        Roads roads = Roads.get();
        double limit = roads.width() + range;
        int[][] probes = { { 0, 0 }, { range, 0 }, { -range, 0 }, { 0, range }, { 0, -range } };
        for (int[] p : probes) {
            for (Roads.Segment s : roads.near(x + p[0], z + p[1])) {
                if (s.distanceSq(x + 0.5, z + 0.5) <= limit * limit) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public StructureType<?> type() {
        return LotrStructures.LOTR.get();
    }
}
