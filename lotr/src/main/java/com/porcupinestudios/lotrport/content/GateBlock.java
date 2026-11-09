package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LOTR's gates: round hobbit doors, portcullises, wooden and stone gates. A 4 px panel across the doorway, facing
 * {@code facing} (dwarven doors, {@code full}: a whole block while shut); using one, or powering it, opens or shuts the
 * whole gate (every touching block of the same kind in its plane, up to {@link #MAX_GATE}). Open, it lets everything
 * through and draws only its frame ({@code client/ConnectedModel}). NPCs path through shut upright gates as through
 * wooden doors and open them ({@code npc/OpenGateGoal}); LOTR's NPCs couldn't, Aaron wants them to (colonists).
 */
public class GateBlock extends DirectionalBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    static final int MAX_GATE = 64;
    private static final VoxelShape ACROSS_Z = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape ACROSS_X = Block.box(6, 0, 0, 10, 16, 16);
    private static final VoxelShape FLAT = Block.box(0, 6, 0, 16, 10, 16);
    private final boolean full;

    public GateBlock(Properties properties, boolean full) {
        super(properties);
        this.full = full;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return simpleCodec(p -> new GateBlock(p, full));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    /** Structures are placed turned and mirrored; without these a gate kept its old facing and stood across the
     * doorway's sides instead of filling it. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) {
            return Shapes.empty();
        }
        if (full) {
            return Shapes.block();
        }
        return switch (state.getValue(FACING).getAxis()) {
            case X -> ACROSS_X;
            case Z -> ACROSS_Z;
            default -> FLAT;
        };
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN) ? Shapes.block() : getShape(state, level, pos, CollisionContext.empty());
    }

    /** As LOTR: with a block or a bow in hand the click places or shoots instead of working the gate. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof BlockItem || stack.getItem() instanceof ProjectileWeaponItem
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide) {
            setOpen(level, pos, !state.getValue(OPEN));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** LOTR's redstone: a signal at any block of the gate opens it; losing it shuts it. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from,
            boolean moved) {
        if (level.isClientSide || neighbor instanceof GateBlock) {
            return;
        }
        Set<BlockPos> gate = gate(level, pos, state);
        boolean powered = gate.stream().anyMatch(level::hasNeighborSignal);
        if ((powered || neighbor.defaultBlockState().isSignalSource()) && powered != state.getValue(OPEN)) {
            setOpen(level, pos, powered);
        }
    }

    /** A shut upright gate is a door to the pathfinder: mobs that open doors path through, others go round. */
    @Override
    public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, Mob mob) {
        return !state.getValue(OPEN) && state.getValue(FACING).getAxis() != Direction.Axis.Y
                ? PathType.DOOR_WOOD_CLOSED : null;
    }

    /**
     * LOTR's connected textures ({@code client/ConnectedModel}): a block connects to the same block. A gate only within
     * its own plane, to a gate with the same axis and open state (shut: the same gate block, open: any gate), and an
     * open upright gate to whatever is under it (so its frame stops at the ground).
     */
    public static boolean connects(BlockGetter level, BlockPos pos, BlockState state, BlockPos other) {
        BlockState o = level.getBlockState(other);
        if (!(state.getBlock() instanceof GateBlock)) {
            return o.is(state.getBlock());
        }
        Direction.Axis axis = state.getValue(FACING).getAxis();
        if (axis.choose(other.getX() - pos.getX(), other.getY() - pos.getY(), other.getZ() - pos.getZ()) != 0) {
            return false;
        }
        boolean open = state.getValue(OPEN);
        boolean gate = o.getBlock() instanceof GateBlock;
        // the whole row below, diagonals too: else the bottom quarters between two gate blocks get the inner corner
        if (open && !gate && axis != Direction.Axis.Y && other.getY() < pos.getY()) {
            return true;
        }
        return gate && (open || o.is(state.getBlock())) && o.getValue(FACING).getAxis() == axis
                && o.getValue(OPEN) == open;
    }

    /** Opens or shuts the whole gate {@code pos} belongs to; false if it isn't a gate or already was. */
    public static boolean setOpen(Level level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof GateBlock) || state.getValue(OPEN) == open) {
            return false;
        }
        for (BlockPos p : gate(level, pos, state)) {
            level.setBlock(p, level.getBlockState(p).setValue(OPEN, open), Block.UPDATE_ALL);
        }
        // LOTR's own gate sounds: stone gates grind, the rest creak (LOTRBlockGate)
        String stone = state.getSoundType(level, pos, null) == SoundType.STONE ? "stone_" : "";
        SoundEvent sound = LotrSounds.get("block.gate." + stone + (open ? "open" : "close"))
                .map(Holder::value)
                .orElse(open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    /**
     * The blocks of one gate: this one and every touching block of the same kind facing the same way in the gate's
     * plane, so two gates side by side in a gatehouse, or one behind another, open on their own.
     */
    public static Set<BlockPos> gate(BlockGetter level, BlockPos start, BlockState from) {
        Block block = from.getBlock();
        Direction.Axis axis = from.getValue(FACING).getAxis();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start);
        seen.add(start);
        while (!todo.isEmpty() && seen.size() < MAX_GATE) {
            BlockPos p = todo.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                BlockState other = level.getBlockState(n);
                if (d.getAxis() != axis && !seen.contains(n) && other.is(block)
                        && other.getValue(FACING).getAxis() == axis) {
                    seen.add(n);
                    todo.add(n);
                }
            }
        }
        return seen;
    }
}
