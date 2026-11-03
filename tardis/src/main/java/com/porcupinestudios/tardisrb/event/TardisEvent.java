package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import net.neoforged.bus.api.Event;

/** Base of every TARDIS event; all fire on {@code NeoForge.EVENT_BUS}. */
public abstract class TardisEvent extends Event {
    private final TardisRecord tardis;

    protected TardisEvent(TardisRecord tardis) {
        this.tardis = tardis;
    }

    public TardisRecord tardis() {
        return tardis;
    }
}
