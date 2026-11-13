package com.porcupinestudios.lotrport.colony.members;

import java.util.List;

/**
 * The permission names roles grant ({@code colony_role/*.json} {@code permissions}). Strings, so a pack or addon can
 * add its own and check them with {@link Permissions#can}; these are the ones this mod checks.
 */
public final class Perm {
    /** Break and place blocks on the colony's land. */
    public static final String BUILD = "build";
    /** Open chests, furnaces and other containers on its land. */
    public static final String CONTAINERS = "containers";
    /** Use doors, gates, buttons and levers on its land. */
    public static final String DOORS = "doors";
    /** Command the colony's soldiers (orders, squads). */
    public static final String COMMAND = "command";
    /** Station your own hired units in the colony (it pays them). */
    public static final String STATION = "station";
    /** Accept or turn away migrants. */
    public static final String MIGRANTS = "migrants";
    /** Add and remove members and set roles below your own. */
    public static final String MEMBERS = "members";
    /** Place and take down the colony's banners (its land). */
    public static final String BANNERS = "banners";
    /** Pay coins out of the treasury. */
    public static final String TREASURY = "treasury";
    /** Declare war and make peace for the colony. */
    public static final String WAR = "war";
    /** Choose who sees the colony on the map. */
    public static final String MAP = "map";
    /** Rename citizens and give them orders: where to work and live, work or rest, their ration. */
    public static final String CITIZENS = "citizens";

    /** Send caravans, set up routes, trade offers and floors. */
    public static final String CARAVANS = "caravans";

    /** Proclaim events at the Hall: weddings, festivals, feasts (they cost the stores). */
    public static final String EVENTS = "events";

    public static final List<String> ALL = List.of(BUILD, CONTAINERS, DOORS, COMMAND, STATION, MIGRANTS, MEMBERS,
            BANNERS, TREASURY, WAR, MAP, CITIZENS, CARAVANS,
            EVENTS);

    private Perm() {}
}
