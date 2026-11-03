package com.porcupinestudios.tardisrb.research;

import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.event.ResearchEvent;
import com.porcupinestudios.tardisrb.progression.Progression;
import com.porcupinestudios.tardisrb.progression.Research;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import com.porcupinestudios.tardisrb.repair.Repairs;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Research per TARDIS (shared by its crew): one node at a time, paid when it starts, finished after its {@code time}
 * in game ticks. Colonist researchers (LOTR port) will start nodes through the same {@link #start}.
 */
public final class Researching {
    public enum State { DONE, RUNNING, AVAILABLE, LOCKED }

    private Researching() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % 20 == 0) {
                tick(e.getServer());
            }
        });
    }

    /** Finishes every job whose time is up. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (TardisRecord t : TardisManager.get(server).all()) {
            t.researching().filter(j -> now >= j.finishAt()).ifPresent(j -> finish(server, t, j.node()));
        }
    }

    public static void finish(MinecraftServer server, TardisRecord tardis, ResourceLocation node) {
        tardis.completeResearch(node);
        Progression.update(server, tardis);
    }

    public static State state(MinecraftServer server, TardisRecord tardis, ResourceLocation node) {
        if (tardis.research().contains(node)) {
            return State.DONE;
        }
        if (tardis.researching().map(j -> j.node().equals(node)).orElse(false)) {
            return State.RUNNING;
        }
        return blocked(server, tardis, node, Optional.empty()).isEmpty() ? State.AVAILABLE : State.LOCKED;
    }

    /** Why this node can't start now (ignoring cost), or empty. */
    public static Optional<Component> blocked(MinecraftServer server, TardisRecord tardis, ResourceLocation id,
            Optional<ServerPlayer> player) {
        Research r = TardisContent.get().research().get(id);
        if (r == null) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.research.unknown",
                    "There is no research %s", id.toString()));
        }
        if (tardis.research().contains(id)) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.research.done", "Already researched"));
        }
        if (tardis.researching().isPresent()) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.research.busy",
                    "Other research is under way"));
        }
        for (ResourceLocation parent : r.parents()) {
            if (!tardis.research().contains(parent)) {
                return Optional.of(Component.translatableWithFallback("tardis_rb.research.parent",
                        "Research %s first", parent.toString()));
            }
        }
        if (r.requirements().isPresent() && !r.requirements().get().test(Unlocks.context(server, tardis, player))) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.research.requirements",
                    "Something else has to come first"));
        }
        return Optional.empty();
    }

    /** Starts a node, paid by the player (items; none for creative or no player) and the TARDIS (charge). */
    public static Repairs.Outcome start(MinecraftServer server, TardisRecord tardis, ResourceLocation id,
            Optional<ServerPlayer> player) {
        Optional<Component> blocked = blocked(server, tardis, id, player);
        if (blocked.isPresent()) {
            return new Repairs.Outcome(blocked);
        }
        Research r = TardisContent.get().research().get(id);
        ResearchEvent.Pre pre = NeoForge.EVENT_BUS.post(new ResearchEvent.Pre(tardis, id,
                TardisContent.get().cost(r.cost()), r.time()));
        if (pre.isCanceled()) {
            return new Repairs.Outcome(Optional.of(Component.translatableWithFallback("tardis_rb.research.cancelled",
                    "The research was stopped")));
        }
        Optional<Component> unpaid = Repairs.pay(tardis, pre.cost(), player);
        if (unpaid.isPresent()) {
            return new Repairs.Outcome(unpaid);
        }
        long now = server.overworld().getGameTime();
        tardis.startResearch(new TardisRecord.ResearchJob(id, now + Math.max(0, pre.time())));
        if (pre.time() <= 0) {
            finish(server, tardis, id);
        }
        return new Repairs.Outcome(Optional.empty());
    }
}
