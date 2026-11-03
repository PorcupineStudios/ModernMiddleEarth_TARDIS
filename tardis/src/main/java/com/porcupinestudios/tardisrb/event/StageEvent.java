package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** The TARDIS entered a new stage. */
public final class StageEvent extends TardisEvent {
    private final Optional<ResourceLocation> from;
    private final ResourceLocation to;

    public StageEvent(TardisRecord tardis, Optional<ResourceLocation> from, ResourceLocation to) {
        super(tardis);
        this.from = from;
        this.to = to;
    }

    public Optional<ResourceLocation> from() {
        return from;
    }

    public ResourceLocation to() {
        return to;
    }
}
