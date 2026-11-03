package com.porcupinestudios.tardisrb.block;

import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.net.ConsoleNet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The console: the crew open the flight screen here. It belongs to the TARDIS whose interior plot it stands in. */
public final class ConsoleBlock extends Block {
    public ConsoleBlock(Properties properties) {
        super(properties);
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
