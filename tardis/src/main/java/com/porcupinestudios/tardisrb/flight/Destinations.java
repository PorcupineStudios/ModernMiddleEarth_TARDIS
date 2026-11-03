package com.porcupinestudios.tardisrb.flight;

import com.porcupinestudios.ringbearer.api.provider.Providers;
import com.porcupinestudios.tardisrb.Config;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.progression.Destination;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Which destinations a TARDIS may jump to. Order: the destination must exist in data; the server config's forced
 * lists (by destination id) win; the start dimension is always open; otherwise its {@code requirements} decide
 * (none = open); last, its dimension must exist in this world.
 */
public final class Destinations {
    private Destinations() {}

    /** Why this destination is closed, or empty when it is open. */
    public static Optional<Component> closed(MinecraftServer server, TardisRecord tardis, ResourceLocation id,
            Optional<ServerPlayer> player) {
        return closed(server, tardis, id, player, Providers.startDimension(server));
    }

    /** As {@link #closed(MinecraftServer, TardisRecord, ResourceLocation, Optional)}, with the start dimension given. */
    public static Optional<Component> closed(MinecraftServer server, TardisRecord tardis, ResourceLocation id,
            Optional<ServerPlayer> player, ResourceKey<Level> start) {
        Optional<Destination> found = TardisContent.destination(id);
        if (found.isEmpty()) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.jump.unknown",
                    "There is no destination %s", id.toString()));
        }
        Destination d = found.get();
        if (Config.FORCED_OFF.get().contains(id.toString())) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.unlock.forced_off",
                    "%s is switched off on this server", id.toString()));
        }
        boolean open = Config.FORCED_ON.get().contains(id.toString()) || d.dimension().equals(start)
                || d.requirements().map(c -> c.test(Unlocks.context(server, tardis, player))).orElse(true);
        if (!open) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.jump.locked",
                    "The TARDIS can't reach %s yet", d.dimension().location().toString()));
        }
        if (server.getLevel(d.dimension()) == null) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.jump.missing",
                    "That dimension isn't in this world"));
        }
        return Optional.empty();
    }

    /** Open destinations, sorted by id. */
    public static List<ResourceLocation> open(MinecraftServer server, TardisRecord tardis,
            Optional<ServerPlayer> player) {
        List<ResourceLocation> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Destination> e : new TreeMap<>(TardisContent.get().destinations())
                .entrySet()) {
            if (closed(server, tardis, e.getKey(), player).isEmpty()) {
                out.add(e.getKey());
            }
        }
        return out;
    }
}
