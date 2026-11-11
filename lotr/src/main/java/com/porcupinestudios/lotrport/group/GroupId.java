package com.porcupinestudios.lotrport.group;

import java.util.UUID;

/** The id of a {@link Group}. Other systems hold this, never the group object. A colony's group has the colony's id. */
public record GroupId(UUID value) {
    public static GroupId of(UUID value) {
        return new GroupId(value);
    }

    public static GroupId random() {
        return new GroupId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
