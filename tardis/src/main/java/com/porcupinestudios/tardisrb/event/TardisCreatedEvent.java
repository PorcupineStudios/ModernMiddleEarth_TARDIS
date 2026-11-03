package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;

/** A TARDIS record was made (by a crash site, a command or another mod). */
public final class TardisCreatedEvent extends TardisEvent {
    public TardisCreatedEvent(TardisRecord tardis) {
        super(tardis);
    }
}
