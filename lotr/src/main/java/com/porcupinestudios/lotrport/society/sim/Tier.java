package com.porcupinestudios.lotrport.society.sim;

import java.util.EnumSet;

/** How closely a colony is simulated right now. Systems say which tiers they run in. */
public enum Tier {
    /** A player is near the Hall (within {@code active_radius}): bodies walk, work happens in the world. */
    ACTIVE,
    /** The Hall's chunk is loaded but nobody is near: blocks can be read, no bodies need to move. */
    LOADED,
    /** The Hall's chunk is unloaded: only numbers change (arithmetic over the time that passed). */
    UNLOADED;

    public static final EnumSet<Tier> ALL = EnumSet.allOf(Tier.class);
    public static final EnumSet<Tier> IN_WORLD = EnumSet.of(ACTIVE, LOADED);

    public boolean inWorld() {
        return this != UNLOADED;
    }
}
