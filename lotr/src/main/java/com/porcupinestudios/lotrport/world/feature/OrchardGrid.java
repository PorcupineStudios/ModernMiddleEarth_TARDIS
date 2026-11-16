package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement modifier {@code lotr_port:orchard_grid}: every column of the chunk on a world-aligned grid of
 * {@code spacing} blocks (rows run on across chunks), each kept with chance {@code fill}: orchard rows, as LOTR's
 * orchard variants planted fruit trees in rows. Put it first, in place of count + in_square.
 */
public final class OrchardGrid extends PlacementModifier {
    public static final MapCodec<OrchardGrid> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(2, 16).fieldOf("spacing").forGetter(g -> g.spacing),
            Codec.floatRange(0, 1).optionalFieldOf("fill", 1f).forGetter(g -> g.fill)).apply(i, OrchardGrid::new));
    private final int spacing;
    private final float fill;

    public OrchardGrid(int spacing, float fill) {
        this.spacing = spacing;
        this.fill = fill;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int x0 = pos.getX() & ~15;
        int z0 = pos.getZ() & ~15;
        Stream.Builder<BlockPos> out = Stream.builder();
        for (int x = x0 + Math.floorMod(-x0, spacing); x < x0 + 16; x += spacing) {
            for (int z = z0 + Math.floorMod(-z0, spacing); z < z0 + 16; z += spacing) {
                if (random.nextFloat() < fill) {
                    out.add(new BlockPos(x, pos.getY(), z));
                }
            }
        }
        return out.build();
    }

    @Override
    public PlacementModifierType<?> type() {
        return com.porcupinestudios.lotrport.world.LotrWorldgen.ORCHARD_GRID.get();
    }
}
