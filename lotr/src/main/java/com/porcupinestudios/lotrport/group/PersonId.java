package com.porcupinestudios.lotrport.group;

import java.util.UUID;

/**
 * The id of a person: for now the UUID of the NPC entity that is their body, which is also the key of their record in
 * {@code npc.People}. Kept as its own type so a person can later outlive or change bodies without touching callers.
 */
public record PersonId(UUID value) {
    public static PersonId of(UUID value) {
        return new PersonId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
