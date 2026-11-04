package com.porcupinestudios.tardisrb.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The console's body is wider than its block (about 2.6 blocks across, Aaron 2026-11-04), so these invisible blocks
 * stand in the eight cells round it, each holding its share of the console's hexagon, 1.2 blocks tall. {@code part}
 * picks the cell ({@link #CELLS}, the same order as {@code tools/capaldi.py}'s HULL_CELLS). The console block itself
 * uses {@link #shape(int, int)} for the middle cell.
 */
public final class ConsoleHullBlock extends CollisionOnlyBlock {
    /** The cells round the console block (x, z offsets), in {@code part} order. */
    public static final int[][] CELLS = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, CELLS.length - 1);
    /** The hexagon's centre (the time rotor's axis) from the console block's corner, its apothem and height. */
    private static final double CX = 0.5;
    private static final double CZ = 0.25;
    private static final double APOTHEM = 1.12;
    private static final int HEIGHT_PX = 19;
    private static final VoxelShape[] SHAPES = new VoxelShape[CELLS.length];

    static {
        for (int i = 0; i < CELLS.length; i++) {
            SHAPES[i] = shape(CELLS[i][0], CELLS[i][1]);
        }
    }

    public ConsoleHullBlock(Properties properties) {
        super(properties, Shapes.empty());
        registerDefaultState(stateDefinition.any().setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    protected VoxelShape collision(BlockState state) {
        return SHAPES[state.getValue(PART)];
    }

    /**
     * The part of the console's hexagon (flat sides facing the panels: north, south and every 60 degrees) in the cell
     * {@code dx, dz} from the console block, built from 2 px columns.
     */
    public static VoxelShape shape(int dx, int dz) {
        VoxelShape out = Shapes.empty();
        for (int i = 0; i < 8; i++) {
            for (int k = 0; k < 8; k++) {
                double x = dx + (i * 2 + 1) / 16.0 - CX;
                double z = dz + (k * 2 + 1) / 16.0 - CZ;
                boolean inside = true;
                for (int side = 0; side < 6 && inside; side++) {
                    double a = Math.toRadians(side * 60);
                    inside = x * Math.sin(a) + z * Math.cos(a) <= APOTHEM;
                }
                if (inside) {
                    out = Shapes.or(out, Block.box(i * 2, 0, k * 2, i * 2 + 2, HEIGHT_PX, k * 2 + 2));
                }
            }
        }
        return out.optimize();
    }
}
