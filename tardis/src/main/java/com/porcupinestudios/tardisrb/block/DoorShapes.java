package com.porcupinestudios.tardisrb.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shape of an open TARDIS door block: two 2 px side walls (the police box's doorway is 12 px wide) and a 1 px
 * back, open towards {@code front}.
 */
final class DoorShapes {
    private DoorShapes() {}

    static VoxelShape open(Direction front) {
        return switch (front) {
            case NORTH -> Shapes.or(Block.box(0, 0, 0, 2, 16, 16), Block.box(14, 0, 0, 16, 16, 16),
                    Block.box(0, 0, 15, 16, 16, 16));
            case SOUTH -> Shapes.or(Block.box(0, 0, 0, 2, 16, 16), Block.box(14, 0, 0, 16, 16, 16),
                    Block.box(0, 0, 0, 16, 16, 1));
            case WEST -> Shapes.or(Block.box(0, 0, 0, 16, 16, 2), Block.box(0, 0, 14, 16, 16, 16),
                    Block.box(15, 0, 0, 16, 16, 16));
            case EAST -> Shapes.or(Block.box(0, 0, 0, 16, 16, 2), Block.box(0, 0, 14, 16, 16, 16),
                    Block.box(0, 0, 0, 1, 16, 16));
            default -> Shapes.block();
        };
    }
}
