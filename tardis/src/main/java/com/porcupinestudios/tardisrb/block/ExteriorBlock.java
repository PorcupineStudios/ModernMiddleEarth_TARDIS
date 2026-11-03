package com.porcupinestudios.tardisrb.block;

import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.interior.Interiors;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The police box, two blocks tall; the lower half holds the TARDIS id. Using it: the crew open or close the doors
 * (see-through portals, {@link Doors}) and walk in, or sneak-use it to step straight inside; an unclaimed wreck is
 * claimed by the first player allowed to own one; anyone else finds it locked. Placed and moved only by code
 * ({@link Exteriors}).
 */
public final class ExteriorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** Which look (model and texture set) the box wears; set from the look rules ({@code Looks}). */
    public static final IntegerProperty LOOK = IntegerProperty.create("look", 0, 3);
    /** The doors stand open: a hollow shell with a doorway the portal sits in ({@link Doors}). */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public ExteriorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(LOOK, 0).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, LOOK, OPEN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new ExteriorBlockEntity(pos, state) : null;
    }

    /** The lower half's position for either half. */
    public static BlockPos base(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** The TARDIS behind the box at this position, if any. */
    public static Optional<TardisRecord> tardisAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ExteriorBlock)) {
            return Optional.empty();
        }
        if (level.getBlockEntity(base(state, pos)) instanceof ExteriorBlockEntity be) {
            return be.tardis().flatMap(id -> TardisManager.get(level.getServer()).get(id));
        }
        return Optional.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
            return InteractionResult.PASS;
        }
        Optional<TardisRecord> found = tardisAt(sl, pos);
        if (found.isEmpty()) {
            sp.displayClientMessage(Component.translatableWithFallback("tardis_rb.exterior.dead",
                    "The box is dead and empty"), true);
            return InteractionResult.CONSUME;
        }
        TardisRecord tardis = found.get();
        TardisManager manager = TardisManager.get(sl.getServer());
        if (tardis.owner().isEmpty()) {
            if (!manager.mayOwnAnother(sp.getUUID())) {
                sp.displayClientMessage(Component.translatableWithFallback("tardis_rb.exterior.salvage",
                        "You already have a TARDIS; this one is only good for parts"), true);
                return InteractionResult.CONSUME;
            }
            tardis.claim(sp.getUUID());
            sp.sendSystemMessage(Component.translatableWithFallback("tardis_rb.exterior.claimed",
                    "The door creaks open. This wreck is yours now."));
        }
        if (!tardis.isCrew(sp.getUUID())) {
            sp.displayClientMessage(Component.translatableWithFallback("tardis_rb.exterior.locked",
                    "The door is locked"), true);
            return InteractionResult.CONSUME;
        }
        if (sp.isShiftKeyDown()) {
            Interiors.enter(sp, tardis);
        } else {
            boolean wasOpen = Doors.isOpen(tardis);
            if (!Doors.toggle(sl.getServer(), tardis)) {
                Interiors.enter(sp, tardis);
            } else {
                sp.displayClientMessage(wasOpen
                        ? Component.translatableWithFallback("tardis_rb.door.closed", "The doors close")
                        : Component.translatableWithFallback("tardis_rb.door.opened", "The doors swing open"), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // drawn by the exterior renderer from Aaron's model, with the doors animated
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? DoorShapes.open(state.getValue(FACING)) : Shapes.block();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardOther = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardOther && !(neighbor.getBlock() instanceof ExteriorBlock)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
}
