package com.porcupinestudios.lotrport.unit;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.ColonyAccess;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.CoreComponents.Order;
import com.porcupinestudios.lotrport.society.CoreComponents.Unit;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The unit screen's packets: the server sends the units a player may command ({@link UnitList}); the screen sends
 * {@link Action}s back, checked again on the server, and gets a fresh list. Client handler set by client setup.
 */
public final class UnitNet {
    /** Opens or refreshes the unit screen; set by the client. */
    public static Consumer<UnitList> listHandler = list -> { };

    /** What a button asks for. */
    public enum Kind { FOLLOW, GUARD, STAND, SQUAD, STATION, DISMISS }

    private UnitNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(UnitNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(UnitList.TYPE, UnitList.CODEC, (p, ctx) -> ctx.enqueueWork(() -> listHandler.accept(p)));
        r.playToServer(Open.TYPE, Open.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                send(sp, Optional.empty(), true);
            }
        }));
        r.playToServer(Action.TYPE, Action.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                act(sp, p);
                send(sp, Optional.of(p.unit()), false);
            }
        }));
    }

    /** Applies a button press, if the player may command the unit. */
    static void act(ServerPlayer player, Action a) {
        Persons persons = Persons.get(player.server);
        Person p = persons.get(a.unit()).orElse(null);
        if (p == null || !Units.canCommand(player.server, player.getUUID(), p)) {
            return;
        }
        Kind kind = a.kind() >= 0 && a.kind() < Kind.values().length ? Kind.values()[a.kind()] : null;
        if (kind == null) {
            return;
        }
        switch (kind) {
            case FOLLOW, GUARD, STAND -> {
                Order order = kind == Kind.FOLLOW ? Order.FOLLOW : kind == Kind.GUARD ? Order.GUARD : Order.NONE;
                // "Guard" from the screen guards where the unit stands; a whole squad too
                Optional<GlobalPos> at = Optional.empty();
                if (a.squad()) {
                    Units.orderSquad(player.server, p, order, at);
                } else {
                    Units.order(player.server, p, order, at);
                }
            }
            case SQUAD -> Units.setSquad(player.server, p, a.arg());
            case STATION -> {
                try {
                    UUID colony = UUID.fromString(a.arg());
                    Units.colonies(player.server).get(colony).ifPresent(c -> player.displayClientMessage(
                            Units.station(player.server, player.getUUID(), p, c).message(), false));
                } catch (IllegalArgumentException e) {
                    // a bad id from the client: ignore
                }
            }
            case DISMISS -> {
                if (p.owner().playerId().filter(player.getUUID()::equals).isPresent()
                        || p.owner().colonyId().isPresent()) {
                    Units.dismiss(player.server, p, "dismissed");
                }
            }
            default -> { }
        }

    }

    /** Sends the player the units they may command; {@code open} opens the screen, else only refreshes it. */
    public static void send(ServerPlayer player, Optional<UUID> focus, boolean open) {
        List<Entry> entries = new ArrayList<>();
        for (Person p : Units.commandable(player.server, player.getUUID())) {
            Unit u = p.get(CoreComponents.UNIT).orElseThrow();
            Optional<LotrNpc> body = Units.body(player.server, p);
            float hp = body.map(LotrNpc::getHealth).orElse(-1f);
            float max = body.map(LotrNpc::getMaxHealth).orElse(-1f);
            String where = body.map(b -> b.blockPosition().toShortString())
                    .orElse(p.lastSeen().map(g -> g.pos().toShortString()).orElse("?"));
            entries.add(new Entry(p.id(), p.name(), p.type().toString(), u.order().ordinal(), u.squad().orElse(""),
                    Units.colonyName(player.server, p), u.wage(), u.unpaid(), hp, max, where));
        }
        List<Target> targets = new ArrayList<>();
        for (Colony c : Units.colonies(player.server).all()) {
            if (ColonyAccess.canStation(player.server, player.getUUID(), c)) {
                targets.add(new Target(c.id(), c.name()));
            }
        }
        PacketDistributor.sendToPlayer(player, new UnitList(entries, targets, focus, open));
    }

    /** One unit as the screen shows it. {@code colony} empty = the player's own; {@code health} -1 = not loaded. */
    public record Entry(UUID id, String name, String type, int order, String squad, String colony, int wage,
            int unpaid, float health, float maxHealth, String where) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.of((buf, e) -> {
            UUIDUtil.STREAM_CODEC.encode(buf, e.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.name());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.type());
            ByteBufCodecs.VAR_INT.encode(buf, e.order());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.squad());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.colony());
            ByteBufCodecs.VAR_INT.encode(buf, e.wage());
            ByteBufCodecs.VAR_INT.encode(buf, e.unpaid());
            buf.writeFloat(e.health());
            buf.writeFloat(e.maxHealth());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.where());
        }, buf -> new Entry(UUIDUtil.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(),
                buf.readFloat(), ByteBufCodecs.STRING_UTF8.decode(buf)));

        public Order orderValue() {
            return order >= 0 && order < Order.values().length ? Order.values()[order] : Order.NONE;
        }

        public boolean yours() {
            return colony.isEmpty();
        }
    }

    /** A colony the player may station units in. */
    public record Target(UUID id, String name) {
        public static final StreamCodec<ByteBuf, Target> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Target::id, ByteBufCodecs.STRING_UTF8, Target::name, Target::new);
    }

    public record UnitList(List<Entry> units, List<Target> colonies, Optional<UUID> focus, boolean open)
            implements CustomPacketPayload {
        public static final Type<UnitList> TYPE = new Type<>(LotrPort.id("unit_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UnitList> CODEC = StreamCodec.composite(
                Entry.CODEC.apply(ByteBufCodecs.list()), UnitList::units,
                Target.CODEC.apply(ByteBufCodecs.list()), UnitList::colonies,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), UnitList::focus,
                ByteBufCodecs.BOOL, UnitList::open,
                UnitList::new);

        @Override
        public Type<UnitList> type() {
            return TYPE;
        }
    }

    /** The unit key: open the screen. */
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(LotrPort.id("unit_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.unit(new Open());

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** A button: {@code kind} is a {@link Kind} ordinal; {@code squad} = the unit's whole squad. */
    public record Action(UUID unit, int kind, String arg, boolean squad) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(LotrPort.id("unit_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Action::unit,
                ByteBufCodecs.VAR_INT, Action::kind,
                ByteBufCodecs.STRING_UTF8, Action::arg,
                ByteBufCodecs.BOOL, Action::squad,
                Action::new);

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }

    /** Text for a unit's order. */
    public static Component orderName(Order order) {
        return Component.translatableWithFallback("unit.lotr_port.order." + order.getSerializedName(),
                switch (order) {
                    case FOLLOW -> "Following";
                    case GUARD -> "Guarding";
                    default -> "Standing";
                });
    }
}
