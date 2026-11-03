package com.porcupinestudios.tardisrb.block;

import com.porcupinestudios.tardisrb.interior.Interiors;
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

/** The inside of the police box doors: walks the player out to wherever the exterior stands. */
public final class InteriorDoorBlock extends Block {
    public InteriorDoorBlock(Properties properties) {
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
            if (tardis.isEmpty() || !Interiors.exit(sp, tardis.get())) {
                sp.displayClientMessage(Component.translatableWithFallback("tardis_rb.door.stuck",
                        "The doors won't open: the TARDIS is between places"), true);
            }
        }
        return InteractionResult.CONSUME;
    }
}
