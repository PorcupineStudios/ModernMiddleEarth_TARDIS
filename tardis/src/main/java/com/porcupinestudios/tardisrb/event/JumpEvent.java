package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import net.minecraft.core.GlobalPos;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * A jump, in order: {@link Plan} (cancel or change the target), {@link Cost} (change the charge), {@link Land} (change
 * the landing spot), {@link Arrived}.
 */
public abstract class JumpEvent extends TardisEvent {
    private final GlobalPos from;

    protected JumpEvent(TardisRecord tardis, GlobalPos from) {
        super(tardis);
        this.from = from;
    }

    public GlobalPos from() {
        return from;
    }

    public static final class Plan extends JumpEvent implements ICancellableEvent {
        private GlobalPos target;

        public Plan(TardisRecord tardis, GlobalPos from, GlobalPos target) {
            super(tardis, from);
            this.target = target;
        }

        public GlobalPos target() {
            return target;
        }

        public void setTarget(GlobalPos target) {
            this.target = target;
        }
    }

    public static final class Cost extends JumpEvent {
        private final GlobalPos target;
        private long charge;

        public Cost(TardisRecord tardis, GlobalPos from, GlobalPos target, long charge) {
            super(tardis, from);
            this.target = target;
            this.charge = charge;
        }

        public GlobalPos target() {
            return target;
        }

        public long charge() {
            return charge;
        }

        public void setCharge(long charge) {
            this.charge = Math.max(0, charge);
        }
    }

    public static final class Land extends JumpEvent {
        private GlobalPos spot;

        public Land(TardisRecord tardis, GlobalPos from, GlobalPos spot) {
            super(tardis, from);
            this.spot = spot;
        }

        public GlobalPos spot() {
            return spot;
        }

        public void setSpot(GlobalPos spot) {
            this.spot = spot;
        }
    }

    public static final class Arrived extends JumpEvent {
        private final GlobalPos to;

        public Arrived(TardisRecord tardis, GlobalPos from, GlobalPos to) {
            super(tardis, from);
            this.to = to;
        }

        public GlobalPos to() {
            return to;
        }
    }
}
