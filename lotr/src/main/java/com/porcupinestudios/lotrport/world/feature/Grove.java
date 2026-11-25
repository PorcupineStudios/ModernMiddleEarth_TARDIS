package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.lotrport.world.map.Wilds;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code lotr_port:grove} (Aaron 2026-11-25: "patches of forests, seeded and following rules that
 * make it feel life like"): trees in dense clumps with open land between, as Tolkien's Lone-lands ("bushes and stunted
 * trees in dense patches"). A seeded patch noise of {@code size} blocks, plus {@code hollow} for ground lower than
 * the land 40 blocks round (trees gather in sheltered, wetter hollows and gullies; per 8 blocks lower, capped), minus
 * {@code east} at the east end ({@code east_from} -> {@code east_to}: barer towards the Hoarwell). A position stays if
 * that value lies in [{@code above}, {@code below}): use {@code above} alone for the groves (thinning towards their
 * edge: the chance rises from 0.35 at the edge to 1 at 0.2 in), and a band just under a grove's {@code above} for the
 * scrub round it.
 */
public final class Grove extends PlacementFilter {
    public record Settings(float size, float above, float below, float hollow, float east, int eastFrom, int eastTo) {}

    public static final MapCodec<Grove> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("size").forGetter(g -> g.s.size()),
            Codec.FLOAT.fieldOf("above").forGetter(g -> g.s.above()),
            Codec.FLOAT.optionalFieldOf("below", 9f).forGetter(g -> g.s.below()),
            Codec.FLOAT.optionalFieldOf("hollow", 0f).forGetter(g -> g.s.hollow()),
            Codec.FLOAT.optionalFieldOf("east", 0f).forGetter(g -> g.s.east()),
            Codec.INT.optionalFieldOf("east_from", 0).forGetter(g -> g.s.eastFrom()),
            Codec.INT.optionalFieldOf("east_to", 0).forGetter(g -> g.s.eastTo()))
            .apply(i, (a, b, c, d, e, f, g) -> new Grove(new Settings(a, b, c, d, e, f, g))));

    private final Settings s;

    public Grove(Settings settings) {
        this.s = settings;
    }

    public Settings settings() {
        return s;
    }

    /** The grove value at a column (see the class comment). */
    public static double value(Settings s, int x, int z) {
        double v = Wilds.fbm(x, z, s.size(), 0x47524F56L);
        if (s.hollow() != 0) {
            MiddleEarthMap map = MiddleEarthMap.get();
            double around = (map.heightAt(x + 40, z) + map.heightAt(x - 40, z) + map.heightAt(x, z + 40)
                    + map.heightAt(x, z - 40)) / 4;
            v += s.hollow() * Math.max(-1, Math.min(1, (around - map.heightAt(x, z)) / 8));
        }
        if (s.eastTo() > s.eastFrom()) {
            v -= s.east() * Math.max(0, Math.min(1, (x - s.eastFrom()) / (double) (s.eastTo() - s.eastFrom())));
        }
        return v;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        double v = value(s, pos.getX(), pos.getZ());
        if (v < s.above() || v >= s.below()) {
            return false;
        }
        return s.below() < 9 || random.nextDouble() < Math.min(1, 0.35 + (v - s.above()) / 0.2 * 0.65);
    }

    @Override
    public PlacementModifierType<?> type() {
        return com.porcupinestudios.lotrport.world.LotrWorldgen.GROVE.get();
    }
}
