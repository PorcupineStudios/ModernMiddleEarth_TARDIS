package com.porcupinestudios.ringbearer.harness.content;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.ringbearer.harness.physics.Convex;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A slope: a right-triangle wedge (45 degrees, or one half of a gentle 2-block slope, {@link Profile}) placed like stairs (its low edge toward you, climbing away; upside down
 * when you place it against the top half of a side or under a block). On the ground it makes smooth ramps; on a
 * vehicle (a sled's nose, upside down, thin edge forward) it lets the vehicle ride up over block edges.
 *
 * <p>People and animals walk on a 16-step staircase of thin boxes. Vehicles don't: Sable would make the same boxes
 * into its colliders, and a sliding sled catches on every 1/16-block lip, so the slope gives Sable no collider at all
 * ({@link BlockSubLevelCollisionShape}) and {@link SlopeContacts} pushes vehicles off the true sloped face instead.
 * Wagon wheels read the true face too ({@code WheelTerrain}).
 */
public class SlopeBlock extends HorizontalDirectionalBlock implements BlockSubLevelCollisionShape {
    public static final MapCodec<SlopeBlock> CODEC = simpleCodec(SlopeBlock::new);
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    /** Few, wide steps: vanilla steps up the lowest height that helps, so a 1/16 staircase let entities climb one
     * slice a tick (a horse slowed to 1.25 blocks/s, 2026-11-24); a quarter block a tick is faster than walking. */
    private static final int STEPS = 4;

    /**
     * How the face rises across the block: the full 45-degree slope (0 to 1), or the two halves of a gentle one (0 to
     * 1/2 and 1/2 to 1, placed one after the other: a 2-block ramp, or upside down a 2-block sled nose, at 27 degrees).
     */
    public enum Profile {
        FULL(0, 1), LOW(0, 0.5), HIGH(0.5, 1);

        public final double h0, h1;

        Profile(double h0, double h1) {
            this.h0 = h0;
            this.h1 = h1;
        }

        double at(double u) {
            return h0 + (h1 - h0) * u;
        }
    }

    private final Profile profile;
    private final Map<Direction, VoxelShape> bottomShapes = new EnumMap<>(Direction.class);
    private final Map<Direction, VoxelShape> topShapes = new EnumMap<>(Direction.class);

    /** A box across the block between u0 and u1 along the facing (0 = the near, low edge), from y0 to y1. */
    private static VoxelShape slice(Direction f, double u0, double u1, double y0, double y1) {
        return switch (f) {
            case SOUTH -> Shapes.box(0, y0, u0, 1, y1, u1);
            case NORTH -> Shapes.box(0, y0, 1 - u1, 1, y1, 1 - u0);
            case EAST -> Shapes.box(u0, y0, 0, u1, y1, 1);
            default -> Shapes.box(1 - u1, y0, 0, 1 - u0, y1, 1);
        };
    }

    public SlopeBlock(Properties properties) {
        this(properties, Profile.FULL);
    }

    public SlopeBlock(Properties properties, Profile profile) {
        super(properties);
        this.profile = profile;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.BOTTOM));
        for (Direction f : Direction.Plane.HORIZONTAL) {
            VoxelShape bottom = Shapes.empty(), top = Shapes.empty();
            for (int i = 0; i < STEPS; i++) {
                double u0 = (double) i / STEPS, u1 = (double) (i + 1) / STEPS;
                double h = profile.at(u1);
                bottom = Shapes.or(bottom, slice(f, u0, u1, 0, h));
                top = Shapes.or(top, slice(f, u0, u1, 1 - h, 1));
            }
            bottomShapes.put(f, bottom.optimize());
            topShapes.put(f, top.optimize());
        }
    }

    public Profile profile() {
        return profile;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    /** Like stairs: facing where you look (so the low edge is toward you); upside down from a ceiling or high click. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos();
        boolean top = face == Direction.DOWN
                || (face != Direction.UP && context.getClickLocation().y - pos.getY() > 0.5);
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection())
                .setValue(HALF, top ? Half.TOP : Half.BOTTOM);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == Half.TOP ? topShapes : bottomShapes).get(state.getValue(FACING));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return Shapes.empty();
    }

    /** The true solid of this slope at {@code pos} (block coordinates, in whatever frame pos is in). */
    public static Convex wedge(BlockState state, BlockPos pos) {
        Direction f = state.getValue(FACING);
        Profile p = ((SlopeBlock) state.getBlock()).profile;
        return Convex.prism(pos.getX(), pos.getY(), pos.getZ(), f.getStepX(), f.getStepZ(),
                state.getValue(HALF) == Half.TOP, p.h0, p.h1);
    }

    /**
     * The height of the slope's top over a point of its block (local x, z in 0..1): the sloped face right side up, the
     * flat top upside down.
     */
    public static double topAt(BlockState state, double lx, double lz) {
        if (state.getValue(HALF) == Half.TOP) {
            return 1;
        }
        Direction f = state.getValue(FACING);
        double u = f.getStepX() * (lx - 0.5) + f.getStepZ() * (lz - 0.5) + 0.5;
        return ((SlopeBlock) state.getBlock()).profile.at(Math.max(0, Math.min(1, u)));
    }
}
