package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A feature was checked; listeners may change the answer and the reason shown to the player. */
public final class UnlockCheckEvent extends TardisEvent {
    private final ResourceLocation feature;
    private boolean allowed;
    private Component reason;

    public UnlockCheckEvent(TardisRecord tardis, ResourceLocation feature, boolean allowed, Component reason) {
        super(tardis);
        this.feature = feature;
        this.allowed = allowed;
        this.reason = reason;
    }

    public ResourceLocation feature() {
        return feature;
    }

    public boolean allowed() {
        return allowed;
    }

    public Component reason() {
        return reason;
    }

    public void setResult(boolean allowed, Component reason) {
        this.allowed = allowed;
        this.reason = reason;
    }
}
