package com.porcupinestudios.lotrport.social;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.society.ComponentType;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.util.StringRepresentable;

/** Who sees a colony's icon on the map (a colony component; set by members with the {@code map} permission). */
public enum MapPresence implements StringRepresentable {
    /** Everyone. */
    PUBLIC,
    /** Only the colony's members. */
    MEMBERS,
    /** Nobody but its members, and it is drawn faded for them. */
    HIDDEN;

    public static final Codec<MapPresence> CODEC = StringRepresentable.fromEnum(MapPresence::values);
    public static final ComponentType<MapPresence> TYPE = ComponentType.register(LotrPort.id("map_presence"), CODEC);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MapPresence of(Colony colony) {
        return colony.get(TYPE).orElse(PUBLIC);
    }

    public static boolean visible(Colony colony, UUID viewer) {
        return of(colony) == PUBLIC || Permissions.isMember(viewer, colony);
    }

    public MapPresence next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Loads the class (registers the component) from the mod constructor. */
    public static void init() {}
}
