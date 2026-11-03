package com.porcupinestudios.tardisrb.event;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.progression.Cost;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.ICancellableEvent;

/** A research node starts ({@link Pre}: cancel it, change its cost or time) or finishes ({@link Post}). */
public abstract class ResearchEvent extends TardisEvent {
    private final ResourceLocation node;

    protected ResearchEvent(TardisRecord tardis, ResourceLocation node) {
        super(tardis);
        this.node = node;
    }

    public ResourceLocation node() {
        return node;
    }

    public static final class Pre extends ResearchEvent implements ICancellableEvent {
        private Cost cost;
        private int time;

        public Pre(TardisRecord tardis, ResourceLocation node, Cost cost, int time) {
            super(tardis, node);
            this.cost = cost;
            this.time = time;
        }

        public Cost cost() {
            return cost;
        }

        public void setCost(Cost cost) {
            this.cost = cost;
        }

        public int time() {
            return time;
        }

        public void setTime(int time) {
            this.time = Math.max(0, time);
        }
    }

    public static final class Post extends ResearchEvent {
        public Post(TardisRecord tardis, ResourceLocation node) {
            super(tardis, node);
        }
    }
}
