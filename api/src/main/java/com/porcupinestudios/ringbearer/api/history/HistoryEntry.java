package com.porcupinestudios.ringbearer.api.history;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * One thing that happened in the world.
 *
 * @param gameTime overworld game time when it happened
 * @param type what happened, e.g. {@link HistoryTypes#BLOCK_PLACED}; mods add their own ids
 * @param dimension where it happened
 * @param pos the block it happened at, if any
 * @param data anything else the type needs to rebuild or show it
 */
public record HistoryEntry(long gameTime, ResourceLocation type, ResourceKey<Level> dimension, Optional<BlockPos> pos,
        CompoundTag data) {
    public static final Codec<HistoryEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("time").forGetter(HistoryEntry::gameTime),
            ResourceLocation.CODEC.fieldOf("type").forGetter(HistoryEntry::type),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(HistoryEntry::dimension),
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(HistoryEntry::pos),
            CompoundTag.CODEC.optionalFieldOf("data", new CompoundTag()).forGetter(HistoryEntry::data))
            .apply(i, HistoryEntry::new));
}
