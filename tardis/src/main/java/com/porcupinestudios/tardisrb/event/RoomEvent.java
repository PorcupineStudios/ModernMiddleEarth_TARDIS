package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.progression.Cost;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.ICancellableEvent;

/** A room is about to be added ({@link Pre}: cancel it or change its cost) or was added ({@link Post}). */
public abstract class RoomEvent extends TardisEvent {
    private final ResourceLocation room;
    private final int slot;

    protected RoomEvent(TardisRecord tardis, ResourceLocation room, int slot) {
        super(tardis);
        this.room = room;
        this.slot = slot;
    }

    public ResourceLocation room() {
        return room;
    }

    /** 1 is the first slot behind the console room. */
    public int slot() {
        return slot;
    }

    public static final class Pre extends RoomEvent implements ICancellableEvent {
        private Cost cost;

        public Pre(TardisRecord tardis, ResourceLocation room, int slot, Cost cost) {
            super(tardis, room, slot);
            this.cost = cost;
        }

        public Cost cost() {
            return cost;
        }

        public void setCost(Cost cost) {
            this.cost = cost;
        }
    }

    public static final class Post extends RoomEvent {
        public Post(TardisRecord tardis, ResourceLocation room, int slot) {
            super(tardis, room, slot);
        }
    }
}
