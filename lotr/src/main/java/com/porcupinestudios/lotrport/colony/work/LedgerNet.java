package com.porcupinestudios.lotrport.colony.work;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.BuildingType;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The Ledger (RingRealms' Steward's Ledger) and the citizen screen. Members ask for a colony's Ledger
 * ({@link Request}); the server answers with what the colony has ({@link Ledger}); those with the {@code treasury}
 * permission change the head tax and wage bonus ({@link SetEconomy}). Sneak + right-click a citizen of your colony
 * for its page ({@link CitizenView}). Text is resolved on the server; the client only draws.
 */
public final class LedgerNet {
    public static Consumer<Ledger> ledgerHandler = l -> { };
    public static Consumer<CitizenView> citizenHandler = c -> { };

    /** Head tax and wage bonus limits (RingRealms: tax 0-10, bonus -3..20). */
    public static final int MAX_TAX = 10;
    public static final int MIN_BONUS = -3;
    public static final int MAX_BONUS = 20;

    private LedgerNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(LedgerNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(Ledger.TYPE, Ledger.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ledgerHandler.accept(p)));
        r.playToClient(CitizenView.TYPE, CitizenView.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> citizenHandler.accept(p)));
        r.playToServer(Request.TYPE, Request.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Colonies.get(sp.server).get(p.colony()).ifPresent(c -> send(sp, c, true));
            }
        }));
        r.playToServer(SetEconomy.TYPE, SetEconomy.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                setEconomy(sp, p);
            }
        }));
    }

    static void setEconomy(ServerPlayer sp, SetEconomy p) {
        Optional<Colony> c = Colonies.get(sp.server).get(p.colony());
        if (c.isEmpty() || !Permissions.can(sp.getUUID(), c.get(), Perm.TREASURY)) {
            return;
        }
        WorkComponents.Economy e = new WorkComponents.Economy(Math.max(0, Math.min(MAX_TAX, p.headTax())),
                Math.max(MIN_BONUS, Math.min(MAX_BONUS, p.wageBonus())));
        Colonies.get(sp.server).set(c.get(), WorkComponents.ECONOMY, e);
        send(sp, c.get(), false);
    }

    /** Sends the Ledger of {@code colony} to a member; {@code open} opens the screen. */
    public static void send(ServerPlayer sp, Colony colony, boolean open) {
        MinecraftServer server = sp.server;
        if (!Permissions.isMember(sp.getUUID(), colony)) {
            sp.displayClientMessage(Component.translatableWithFallback("colony.lotr_port.ledger.members_only",
                    "Only members of %s may read its Ledger", colony.name()), true);
            return;
        }
        List<Person> people = Society.citizens(server, colony);
        Buildings buildings = Buildings.of(colony);
        Map<UUID, Integer> workers = new HashMap<>();
        Map<UUID, Integer> residents = new HashMap<>();
        List<Row> citizens = new ArrayList<>();
        double food = 0;
        double happy = 0;
        int counted = 0;
        for (Person p : people) {
            Optional<WorkComponents.Job> job = p.get(WorkComponents.JOB);
            job.ifPresent(j -> workers.merge(j.building(), 1, Integer::sum));
            p.get(WorkComponents.HOME).ifPresent(h -> residents.merge(h.building(), 1, Integer::sum));
            WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
            String what = p.has(CoreComponents.UNIT) ? "soldier"
                    : job.map(j -> JobDef.titleOf(j.job()).getString()).orElse("idle");
            if (!p.has(CoreComponents.UNIT)) {
                food += n.food();
                happy += n.happiness();
                counted++;
            }
            citizens.add(new Row(name(p), what + " · " + Races.of(p.type()),
                    (int) Math.round(n.happiness()), n.unpaid() > 0 ? "striking" : n.food() <= 0 ? "starving" : ""));
        }
        citizens.sort(Comparator.comparing(Row::name));
        List<Row> rows = new ArrayList<>();
        for (Building b : buildings.list()) {
            String detail = BuildingType.get(b.type()).map(t -> t.home() ? residents.getOrDefault(b.id(), 0) + "/"
                    + b.count("beds") + " living" : t.job().map(j -> workers.getOrDefault(b.id(), 0) + "/"
                    + JobDef.get(j).map(d -> d.slots(b.quality())).orElse(0) + " " + JobDef.titleOf(j).getString())
                    .orElse("")).orElse("");
            rows.add(new Row(b.label().getString(), detail + " · floor " + b.floor(), b.quality(),
                    b.door().toShortString()));
        }
        rows.sort(Comparator.comparing(Row::name));
        List<Row> stock = new ArrayList<>();
        Map<ResourceLocation, Integer> goods = new HashMap<>(colony.get(WorkComponents.STOCK)
                .orElse(WorkComponents.Stock.EMPTY).items());
        goods.entrySet().stream().sorted(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed()).limit(60)
                .forEach(e -> stock.add(new Row(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(e.getKey())
                        .getDescription().getString(), "", e.getValue(), "")));
        WorkComponents.Economy eco = colony.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
        long coins = colony.get(CoreComponents.TREASURY).map(CoreComponents.Treasury::coins).orElse(0L);
        PacketDistributor.sendToPlayer(sp, new Ledger(colony.id(), colony.name(), people.size(),
                counted == 0 ? 0 : (int) Math.round(happy / counted), counted == 0 ? 0 : (int) Math.round(food / counted),
                (int) Math.min(Integer.MAX_VALUE, coins), eco.headTax(), eco.wageBonus(),
                Permissions.can(sp.getUUID(), colony, Perm.TREASURY), rows, citizens, stock, open));
    }

    /** A citizen's page, for a member of its colony. */
    public static boolean sendCitizen(ServerPlayer sp, Person p) {
        Optional<Colony> c = Society.colonyOf(sp.server, p);
        if (c.isEmpty() || !Permissions.isMember(sp.getUUID(), c.get())) {
            return false;
        }
        Buildings buildings = Buildings.of(c.get());
        Optional<WorkComponents.Job> job = p.get(WorkComponents.JOB);
        WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
        String work = p.has(CoreComponents.UNIT) ? "Soldier" : job.map(j -> JobDef.titleOf(j.job()).getString()
                + buildings.get(j.building()).map(b -> " at " + b.label().getString()).orElse("")).orElse("No work");
        String home = p.get(WorkComponents.HOME).flatMap(h -> buildings.get(h.building()))
                .map(b -> b.label().getString() + " (quality " + b.quality() + ")").orElse("Homeless");
        PacketDistributor.sendToPlayer(sp, new CitizenView(p.id(), name(p), c.get().name(), Races.of(p.type()),
                work, home, (int) Math.round(n.food()), (int) Math.round(n.happiness()),
                (int) job.map(WorkComponents.Job::skill).orElse(0.0).doubleValue(),
                (int) Math.min(Integer.MAX_VALUE, p.get(WorkComponents.PURSE).orElse(WorkComponents.Purse.EMPTY).coins()),
                n.unpaid()));
        return true;
    }

    static String name(Person p) {
        return p.name().isEmpty() ? p.type().getPath().replace('_', ' ') : p.name();
    }

    /** A list row: a name, a detail line, a number and a note. */
    public record Row(String name, String detail, int value, String note) {
        public static final StreamCodec<ByteBuf, Row> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Row::name, ByteBufCodecs.STRING_UTF8, Row::detail,
                ByteBufCodecs.VAR_INT, Row::value, ByteBufCodecs.STRING_UTF8, Row::note, Row::new);
    }

    /** The colony as the Ledger shows it. */
    public record Ledger(UUID colony, String name, int citizens, int happiness, int food, int coins, int headTax,
            int wageBonus, boolean canEdit, List<Row> buildings, List<Row> people, List<Row> stock, boolean open)
            implements CustomPacketPayload {
        public static final Type<Ledger> TYPE = new Type<>(LotrPort.id("ledger"));
        private static final StreamCodec<ByteBuf, List<Row>> ROWS = Row.CODEC.apply(ByteBufCodecs.list());
        public static final StreamCodec<RegistryFriendlyByteBuf, Ledger> CODEC = StreamCodec.of((buf, l) -> {
            UUIDUtil.STREAM_CODEC.encode(buf, l.colony());
            ByteBufCodecs.STRING_UTF8.encode(buf, l.name());
            buf.writeVarInt(l.citizens());
            buf.writeVarInt(l.happiness());
            buf.writeVarInt(l.food());
            buf.writeVarInt(l.coins());
            buf.writeVarInt(l.headTax());
            buf.writeInt(l.wageBonus());
            buf.writeBoolean(l.canEdit());
            ROWS.encode(buf, l.buildings());
            ROWS.encode(buf, l.people());
            ROWS.encode(buf, l.stock());
            buf.writeBoolean(l.open());
        }, buf -> new Ledger(UUIDUtil.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readInt(), buf.readBoolean(), ROWS.decode(buf), ROWS.decode(buf), ROWS.decode(buf),
                buf.readBoolean()));

        @Override
        public Type<Ledger> type() {
            return TYPE;
        }
    }

    public record Request(UUID colony) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(LotrPort.id("ledger_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
                StreamCodec.composite(UUIDUtil.STREAM_CODEC, Request::colony, Request::new);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    public record SetEconomy(UUID colony, int headTax, int wageBonus) implements CustomPacketPayload {
        public static final Type<SetEconomy> TYPE = new Type<>(LotrPort.id("ledger_economy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetEconomy> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, SetEconomy::colony, ByteBufCodecs.VAR_INT, SetEconomy::headTax,
                ByteBufCodecs.INT, SetEconomy::wageBonus, SetEconomy::new);

        @Override
        public Type<SetEconomy> type() {
            return TYPE;
        }
    }

    /** One citizen's page. */
    public record CitizenView(UUID id, String name, String colony, String race, String work, String home, int food,
            int happiness, int skill, int purse, int unpaid) implements CustomPacketPayload {
        public static final Type<CitizenView> TYPE = new Type<>(LotrPort.id("citizen_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CitizenView> CODEC = StreamCodec.of((buf, c) -> {
            UUIDUtil.STREAM_CODEC.encode(buf, c.id());
            for (String s : new String[] {c.name(), c.colony(), c.race(), c.work(), c.home()}) {
                ByteBufCodecs.STRING_UTF8.encode(buf, s);
            }
            for (int i : new int[] {c.food(), c.happiness(), c.skill(), c.purse(), c.unpaid()}) {
                buf.writeVarInt(i);
            }
        }, buf -> new CitizenView(UUIDUtil.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<CitizenView> type() {
            return TYPE;
        }
    }
}
