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
import com.porcupinestudios.tardisrb.repair.Parts;
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
 * The repairs and research screen: the stage's repair list, the research tree and the rooms that can be added, with what each costs and whether
 * it can be done now. Opened from the console; works only for crew standing in the TARDIS.
 */
public final class WorkshopNet {
    public static final int REPAIR = 0;
    public static final int RESEARCH = 1;
    public static final int ROOM = 2;
    public static final int DONE = 0;
    public static final int RUNNING = 1;
    public static final int AVAILABLE = 2;
    public static final int LOCKED = 3;
    /** A physical repair: done at its part block, not from the screen. */
    public static final int AT_PART = 4;

    public static BiConsumer<WorkshopView, IPayloadContext> clientHandler = (view, ctx) -> { };

    private WorkshopNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(WorkshopNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(WorkshopView.TYPE, WorkshopView.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> clientHandler.accept(p, ctx)));
        r.playToServer(OpenWorkshop.TYPE, OpenWorkshop.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                mine(sp).ifPresent(t -> PacketDistributor.sendToPlayer(sp, view(sp, t, "")));
            }
        }));
        r.playToServer(Act.TYPE, Act.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                mine(sp).ifPresent(t -> {
                    Repairs.Outcome out = switch (p.kind()) {
                        case REPAIR -> Repairs.attempt(sp.server, t, p.id(), Optional.of(sp));
                        case ROOM -> Rooms.add(sp.server, t, p.id(), Optional.of(sp));
                        default -> Researching.start(sp.server, t, p.id(), Optional.of(sp));
                    };
                    String msg = out.done() ? Component.translatableWithFallback("tardis_rb.workshop.ok", "Done")
                            .getString() : out.problem().get().getString();
                    PacketDistributor.sendToPlayer(sp, view(sp, t, msg));
                });
            }
        }));
    }

    private static Optional<TardisRecord> mine(ServerPlayer player) {
        return Interiors.at(player.serverLevel(), player.blockPosition()).filter(t -> t.isCrew(player.getUUID()));
    }

    /** The stage's repairs (plus any other repair that can be done now), then every research node. */
    public static WorkshopView view(ServerPlayer player, TardisRecord t, String message) {
        TardisContent.Snapshot data = TardisContent.get();
        List<Entry> entries = new ArrayList<>();
        Set<ResourceLocation> shown = new LinkedHashSet<>();
        t.stage().map(data.stages()::get).map(Stage::repairs).ifPresent(shown::addAll);
        new TreeMap<>(data.repairs()).forEach((id, r) -> {
            if (Repairs.blocked(player.server, t, id, Optional.of(player)).isEmpty()) {
                shown.add(id);
            }
        });
        for (ResourceLocation id : shown) {
            Repair r = data.repairs().get(id);
            if (r == null) {
                continue;
            }
            int state = Parts.fixed(t, r) ? DONE
                    : Repairs.blocked(player.server, t, id, Optional.of(player)).isPresent() ? LOCKED
                    : r.partPos().isPresent() ? AT_PART : AVAILABLE;
            entries.add(new Entry(REPAIR, id, r.subsystem().getPath() + " " + r.level(), state,
                    describe(data.cost(r.cost())), ""));
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
                    describe(data.cost(e.getValue().cost())), extra));
        }
        for (Map.Entry<ResourceLocation, Room> e : new TreeMap<>(data.rooms()).entrySet()) {
            long count = t.rooms().stream().filter(r -> r.room().equals(e.getKey())).count();
            int state = Rooms.blocked(player.server, t, e.getKey(), Optional.of(player)).isPresent() ? LOCKED
                    : AVAILABLE;
            entries.add(new Entry(ROOM, e.getKey(), e.getKey().getPath(), state,
                    describe(data.cost(e.getValue().cost())), count > 0 ? count + " built" : ""));
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

    /** One line: a repair or research node. */
    public record Entry(int kind, ResourceLocation id, String title, int state, String cost, String extra) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::kind,
                ResourceLocation.STREAM_CODEC, Entry::id,
                ByteBufCodecs.STRING_UTF8, Entry::title,
                ByteBufCodecs.VAR_INT, Entry::state,
                ByteBufCodecs.STRING_UTF8, Entry::cost,
                ByteBufCodecs.STRING_UTF8, Entry::extra,
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

    public record OpenWorkshop() implements CustomPacketPayload {
        public static final Type<OpenWorkshop> TYPE = new Type<>(TardisRB.id("open_workshop"));
        public static final StreamCodec<ByteBuf, OpenWorkshop> CODEC = StreamCodec.unit(new OpenWorkshop());

        @Override
        public Type<OpenWorkshop> type() {
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
