package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.progression.Cost;
import com.porcupinestudios.tardisrb.progression.Repair;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.ICancellableEvent;

/** A repair is attempted ({@link Pre}: cancel it or change its cost) or done ({@link Post}). */
public abstract class RepairEvent extends TardisEvent {
    private final ResourceLocation repairId;
    private final Repair repair;

    protected RepairEvent(TardisRecord tardis, ResourceLocation repairId, Repair repair) {
        super(tardis);
        this.repairId = repairId;
        this.repair = repair;
    }

    public ResourceLocation repairId() {
        return repairId;
    }

    public Repair repair() {
        return repair;
    }

    public static final class Pre extends RepairEvent implements ICancellableEvent {
        private Cost cost;

        public Pre(TardisRecord tardis, ResourceLocation repairId, Repair repair, Cost cost) {
            super(tardis, repairId, repair);
            this.cost = cost;
        }

        public Cost cost() {
            return cost;
        }

        public void setCost(Cost cost) {
            this.cost = cost;
        }
    }

    public static final class Post extends RepairEvent {
        public Post(TardisRecord tardis, ResourceLocation repairId, Repair repair) {
            super(tardis, repairId, repair);
        }
    }
}
