package com.porcupinestudios.tardisrb.gravity;

import com.porcupinestudios.tardisrb.progression.Tuning;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Ship decks (space stations are Aeronautics ships, Aaron 2026-11-15): standing on or in a ship, an entity's down is
 * the ship's own down. compat/sable fills {@link #down} when Sable is loaded; otherwise there are no decks.
 */
public final class Decks {
    /** Vanilla's pull, blocks a tick per tick, which {@code space.stations.deck_gravity} scales. */
    public static final double NORMAL = 0.08;

    /** The world direction of "down" on the ship an entity is on or in (unit), or null when it isn't on one. */
    public static volatile Function<Entity, Vec3> down = e -> null;

    private Decks() {}

    /** The deck's pull on an entity (blocks a tick per tick), or null when it isn't on a ship. */
    public static Vec3 pull(Entity e, Tuning.SpaceTuning t) {
        Vec3 d = down.apply(e);
        return d == null ? null : d.scale(NORMAL * t.stations().deckGravity());
    }
}
