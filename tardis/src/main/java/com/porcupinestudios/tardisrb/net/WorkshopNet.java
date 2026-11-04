package com.porcupinestudios.tardisrb.net;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.interior.Rooms;
import com.porcupinestudios.tardisrb.progression.Cost;
import com.porcupinestudios.tardisrb.progression.Repair;
import com.porcupinestudios.tardisrb.progression.Research;
import com.porcupinestudios.tardisrb.progression.Room;
import com.porcupinestudios.tardisrb.progression.Stage;
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
 * The repair and upgrade tree (Aaron, 2026-11-03): every repair by subsystem and level, the research tree and the rooms
 * that can be added, with what each costs and whether it can be done now. Opened at the Repair Station in the
 * Repair Station; works only for crew standing in the TARDIS.
 */
public final class WorkshopNet {
    public static final int REPAIR = 0;
    public static final int RESEARCH = 1;
    public static final int ROOM = 2;
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

    /** Does what a click on a node asks: a repair, a research node or a room, paid from the player. */
    public static Repairs.Outcome act(ServerPlayer player, TardisRecord t, int kind, ResourceLocation id) {
        return switch (kind) {
            case REPAIR -> Repairs.attempt(player.server, t, id, Optional.of(player));
            case ROOM -> Rooms.add(player.server, t, id, Optional.of(player));
            default -> Researching.start(player.server, t, id, Optional.of(player));
        };
    }

    private static Optional<TardisRecord> mine(ServerPlayer player) {
        return Interiors.at(player.serverLevel(), player.blockPosition()).filter(t -> t.isCrew(player.getUUID()));
    }

    /** Every repair (by subsystem and level), then every research node (with its parents), then every room. */
    public static WorkshopView view(ServerPlayer player, TardisRecord t, String message) {
        TardisContent.Snapshot data = TardisContent.get();
        List<Entry> entries = new ArrayList<>();
        Set<ResourceLocation> stageRepairs = new LinkedHashSet<>();
        t.stage().map(data.stages()::get).map(Stage::repairs).ifPresent(stageRepairs::addAll);
        for (Map.Entry<ResourceLocation, Repair> e : new TreeMap<>(data.repairs()).entrySet()) {
            Repair r = e.getValue();
            int state = Unlocks.level(t, r.subsystem()) >= r.level() ? DONE
                    : Repairs.blocked(player.server, t, e.getKey(), Optional.of(player)).isPresent() ? LOCKED
                    : AVAILABLE;
            entries.add(new Entry(REPAIR, e.getKey(), r.subsystem().getPath() + " " + r.level(), state,
                    new Text(describe(data.cost(r.cost())), stageRepairs.contains(e.getKey()) ? "this stage" : ""),
                    new Node(r.subsystem().getPath(), r.level(), List.of())));
        }
        long now = player.server.overworld().getGameTime();
        for (Map.Entry<ResourceLocation, Research> e : new TreeMap<>(data.research()).entrySet()) {
            Researching.State s = Researching.state(player.server, t, e.getKey());
            int state = switch (s) {
                case DONE -> DONE;
                case RUNNING -> RUNNING;
                case AVAILABLE -> AVAILABLE;
                case LOCKED -> LOCKED;
            };
            String extra = s == Researching.State.RUNNING
                    ? t.researching().map(j -> Math.max(0, (j.finishAt() - now) / 20) + "s left").orElse("")
                    : (e.getValue().time() / 20) + "s";
            entries.add(new Entry(RESEARCH, e.getKey(), e.getKey().getPath(), state,
                    new Text(describe(data.cost(e.getValue().cost())), extra),
                    new Node("", 0, e.getValue().parents())));
        }
        for (Map.Entry<ResourceLocation, Room> e : new TreeMap<>(data.rooms()).entrySet()) {
            long count = t.rooms().stream().filter(r -> r.room().equals(e.getKey())).count();
            int state = Rooms.blocked(player.server, t, e.getKey(), Optional.of(player)).isPresent() ? LOCKED
                    : AVAILABLE;
            entries.add(new Entry(ROOM, e.getKey(), e.getKey().getPath(), state,
                    new Text(describe(data.cost(e.getValue().cost())), count > 0 ? count + " built" : ""),
                    new Node("", 0, List.of())));
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

    /** What a node costs, and a short note (time left, rooms built, part of this stage). */
    public record Text(String cost, String extra) {
        public static final StreamCodec<ByteBuf, Text> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Text::cost, ByteBufCodecs.STRING_UTF8, Text::extra, Text::new);
    }

    /** Where a node sits in the tree: a repair's subsystem row and level column, a research node's parents. */
    public record Node(String group, int level, List<ResourceLocation> parents) {
        public static final StreamCodec<ByteBuf, Node> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Node::group,
                ByteBufCodecs.VAR_INT, Node::level,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), Node::parents,
                Node::new);
    }

    /** One node: a repair, a research node or a room. */
    public record Entry(int kind, ResourceLocation id, String title, int state, Text text, Node node) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::kind,
                ResourceLocation.STREAM_CODEC, Entry::id,
                ByteBufCodecs.STRING_UTF8, Entry::title,
                ByteBufCodecs.VAR_INT, Entry::state,
                Text.CODEC, Entry::text,
                Node.CODEC, Entry::node,
                Entry::new);
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
