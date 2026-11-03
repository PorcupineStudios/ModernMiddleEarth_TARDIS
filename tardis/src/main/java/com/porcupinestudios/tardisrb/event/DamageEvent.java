package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.ICancellableEvent;

/** A subsystem is about to lose levels. Cancel it, or change how many. */
public final class DamageEvent extends TardisEvent implements ICancellableEvent {
    private final ResourceLocation subsystem;
    private int amount;

    public DamageEvent(TardisRecord tardis, ResourceLocation subsystem, int amount) {
        super(tardis);
        this.subsystem = subsystem;
        this.amount = amount;
    }

    public ResourceLocation subsystem() {
        return subsystem;
    }

    public int amount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }
}
