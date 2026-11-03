package com.porcupinestudios.tardisrb.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The shape of an open TARDIS door block: two 1 px side walls and a 1 px back, open towards {@code front}. */
final class DoorShapes {
    private DoorShapes() {}

    static VoxelShape open(Direction front) {
        return switch (front) {
            case NORTH -> Shapes.or(Block.box(0, 0, 0, 1, 16, 16), Block.box(15, 0, 0, 16, 16, 16),
                    Block.box(0, 0, 15, 16, 16, 16));
            case SOUTH -> Shapes.or(Block.box(0, 0, 0, 1, 16, 16), Block.box(15, 0, 0, 16, 16, 16),
                    Block.box(0, 0, 0, 16, 16, 1));
            case WEST -> Shapes.or(Block.box(0, 0, 0, 16, 16, 1), Block.box(0, 0, 15, 16, 16, 16),
                    Block.box(15, 0, 0, 16, 16, 16));
            case EAST -> Shapes.or(Block.box(0, 0, 0, 16, 16, 1), Block.box(0, 0, 15, 16, 16, 16),
                    Block.box(0, 0, 0, 1, 16, 16));
            default -> Shapes.block();
        };
    }
}
