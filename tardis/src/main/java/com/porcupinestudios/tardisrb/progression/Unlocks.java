package com.porcupinestudios.tardisrb.progression;

import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.tardisrb.Config;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.event.UnlockCheckEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The one answer to "may this TARDIS do that?". {@link #check} decides features, {@link #level} gives a subsystem's
 * level after damage and milestone caps, and {@link #stat} adds up a named number (range, charge cap, room cap) over
 * all subsystems at their current levels.
 */
public final class Unlocks {
    /** Allowed or not, with the reason shown to the player. */
    public record Result(boolean allowed, Component reason) {
        static Result yes() {
            return new Result(true, Component.empty());
        }

        static Result no(String key, String fallback, Object... args) {
            return new Result(false, Component.translatableWithFallback(key, fallback, args));
        }
    }

    private record Cached(int revision, int generation, Result result) {}

    /** Player-free checks by TARDIS and feature; dropped when the record changes or data reloads. */
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private Unlocks() {}

    /** A subsystem's level that counts: repaired level minus damage, no higher than the current milestone cap. */
    public static int level(TardisRecord tardis, ResourceLocation subsystem) {
        int level = Math.max(0, tardis.repairedLevel(subsystem) - tardis.damage(subsystem));
        return Math.min(level, cap(tardis, subsystem));
    }

    /**
     * The highest level a subsystem may reach now: the cap from the passed milestone that sets the highest one, or the
     * subsystem's {@code start_cap} when no passed milestone sets one.
     */
    public static int cap(TardisRecord tardis, ResourceLocation subsystem) {
        TardisContent.Snapshot data = TardisContent.get();
        Subsystem sub = data.subsystems().get(subsystem);
        int cap = sub == null ? 0 : sub.baseCap();
        for (ResourceLocation id : tardis.milestones()) {
            Milestone m = data.milestones().get(id);
            if (m != null && m.caps().containsKey(subsystem)) {
                cap = Math.max(cap, m.caps().get(subsystem));
            }
        }
        return sub == null ? cap : Math.min(cap, sub.maxLevel());
    }

    /**
     * A named number such as {@code range}: added up over every subsystem at its current level, plus the bonuses of
     * finished research.
     */
    public static double stat(TardisRecord tardis, String key) {
        TardisContent.Snapshot data = TardisContent.get();
        double total = 0;
        for (Map.Entry<ResourceLocation, Subsystem> e : data.subsystems().entrySet()) {
            total += e.getValue().value(level(tardis, e.getKey()), key);
        }
        for (ResourceLocation node : tardis.research()) {
            Research r = data.research().get(node);
            if (r != null) {
                total += r.stats().getOrDefault(key, 0.0);
            }
        }
        return total;
    }

    public static long chargeCap(TardisRecord tardis) {
        return (long) stat(tardis, "charge_cap");
    }

    /** The condition context for questions about this TARDIS, optionally asked by a player. */
    public static ConditionContext context(MinecraftServer server, TardisRecord tardis,
            Optional<ServerPlayer> player) {
        return new ConditionContext(player.map(ServerPlayer::serverLevel).orElse(server.overworld()), player,
                Map.of(TardisRB.MOD_ID, tardis));
    }

    /**
     * Whether the TARDIS may use a feature. Order: the feature must exist in data; the server config's forced lists
     * win; then the file's {@code enabled} flag and requirements; finally {@link UnlockCheckEvent} listeners.
     */
    public static Result check(MinecraftServer server, TardisRecord tardis, ResourceLocation featureId,
            Optional<ServerPlayer> player) {
        int generation = TardisContent.get().generation();
        String key = tardis.id() + "|" + featureId;
        if (player.isEmpty()) {
            Cached hit = CACHE.get(key);
            if (hit != null && hit.revision == tardis.revision() && hit.generation == generation) {
                return hit.result;
            }
        }
        Result result = decide(server, tardis, featureId, player);
        UnlockCheckEvent event = NeoForge.EVENT_BUS.post(
                new UnlockCheckEvent(tardis, featureId, result.allowed(), result.reason()));
        result = new Result(event.allowed(), event.reason());
        if (player.isEmpty()) {
            CACHE.put(key, new Cached(tardis.revision(), generation, result));
        }
        return result;
    }

    private static Result decide(MinecraftServer server, TardisRecord tardis, ResourceLocation featureId,
            Optional<ServerPlayer> player) {
        Optional<Feature> feature = TardisContent.feature(featureId);
        if (feature.isEmpty()) {
            return Result.no("tardis_rb.unlock.unknown", "%s isn't part of this pack", featureId.toString());
        }
        String id = featureId.toString();
        if (forced(Config.FORCED_OFF.get(), id)) {
            return Result.no("tardis_rb.unlock.forced_off", "%s is switched off on this server", id);
        }
        if (forced(Config.FORCED_ON.get(), id)) {
            return Result.yes();
        }
        if (!feature.get().enabled()) {
            return Result.no("tardis_rb.unlock.disabled", "%s is switched off in this pack", id);
        }
        boolean met = feature.get().requirements().map(c -> c.test(context(server, tardis, player))).orElse(true);
        return met ? Result.yes() : Result.no("tardis_rb.unlock.locked", "The TARDIS can't do that yet (%s)", id);
    }

    private static boolean forced(List<? extends String> list, String id) {
        return list.contains(id);
    }
}
