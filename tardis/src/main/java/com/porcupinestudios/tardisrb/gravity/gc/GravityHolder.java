package com.porcupinestudios.tardisrb.gravity.gc;

import net.minecraft.core.Direction;

/** Added to every entity by {@code mixin.EntityGravityDataMixin}: its gravity direction, kept in a plain field. */
public interface GravityHolder {
    Direction tardis_rb$gravity();

    void tardis_rb$setGravityField(Direction direction);

    /** Client only: the turn animation when gravity changes (null on the server). */
    RotationAnimation tardis_rb$animation();

    /** The free lean while floating in a solar system, or null ({@code gravity.Tilt}). */
    com.porcupinestudios.tardisrb.gravity.Tilt tardis_rb$tilt();

    void tardis_rb$setTilt(com.porcupinestudios.tardisrb.gravity.Tilt tilt);
}
