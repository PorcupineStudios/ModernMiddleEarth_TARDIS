package com.porcupinestudios.lotrport.faction;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How two factions stand with each other, from LOTR's five relations. Symmetric: Gondor-Mordor = Mordor-Gondor. */
public enum Relation implements StringRepresentable {
    ALLY("ally"),
    FRIEND("friend"),
    NEUTRAL("neutral"),
    ENEMY("enemy"),
    MORTAL_ENEMY("mortal_enemy");

    public static final Codec<Relation> CODEC = StringRepresentable.fromEnum(Relation::values);

    private final String name;

    Relation(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public boolean isGood() {
        return this == ALLY || this == FRIEND;
    }

    public boolean isBad() {
        return this == ENEMY || this == MORTAL_ENEMY;
    }

    public static Relation byName(String name) {
        for (Relation r : values()) {
            if (r.name.equals(name)) {
                return r;
            }
        }
        return null;
    }
}
