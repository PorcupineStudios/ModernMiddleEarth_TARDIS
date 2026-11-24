package com.porcupinestudios.ringbearer.harness.content;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The hitch: build it into the front of a Create: Aeronautics vehicle, facing where the animals will walk. Put
 * harnesses on them ({@link HarnessItem}), lead them here and use it to hitch every one you're leading (it takes their
 * leads; two ropes run from its ends to each harness); sneak-use with an empty hand to unhitch them (they come back on
 * your leads, harnesses still on).
 */
public class HitchBlock extends HorizontalDirectionalBlock implements EntityBlock,
        dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape {
    public static final MapCodec<HitchBlock> CODEC = simpleCodec(HitchBlock::new);

    public HitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    /** The bar, the pole and the base, as drawn (people bump into those, not a whole block). */
    private static final java.util.Map<net.minecraft.core.Direction, net.minecraft.world.phys.shapes.VoxelShape> SHAPES =
            new java.util.EnumMap<>(net.minecraft.core.Direction.class);

    static {
        net.minecraft.world.phys.shapes.VoxelShape north = net.minecraft.world.phys.shapes.Shapes.or(
                Block.box(3, 0, 8, 13, 6, 16), Block.box(6, 4, 2, 10, 8, 8), Block.box(0, 7, 0, 16, 9, 2.5));
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            SHAPES.put(d, rotate(north, d));
        }
    }

    private static net.minecraft.world.phys.shapes.VoxelShape rotate(net.minecraft.world.phys.shapes.VoxelShape s,
                                                                    net.minecraft.core.Direction d) {
        net.minecraft.world.phys.shapes.VoxelShape out = net.minecraft.world.phys.shapes.Shapes.empty();
        for (var b : s.toAabbs()) {
            double[] c = switch (d) {
                case SOUTH -> new double[] {1 - b.maxX, 1 - b.maxZ, 1 - b.minX, 1 - b.minZ};
                case EAST -> new double[] {1 - b.maxZ, b.minX, 1 - b.minZ, b.maxX};
                case WEST -> new double[] {b.minZ, 1 - b.maxX, b.maxZ, 1 - b.minX};
                default -> new double[] {b.minX, b.minZ, b.maxX, b.maxZ};
            };
            out = net.minecraft.world.phys.shapes.Shapes.or(out, net.minecraft.world.phys.shapes.Shapes.box(c[0],
                    b.minY, c[1], c[2], b.maxY, c[3]));
        }
        return out.optimize();
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
                                                                 net.minecraft.world.level.BlockGetter level,
                                                                 BlockPos pos,
                                                                 net.minecraft.world.phys.shapes.CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    /**
     * No collider on a vehicle: the hitch is a light bar the traces hang from, so it can sit low at a sled's front
     * (where a pull doesn't tip it nose-down) and pass over step edges. (A block-high hitch on top of a sled's nose
     * tipped the sled over when pulled, 2026-11-24.)
     */
    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getSubLevelCollisionShape(
            net.minecraft.world.level.BlockGetter level, BlockState state) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HitchBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof HitchBlockEntity harness)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            List<Entity> freed = harness.unhitchAll();
            for (Entity e : freed) {
                if (e instanceof Mob mob) {
                    mob.setLeashedTo(player, true);
                }
            }
            player.displayClientMessage(Component.translatable("message.ringbearer_harness.unhitched", freed.size()),
                    true);
            return InteractionResult.CONSUME;
        }
        int[] result = hitchLed(harness, player);
        player.displayClientMessage(result[0] > 0
                ? Component.translatable("message.ringbearer_harness.hitched", result[0])
                : result[1] > 0 ? Component.translatable("message.ringbearer_harness.needs_harness")
                : Component.translatable("message.ringbearer_harness.none"), true);
        return InteractionResult.CONSUME;
    }

    /**
     * Hitches every animal {@code player} leads on a lead within 10 blocks that wears a harness (taking its lead).
     * Returns {hitched, led but not harnessed}.
     */
    public static int[] hitchLed(HitchBlockEntity hitch, Player player) {
        int hitched = 0, bare = 0;
        AABB around = player.getBoundingBox().inflate(10);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, around, m -> m.getLeashHolder() == player)) {
            if (!Harnessing.wears(mob)) {
                bare++;
            } else if (hitch.hitch(mob)) {
                mob.dropLeash(true, false);
                hitched++;
            }
        }
        return new int[] {hitched, bare};
    }
}
