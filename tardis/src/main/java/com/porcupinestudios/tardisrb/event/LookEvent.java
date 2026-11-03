package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;

/** The police box's look (0-3) was worked out from the look rules; listeners may change it. */
public final class LookEvent extends TardisEvent {
    private int look;

    public LookEvent(TardisRecord tardis, int look) {
        super(tardis);
        this.look = look;
    }

    public int look() {
        return look;
    }

    public void setLook(int look) {
        this.look = Math.max(0, Math.min(3, look));
    }
}
