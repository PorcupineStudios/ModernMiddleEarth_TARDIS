package com.porcupinestudios.lotrport.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.world.map.Roads;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A LOTR structure, placed the way LOTR's biome decorator placed it. Every chunk is a candidate (structure set
 * spacing 1) and rolls 1 in {@code chance}, LOTR's chance per chunk for these biomes. Then, as LOTR's own
 * generators did, it gives up on ground that isn't fit: water at the surface, more than {@code max_slope} blocks
 * between the highest and lowest ground under its footprint, or a road within {@code road_distance} blocks. One of
 * the {@code templates} is picked and turned at random.
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
            Codec.INT.optionalFieldOf("chance", 1).forGetter(s -> s.chance),
            Codec.INT.optionalFieldOf("salt", 0).forGetter(s -> s.salt),
            Codec.INT.fieldOf("ground").forGetter(s -> s.ground),
            Codec.INT.optionalFieldOf("max_slope", 6).forGetter(s -> s.maxSlope),
            Codec.INT.optionalFieldOf("road_distance", 16).forGetter(s -> s.roadDistance),
            Codec.INT.optionalFieldOf("max_foundation", 24).forGetter(s -> s.maxFoundation))
            .apply(i, LotrStructure::new));

    /** Ground samples across the footprint, this many blocks apart. */
    private static final int SAMPLE_STEP = 3;

    private final List<ResourceLocation> templates;
    private final int chance;
    private final int salt;
    private final int ground;
    private final int maxSlope;
    private final int roadDistance;
    private final int maxFoundation;

    public LotrStructure(StructureSettings settings, List<ResourceLocation> templates, int chance, int salt,
            int ground, int maxSlope, int roadDistance, int maxFoundation) {
        super(settings);
        this.templates = templates;
        this.chance = Math.max(1, chance);
        this.salt = salt;
        this.ground = ground;
        this.maxSlope = maxSlope;
        this.roadDistance = roadDistance;
        this.maxFoundation = maxFoundation;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        if (templates.isEmpty() || !rolls(ctx.seed(), ctx.chunkPos().x, ctx.chunkPos().z)) {
            return Optional.empty();
        }
        // where in the chunk, which layout, which way round
        int cx = ctx.chunkPos().getMinBlockX() + ctx.random().nextInt(16);
        int cz = ctx.chunkPos().getMinBlockZ() + ctx.random().nextInt(16);
        ResourceLocation id = templates.get(ctx.random().nextInt(templates.size()));
        Rotation rotation = Rotation.getRandom(ctx.random());
        StructureTemplate template = ctx.structureTemplateManager().getOrCreate(id);
        Vec3i size = template.getSize(rotation);
        if (size.getX() == 0) {
            return Optional.empty();
        }
        if (roadDistance > 0 && nearRoad(cx, cz, roadDistance + Math.max(size.getX(), size.getZ()) / 2)) {
            return Optional.empty();
        }
        int x0 = cx - size.getX() / 2;
        int z0 = cz - size.getZ() / 2;
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (int dx = 0; dx < size.getX() + SAMPLE_STEP - 1; dx += SAMPLE_STEP) {
            for (int dz = 0; dz < size.getZ() + SAMPLE_STEP - 1; dz += SAMPLE_STEP) {
                int x = x0 + Math.min(dx, size.getX() - 1);
                int z = z0 + Math.min(dz, size.getZ() - 1);
                int surface = ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        ctx.heightAccessor(), ctx.randomState());
                int floor = ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                        ctx.heightAccessor(), ctx.randomState());
                if (surface != floor) {
                    return Optional.empty(); // water
                }
                lo = Math.min(lo, surface);
                hi = Math.max(hi, surface);
                if (hi - lo > maxSlope) {
                    return Optional.empty();
                }
            }
        }
        int top = ctx.chunkGenerator().getBaseHeight(cx, cz, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(),
                ctx.randomState()) - 1;
        BlockPos origin = new BlockPos(cx, top, cz);
        BlockPos corner = new BlockPos(cx, top - ground, cz);
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(
                new LotrStructurePiece(ctx.structureTemplateManager(), id, corner, rotation, ground, maxFoundation))));
    }

    /**
     * LOTR's roll: 1 in {@code chance} for this chunk. A well-mixed hash of seed, chunk and salt; the vanilla
     * placement {@code frequency} seeds its random linearly from the chunk position, and with every chunk rolling
     * that left structures in long lines of neighbours (a row of overlapping hobbit houses, 2026-11-04).
     */
    private boolean rolls(long seed, int chunkX, int chunkZ) {
        long h = mix(seed ^ mix(chunkX * 0x9E3779B97F4A7C15L ^ mix(chunkZ * 0xC2B2AE3D27D4EB4FL ^ salt)));
        return Long.remainderUnsigned(h, chance) == 0;
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
