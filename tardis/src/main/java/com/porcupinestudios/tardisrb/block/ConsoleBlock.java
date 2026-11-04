package com.porcupinestudios.tardisrb.block;

import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.net.ConsoleNet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The console (Aaron, 2026-11-03: like the original mod's, a custom model with buttons, levers and dials; 2026-11-04 the
 * Capaldi console). The block is the middle of the console; the model, its controls, the time rotor and the room's
 * arches are drawn by the client renderer, controls are clicked through {@code client/ConsoleInput}, and invisible
 * {@link ConsoleHullBlock}s round it give the rest of the body its collision. Clicking the block opens the flight
 * screen. It belongs to the TARDIS whose
 * interior plot it stands in.
 */
public final class ConsoleBlock extends Block implements EntityBlock {
    /** The middle of the console's body; the cells round it are {@link ConsoleHullBlock}s. */
    private static final VoxelShape BODY = ConsoleHullBlock.shape(0, 0);

    public ConsoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConsoleBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide || type != TardisBlocks.CONSOLE_BE.get() ? null
                : (BlockEntityTicker<T>) (BlockEntityTicker<ConsoleBlockEntity>) ConsoleBlockEntity::serverTick;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BODY;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            var tardis = Interiors.at(sl, pos);
            if (tardis.isEmpty() || !tardis.get().isCrew(sp.getUUID())) {
                sp.displayClientMessage(Component.translatableWithFallback("tardis_rb.console.dead",
                        "The console stays dark"), true);
            } else {
                ConsoleNet.open(sp, tardis.get());
            }
        }
        return InteractionResult.CONSUME;
    }
}
