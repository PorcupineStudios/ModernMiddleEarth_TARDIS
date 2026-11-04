package com.porcupinestudios.tardisrb.block;

import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.net.WorkshopNet;
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

/**
 * The Repair Station (Aaron, 2026-11-03; no longer built into the interior since the basement went, 2026-11-04): the crew open the repair and upgrade
 * tree here. Repairs are paid and done from that screen; there are no physical part blocks.
 */
public final class RepairStationBlock extends Block {
    public RepairStationBlock(Properties properties) {
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
                sp.displayClientMessage(Component.translatableWithFallback("tardis_rb.repair_station.dead",
                        "The station's screens stay dark"), true);
            } else {
                WorkshopNet.open(sp, tardis.get());
            }
        }
        return InteractionResult.CONSUME;
    }
}
