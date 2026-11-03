package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import net.minecraft.resources.ResourceLocation;

/** The TARDIS passed a milestone. */
public final class MilestoneEvent extends TardisEvent {
    private final ResourceLocation milestone;

    public MilestoneEvent(TardisRecord tardis, ResourceLocation milestone) {
        super(tardis);
        this.milestone = milestone;
    }

    public ResourceLocation milestone() {
        return milestone;
    }
}
