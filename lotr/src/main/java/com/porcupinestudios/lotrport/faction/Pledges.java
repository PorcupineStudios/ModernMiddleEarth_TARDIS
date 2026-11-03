package com.porcupinestudios.lotrport.faction;

import com.porcupinestudios.lotrport.registry.LotrRegistries;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pledging: a player with enough alignment swears to a faction. A player who rules a realm swears the realm too, so
 * the realm becomes that faction's vassal (the same link RingRealms' vassal charters use).
 */
public final class Pledges {
    public enum Result { PLEDGED, UNKNOWN_FACTION, CANNOT_PLEDGE, NOT_ENOUGH_ALIGNMENT }

    private Pledges() {}

    public static Optional<ResourceLocation> pledge(ServerPlayer player) {
        return player.getData(LotrRegistries.PLAYER_DATA).pledge();
    }

    public static Result pledge(ServerPlayer player, ResourceLocation factionId) {
        MinecraftServer server = player.server;
        Optional<Faction> faction = Factions.get(server, factionId);
        if (faction.isEmpty()) {
            return Result.UNKNOWN_FACTION;
        }
        Optional<Float> needed = faction.get().pledgeAlignment();
        if (needed.isEmpty() || !faction.get().playable()) {
            return Result.CANNOT_PLEDGE;
        }
        if (Alignment.get(player, factionId) < needed.get()) {
            return Result.NOT_ENOUGH_ALIGNMENT;
        }
        player.setData(LotrRegistries.PLAYER_DATA,
                player.getData(LotrRegistries.PLAYER_DATA).withPledge(Optional.of(factionId)));
        Realms.get(server).ownedBy(player.getUUID())
                .ifPresent(realm -> Diplomacy.get(server).setOverlord(server, realm.id(), factionId));
        return Result.PLEDGED;
    }

    public static void unpledge(ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<ResourceLocation> old = pledge(player);
        player.setData(LotrRegistries.PLAYER_DATA,
                player.getData(LotrRegistries.PLAYER_DATA).withPledge(Optional.empty()));
        Realms.get(server).ownedBy(player.getUUID()).ifPresent(realm -> {
            if (old.isPresent() && Diplomacy.get(server).overlord(realm.id()).equals(old)) {
                Diplomacy.get(server).setOverlord(server, realm.id(), null);
            }
        });
    }
}
