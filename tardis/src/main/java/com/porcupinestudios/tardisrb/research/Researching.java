package com.porcupinestudios.tardisrb.research;

import com.porcupinestudios.ringbearer.api.provider.Providers;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.event.ResearchEvent;
import com.porcupinestudios.tardisrb.progression.Progression;
import com.porcupinestudios.tardisrb.progression.Research;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import com.porcupinestudios.tardisrb.repair.Repairs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Research per TARDIS (shared by its crew): one node at a time, paid when it starts, finished after its {@code time}
 * in game ticks. Colonist researchers (any mod, through the shared API) speed up the running node.
 */
public final class Researching {
    /** Ticks between checks. */
    private static final int INTERVAL = 20;

    public enum State { DONE, RUNNING, AVAILABLE, LOCKED }

    private Researching() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % INTERVAL == 0) {
                tick(e.getServer());
            }
        });
    }

    /** Finishes every job whose time is up. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (TardisRecord t : TardisManager.get(server).all()) {
            if (t.researching().isPresent()) {
                t.hurryResearch(bonusTicks(researchers(server, t), INTERVAL));
            }
            t.researching().filter(j -> now >= j.finishAt()).ifPresent(j -> finish(server, t, j.node()));
        }
    }

    /** Researchers working for the TARDIS's owner and crew (colonists, through the shared API), capped by tuning. */
    public static int researchers(MinecraftServer server, TardisRecord t) {
        List<UUID> crew = new ArrayList<>(t.crew());
        t.owner().ifPresent(crew::add);
        return Math.min(Providers.researchers(server, crew), TardisContent.tuning().research().maxResearchers());
    }

    /** Extra ticks of progress that {@code researchers} add over {@code ticks} ticks. */
    public static long bonusTicks(int researchers, long ticks) {
        Tuning.ResearchSpeed r = TardisContent.tuning().research();
        int counted = Math.max(0, Math.min(researchers, r.maxResearchers()));
        return Math.round(ticks * counted * r.speedPerResearcher());
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
