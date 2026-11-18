package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code lotr_port:cave_pocket}: keeps a position only inside a cave pocket of one kind (lush,
 * dripstone...), so those caves come as scattered pockets underground, not as a whole region's caves (Aaron
 * 2026-11-17: lush caves, "not just 1 consecutive lush cave"; every country keeps variety below).
 *
 * <p>The world is cut into cells of {@code cell} x {@code cell_height} x {@code cell} blocks. A cell holds a pocket
 * of this {@code kind} with chance {@code chance} (a hash of the world seed, the cell and the kind, so kinds are
 * independent and a cell may hold none); the pocket is a flattened ball of radius {@code min_radius}..{@code
 * max_radius} (half as tall) placed wholly inside its cell, and only at least {@code below_surface} blocks under
 * the ground. Which countries have which kinds is the placed feature's biome list (converter/caves.py); the biome
 * config's lushCaves and dripstoneCaves scale the chance per biome.
 * {@code {"type": "lotr_port:cave_pocket", "kind": "lush", "chance": 0.3, ...}}.
 */
public final class CavePocket extends PlacementFilter {
    public static final MapCodec<CavePocket> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(c -> c.kind),
            Codec.FLOAT.fieldOf("chance").forGetter(c -> c.chance),
            Codec.INT.optionalFieldOf("cell", 112).forGetter(c -> c.cell),
            Codec.INT.optionalFieldOf("cell_height", 64).forGetter(c -> c.cellHeight),
            Codec.INT.optionalFieldOf("min_radius", 14).forGetter(c -> c.minRadius),
            Codec.INT.optionalFieldOf("max_radius", 30).forGetter(c -> c.maxRadius),
            Codec.INT.optionalFieldOf("below_surface", 10).forGetter(c -> c.belowSurface))
            .apply(i, CavePocket::new));

    private final String kind;
    private final float chance;
    private final int cell;
    private final int cellHeight;
    private final int minRadius;
    private final int maxRadius;
    private final int belowSurface;
    private final long salt;

    public CavePocket(String kind, float chance, int cell, int cellHeight, int minRadius, int maxRadius,
            int belowSurface) {
        this.kind = kind;
        this.chance = chance;
        this.cell = cell;
        this.cellHeight = cellHeight;
        this.minRadius = minRadius;
        this.maxRadius = Math.max(minRadius, Math.min(maxRadius, cell / 2 - 1));
        this.belowSurface = belowSurface;
        this.salt = kind.hashCode() * 0x9E3779B97F4A7C15L;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        if (pos.getY() > context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, pos.getX(), pos.getZ()) - belowSurface) {
            return false;
        }
        // the biome config's lushCaves / dripstoneCaves scale the chance (BiomeCaveConfig)
        double share = com.porcupinestudios.lotrport.world.BiomeCaveConfig.pockets(kind, pos.getX(), pos.getZ());
        return share > 0 && inside(context.getLevel().getSeed(), pos.getX(), pos.getY(), pos.getZ(), chance * share);
    }

    /** Whether a block is inside a pocket of this kind (for tests and previews too). */
    public boolean inside(long seed, int x, int y, int z) {
        return inside(seed, x, y, z, chance);
    }

    private boolean inside(long seed, int x, int y, int z, double chance) {
        int cx = Math.floorDiv(x, cell);
        int cy = Math.floorDiv(y, cellHeight);
        int cz = Math.floorDiv(z, cell);
        long h = mix(seed ^ salt, cx, cy, cz);
        if (unit(h) >= chance) {
            return false;
        }
        int radius = minRadius + (int) (unit(mix(h, 1, 0, 0)) * (maxRadius - minRadius + 1));
        int halfTall = Math.max(4, radius / 2);
        // centre anywhere the whole pocket fits in its cell
        double px = cx * (double) cell + radius + unit(mix(h, 2, 0, 0)) * (cell - 2 * radius);
        double py = cy * (double) cellHeight + halfTall + unit(mix(h, 3, 0, 0)) * Math.max(0, cellHeight - 2 * halfTall);
        double pz = cz * (double) cell + radius + unit(mix(h, 4, 0, 0)) * (cell - 2 * radius);
        double dx = (x + 0.5 - px) / radius;
        double dy = (y + 0.5 - py) / halfTall;
        double dz = (z + 0.5 - pz) / radius;
        return dx * dx + dy * dy + dz * dz <= 1;
    }

    private static long mix(long seed, int a, int b, int c) {
        long h = seed ^ (a * 0x632BE59BD9B4E019L) ^ (b * 0x8CB92BA72F3D8DD7L) ^ (c * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L; // SplitMix64's finalizer
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    @Override
    public PlacementModifierType<?> type() {
        return com.porcupinestudios.lotrport.world.LotrWorldgen.CAVE_POCKET.get();
    }
}
