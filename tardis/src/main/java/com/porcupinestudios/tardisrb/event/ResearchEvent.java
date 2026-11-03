package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import net.minecraft.resources.ResourceLocation;

/** A research node finished ({@link Post}). The research screen (phase 3) adds a cancellable start event. */
public abstract class ResearchEvent extends TardisEvent {
    private final ResourceLocation node;

    protected ResearchEvent(TardisRecord tardis, ResourceLocation node) {
        super(tardis);
        this.node = node;
    }

    public ResourceLocation node() {
        return node;
    }

    public static final class Post extends ResearchEvent {
        public Post(TardisRecord tardis, ResourceLocation node) {
            super(tardis, node);
        }
    }
}
