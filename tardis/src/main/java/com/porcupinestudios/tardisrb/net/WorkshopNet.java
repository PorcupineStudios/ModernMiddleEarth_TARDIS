package com.porcupinestudios.tardisrb.net;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.progression.Cost;
import com.porcupinestudios.tardisrb.progression.Research;
import com.porcupinestudios.tardisrb.progression.Stage;
import com.porcupinestudios.tardisrb.progression.Subsystem;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import com.porcupinestudios.tardisrb.repair.Repairs;
import com.porcupinestudios.tardisrb.research.Researching;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The repair and upgrade tree (Aaron, 2026-11-03; reworked 2026-11-19): a node per subsystem and per upgrade, with its
 * level / max, what the next level costs and exactly what it changes. Opened at the Repair Station in the
 * Repair Station; works only for crew standing in the TARDIS.
 */
public final class WorkshopNet {
    public static final int REPAIR = 0;
    public static final int RESEARCH = 1;
    public static final int DONE = 0;
    public static final int RUNNING = 1;
    public static final int AVAILABLE = 2;
    public static final int LOCKED = 3;

    public static BiConsumer<WorkshopView, IPayloadContext> clientHandler = (view, ctx) -> { };

    private WorkshopNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(WorkshopNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(WorkshopView.TYPE, WorkshopView.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> clientHandler.accept(p, ctx)));
        r.playToServer(Act.TYPE, Act.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                mine(sp).ifPresent(t -> {
                    Repairs.Outcome out = act(sp, t, p.kind(), p.id());
                    String msg = out.done() ? Component.translatableWithFallback("tardis_rb.workshop.ok", "Done")
                            .getString() : out.problem().get().getString();
                    PacketDistributor.sendToPlayer(sp, view(sp, t, msg));
                });
            }
        }));
    }

    /** Opens the tree for a crew member (the Repair Station). */
    public static void open(ServerPlayer player, TardisRecord tardis) {
        PacketDistributor.sendToPlayer(player, view(player, tardis, ""));
    }

    /** Does what a click on a node asks: the subsystem's next repair or the node's next upgrade level. */
    public static Repairs.Outcome act(ServerPlayer player, TardisRecord t, int kind, ResourceLocation id) {
        return kind == REPAIR ? Repairs.attempt(player.server, t, id, Optional.of(player))
                : Researching.start(player.server, t, id, Optional.of(player));
    }

    private static Optional<TardisRecord> mine(ServerPlayer player) {
        return Interiors.at(player.serverLevel(), player.blockPosition()).filter(t -> t.isCrew(player.getUUID()));
    }

    /**
     * One node per subsystem (its next repair; level / max) and one per research node (its next level), each with
     * what it costs and exactly what buying it changes. Rooms aren't shown (Aaron 2026-11-19: later).
     */
    public static WorkshopView view(ServerPlayer player, TardisRecord t, String message) {
        TardisContent.Snapshot data = TardisContent.get();
        List<Entry> entries = new ArrayList<>();
        Set<ResourceLocation> stageRepairs = new LinkedHashSet<>();
        t.stage().map(data.stages()::get).map(Stage::repairs).ifPresent(stageRepairs::addAll);
        Map<ResourceLocation, Map<Integer, ResourceLocation>> bySub = new TreeMap<>();
        data.repairs().forEach((id, r) -> bySub.computeIfAbsent(r.subsystem(), k -> new TreeMap<>()).put(r.level(), id));
        for (Map.Entry<ResourceLocation, Map<Integer, ResourceLocation>> e : bySub.entrySet()) {
            ResourceLocation sub = e.getKey();
            Subsystem subsystem = data.subsystems().get(sub);
            int max = e.getValue().keySet().stream().max(Integer::compare).orElse(0);
            int cur = Unlocks.level(t, sub);
            ResourceLocation next = e.getValue().get(cur + 1);
            int state;
            List<Change> outcome = new ArrayList<>();
            String cost = "";
            if (cur >= max || next == null) {
                state = DONE;
            } else {
                state = Repairs.blocked(player.server, t, next, Optional.of(player)).isPresent() ? LOCKED : AVAILABLE;
                cost = describe(data.cost(data.repairs().get(next).cost()));
                if (subsystem != null) {
                    Set<String> keys = new TreeSet<>();
                    keys.addAll(subsystem.levels().get(Math.min(cur, subsystem.maxLevel())).keySet());
                    keys.addAll(subsystem.levels().get(Math.min(cur + 1, subsystem.maxLevel())).keySet());
                    for (String key : keys) {
                        double before = Unlocks.stat(t, key);
                        double delta = subsystem.value(cur + 1, key) - subsystem.value(cur, key);
                        if (delta != 0) {
                            outcome.add(new Change(key, before, before + delta));
                        }
                    }
                }
            }
            ResourceLocation id = next != null ? next : e.getValue().get(max);
            entries.add(new Entry(REPAIR, id, sub.getPath(), state, cost, outcome, Math.min(cur, max), max,
                    next != null && stageRepairs.contains(next), List.of()));
        }
        for (Map.Entry<ResourceLocation, Research> e : new TreeMap<>(data.research()).entrySet()) {
            Research r = e.getValue();
            int level = t.researchLevel(e.getKey());
            int state = switch (Researching.state(player.server, t, e.getKey())) {
                case DONE -> DONE;
                case RUNNING -> RUNNING;
                case AVAILABLE -> AVAILABLE;
                case LOCKED -> LOCKED;
            };
            List<Change> outcome = new ArrayList<>();
            String cost = "";
            if (level < r.maxLevel()) {
                Research.Level next = r.level(level + 1);
                cost = describe(data.cost(next.cost()));
                for (Map.Entry<String, Double> st : new TreeMap<>(next.stats()).entrySet()) {
                    double before = Unlocks.stat(t, st.getKey());
                    outcome.add(new Change(st.getKey(), before, before + st.getValue()));
                }
            }
            entries.add(new Entry(RESEARCH, e.getKey(), e.getKey().getPath(), state, cost, outcome, level,
                    r.maxLevel(), false, r.parents()));
        }
        String stage = t.stage().map(ResourceLocation::getPath).orElse("-");
        return new WorkshopView(t.name(), stage, (int) Unlocks.stat(t, "range"), t.charge(), entries, message);
    }

    private static String describe(Cost cost) {
        List<String> parts = new ArrayList<>();
        for (Cost.ItemCost c : cost.items()) {
            ItemStack[] shown = c.ingredient().getItems();
            parts.add(c.count() + " " + (shown.length > 0 ? shown[0].getHoverName().getString() : "?"));
        }
        if (cost.charge() > 0) {
            parts.add(cost.charge() + " charge");
        }
        return parts.isEmpty() ? "free" : String.join(", ", parts);
    }

    /** One thing buying a node changes: a named number (lang {@code tardis_rb.stat.<key>}) from {@code before}. */
    public record Change(String key, double before, double after) {
        public static final StreamCodec<ByteBuf, Change> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Change::key, ByteBufCodecs.DOUBLE, Change::before,
                ByteBufCodecs.DOUBLE, Change::after, Change::new);
    }

    /**
     * One node: a subsystem (its next repair is {@code id}) or a research node. {@code level} of {@code max} are done;
     * {@code cost} and {@code outcome} are for the next level (empty when done).
     */
    public record Entry(int kind, ResourceLocation id, String title, int state, String cost, List<Change> outcome,
            int level, int max, boolean stage, List<ResourceLocation> parents) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = new StreamCodec<>() {
            @Override
            public Entry decode(ByteBuf buf) {
                return new Entry(ByteBufCodecs.VAR_INT.decode(buf), ResourceLocation.STREAM_CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf), Change.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, Entry e) {
                ByteBufCodecs.VAR_INT.encode(buf, e.kind());
                ResourceLocation.STREAM_CODEC.encode(buf, e.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.title());
                ByteBufCodecs.VAR_INT.encode(buf, e.state());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.cost());
                Change.CODEC.apply(ByteBufCodecs.list()).encode(buf, e.outcome());
                ByteBufCodecs.VAR_INT.encode(buf, e.level());
                ByteBufCodecs.VAR_INT.encode(buf, e.max());
                ByteBufCodecs.BOOL.encode(buf, e.stage());
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, e.parents());
            }
        };
    }

    public record WorkshopView(String name, String stage, int range, long charge, List<Entry> entries,
            String message) implements CustomPacketPayload {
        public static final Type<WorkshopView> TYPE = new Type<>(TardisRB.id("workshop_view"));
        public static final StreamCodec<ByteBuf, WorkshopView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, WorkshopView::name,
                ByteBufCodecs.STRING_UTF8, WorkshopView::stage,
                ByteBufCodecs.VAR_INT, WorkshopView::range,
                ByteBufCodecs.VAR_LONG, WorkshopView::charge,
                Entry.CODEC.apply(ByteBufCodecs.list()), WorkshopView::entries,
                ByteBufCodecs.STRING_UTF8, WorkshopView::message,
                WorkshopView::new);

        @Override
        public Type<WorkshopView> type() {
            return TYPE;
        }
    }

    /** Do a repair or start a research node. */
    public record Act(int kind, ResourceLocation id) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(TardisRB.id("workshop_act"));
        public static final StreamCodec<ByteBuf, Act> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Act::kind, ResourceLocation.STREAM_CODEC, Act::id, Act::new);

        @Override
        public Type<Act> type() {
            return TYPE;
        }
    }
}
