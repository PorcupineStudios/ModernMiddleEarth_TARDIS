package com.porcupinestudios.lotrport.faction;

import com.porcupinestudios.lotrport.registry.LotrRegistries;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Kills change alignment. Killing a member of a faction costs alignment with that faction and its allies and earns
 * it with its enemies, by the faction's {@code kill_value}. Members are marked with the {@code lotr_port:faction}
 * attachment (NPCs set it when they spawn).
 */
public final class FactionKills {
    private FactionKills() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FactionKills::onDeath);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            killed(player, event.getEntity());
        }
    }

    /** The faction an entity belongs to, if any. */
    public static Optional<ResourceLocation> factionOf(Entity entity) {
        String id = entity.getData(LotrRegistries.ENTITY_FACTION);
        return id.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(id));
    }

    public static void setFaction(Entity entity, ResourceLocation faction) {
        entity.setData(LotrRegistries.ENTITY_FACTION, faction == null ? "" : faction.toString());
    }

    /** Applies the alignment changes for {@code player} killing {@code victim}. */
    public static void killed(ServerPlayer player, LivingEntity victim) {
        Optional<ResourceLocation> victimFaction = factionOf(victim);
        if (victimFaction.isEmpty()) {
            return;
        }
        MinecraftServer server = player.server;
        Optional<Faction> killedFaction = Factions.get(server, victimFaction.get());
        if (killedFaction.isEmpty()) {
            return;
        }
        float value = killedFaction.get().killValue();
        for (Faction other : Factions.all(server)) {
            if (!other.playable() || other.fixedAlignment().isPresent()) {
                continue;
            }
            Relation relation = Factions.relation(server, killedFaction.get().id(), other.id());
            if (relation == Relation.ALLY) {
                Alignment.add(player, other.id(), -value);
            } else if (relation.isBad()) {
                Alignment.add(player, other.id(), value);
            }
        }
    }
}
