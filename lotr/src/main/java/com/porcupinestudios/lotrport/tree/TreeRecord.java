package com.porcupinestudios.lotrport.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One grown tree: which tree feature made it, the lowest corner of its trunk on the ground, the box its blocks fill,
 * and whether a sapling grew it (planted) or world generation did. Enough to find every block of that tree again
 * (see {@link Trees#blocks}), so a colonist felling it takes the whole crown and no neighbour's.
 *
 * @param tree the configured feature id, e.g. {@code lotr_port:tree/beech_party}
 * @param trunk the trunk's lowest x/z corner, at the first log above the soil
 * @param trunkWidth trunk width (1 for most trees, 2 or 3 for big ones)
 * @param box every block of the tree lies in here
 * @param planted grown from saplings (a colony's orchard) rather than by world generation
 */
public record TreeRecord(ResourceLocation tree, BlockPos trunk, int trunkWidth, BoundingBox box, boolean planted) {
    public static final Codec<TreeRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("tree").forGetter(TreeRecord::tree),
            BlockPos.CODEC.fieldOf("trunk").forGetter(TreeRecord::trunk),
            Codec.INT.optionalFieldOf("trunk_width", 1).forGetter(TreeRecord::trunkWidth),
            BoundingBox.CODEC.fieldOf("box").forGetter(TreeRecord::box),
            Codec.BOOL.optionalFieldOf("planted", false).forGetter(TreeRecord::planted))
            .apply(i, TreeRecord::new));

    /** Whether a block position is part of the trunk's footprint column at or above the trunk. */
    public boolean inTrunk(BlockPos pos) {
        return pos.getX() >= trunk.getX() && pos.getX() < trunk.getX() + trunkWidth && pos.getZ() >= trunk.getZ()
                && pos.getZ() < trunk.getZ() + trunkWidth && pos.getY() >= trunk.getY();
    }
}
