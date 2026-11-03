package com.porcupinestudios.tardisrb.block;

import com.porcupinestudios.tardisrb.repair.Parts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A broken TARDIS part in the console room (a physical repair). Use it with the parts in your inventory to fix it;
 * damage breaks it again. {@code FIXED} only changes how it looks; the record decides.
 */
public final class PartBlock extends Block implements EntityBlock {
    public static final BooleanProperty FIXED = BooleanProperty.create("fixed");

    public PartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FIXED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FIXED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PartBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            sp.displayClientMessage(Parts.use(sl, pos, sp), true);
        }
        return InteractionResult.CONSUME;
    }
}
