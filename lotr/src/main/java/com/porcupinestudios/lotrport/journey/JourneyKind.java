package com.porcupinestudios.lotrport.journey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * What a kind of journey does at its ends. Caravans ({@code lotr_port:caravan}) unload and go home; armies
 * ({@code lotr_port:army}) belong to their war. Another mod or system registers its own: a messenger, a migration, a
 * trade expedition. Everything else (routes, speed, bodies, the map) is the same for all.
 */
public interface JourneyKind {
    Map<ResourceLocation, JourneyKind> KINDS = new LinkedHashMap<>();

    static void register(ResourceLocation id, JourneyKind kind) {
        KINDS.put(id, kind);
    }

    static Optional<JourneyKind> of(ResourceLocation id) {
        return Optional.ofNullable(KINDS.get(id));
    }

    /** Its route is ready and it set out. */
    default void planned(MinecraftServer server, Journey j) {}

    /** No way could be found: it never sets out. Give back what it took. */
    void unreachable(MinecraftServer server, Journey j);

    /**
     * It reached the end of its route. Set {@code j.stage} to {@link Journey.Stage#RETURNING} with a route back to
     * go on, or {@link Journey.Stage#DONE} when it is over.
     */
    void arrived(MinecraftServer server, Journey j);

    /** Everyone in it died or left: it is over, and what it carried is lost (or dropped where it was). */
    default void lost(MinecraftServer server, Journey j) {}

    /** What the map and ledgers call it. */
    Component label(MinecraftServer server, Journey j);

    /** 0 a caravan's icon, 1 an army's: the map draws by this. */
    default int icon() {
        return 0;
    }
}
