package com.porcupinestudios.tardisrb.block;

import com.porcupinestudios.tardisrb.door.DoorLock;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The inside of the police box doors: anyone inside opens or closes them unless they are locked (see-through
 * portals, {@link Doors}); sneak-use cycles the lock ({@link DoorLock}).
 */
public final class InteriorDoorBlock extends Block {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public InteriorDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, OPEN);
    }

    /** Open, the doorway faces the console room ({@code door.interior_facing}). */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? DoorShapes.open(TardisContent.tuning().door().interiorFacing()) : Shapes.block();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            var tardis = Interiors.at(sl, pos);
            if (tardis.isEmpty()) {
                return InteractionResult.CONSUME;
            }
            if (sp.isShiftKeyDown()) {
                // sneak-use cycles the lock, as in the original mod (Aaron, 2026-11-04)
                DoorLock.cycle(tardis.get(), sp, true);
                return InteractionResult.CONSUME;
            }
            if (!DoorLock.mayUse(tardis.get(), sp, true)) {
                sp.displayClientMessage(Component.translatableWithFallback("tardis_rb.exterior.locked",
                        "The door is locked"), true);
                return InteractionResult.CONSUME;
            }
            boolean closing = Doors.isOpen(tardis.get()) && !Doors.isClosing(tardis.get());
            if (Doors.toggle(sl.getServer(), tardis.get())) {
                sp.displayClientMessage(Doors.message(closing), true);
            } else {
                sp.displayClientMessage(Component.translatableWithFallback("tardis_rb.door.stuck",
                        "The doors won't open: the TARDIS is between places"), true);
            }
        }
        return InteractionResult.CONSUME;
    }
}
