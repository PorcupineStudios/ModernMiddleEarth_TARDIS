package com.porcupinestudios.tardisrb.gravity.gc;

import net.minecraft.core.Direction;

/** Added to every entity by {@code mixin.EntityGravityDataMixin}: its gravity direction, kept in a plain field. */
public interface GravityHolder {
    Direction tardis_rb$gravity();

    void tardis_rb$setGravityField(Direction direction);

    /** Client only: the turn animation when gravity changes (null on the server). */
    RotationAnimation tardis_rb$animation();
}
