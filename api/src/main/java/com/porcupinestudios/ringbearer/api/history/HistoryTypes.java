package com.porcupinestudios.ringbearer.api.history;

import com.porcupinestudios.ringbearer.api.RingBearerApi;
import net.minecraft.resources.ResourceLocation;

/** History entry types the shared API records itself. Each mod adds its own under its own namespace. */
public final class HistoryTypes {
    /** data: {@code block} (block id), {@code actor} (entity UUID, if any) */
    public static final ResourceLocation BLOCK_PLACED = RingBearerApi.id("block_placed");
    /** data: {@code block} (block id that was there), {@code actor} (entity UUID, if any) */
    public static final ResourceLocation BLOCK_BROKEN = RingBearerApi.id("block_broken");

    private HistoryTypes() {}
}
