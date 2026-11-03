package com.porcupinestudios.ringbearer.api.history;

import com.porcupinestudios.ringbearer.api.ApiConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Writes block changes made by entities (players, and later colonists and NPCs) to the {@link HistoryLog}. Runs last,
 * so a change another mod cancels is never recorded.
 */
public final class HistoryRecorder {
    private HistoryRecorder() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(EventPriority.LOWEST, HistoryRecorder::onPlace);
        gameBus.addListener(EventPriority.LOWEST, HistoryRecorder::onBreak);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        record(level, HistoryTypes.BLOCK_PLACED, event.getPos(), event.getPlacedBlock(), event.getEntity());
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        record(level, HistoryTypes.BLOCK_BROKEN, event.getPos(), event.getState(), event.getPlayer());
    }

    private static void record(ServerLevel level, ResourceLocation type, BlockPos pos, BlockState state, Entity actor) {
        if (!ApiConfig.RECORD_BLOCK_CHANGES.get()) {
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putString("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (actor != null) {
            data.putUUID("actor", actor.getUUID());
        }
        long time = level.getServer().overworld().getGameTime();
        HistoryLog.get(level.getServer())
                .append(new HistoryEntry(time, type, level.dimension(), Optional.of(pos.immutable()), data));
    }
}
