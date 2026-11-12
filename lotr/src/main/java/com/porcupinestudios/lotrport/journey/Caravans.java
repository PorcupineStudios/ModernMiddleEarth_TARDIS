package com.porcupinestudios.lotrport.journey;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.work.Stores;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.faction.Relation;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.war.Wars;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Caravans: a {@link Journey} of kind {@code lotr_port:caravan} between two colonies. It loads its {@code send}
 * goods at home (never below the home's floors), goes, and at the other end:
 * <ul>
 * <li>your own colony: unloads everything, then loads {@code bring} from it (never below that colony's floors), so a
 * route can export what another colony of yours makes or has just imported;</li>
 * <li>another's friendly colony: trades by that colony's standing offers, then leaves the rest as supplies, or with
 * {@code sell} brings it home; a caravan never takes another's goods except through its offers.</li>
 * </ul>
 * Then it comes home and unloads. A caravan with nothing to send still goes, to pick up. Standing routes with
 * {@code every} set out on their own once that many days have passed. If the other colony is gone or no longer
 * friendly when it arrives, it turns round with its load.
 */
public final class Caravans {
    public static final ResourceLocation KIND = LotrPort.id("caravan");
    /** How items a party carries per stack it can carry. */
    static final int PER_STACK = 64;

    private Caravans() {}

    /** Whose a colony is to another: their own, a friend's (may trade and supply), or neither. */
    public enum Standing { OWN, FRIENDLY, NONE }

    public static void register() {
        CaravanBook.TYPE.id(); // registered before worlds load
        JourneyKind.register(KIND, new Kind());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % 200 == 100) {
                runRoutes(e.getServer());
            }
        });
    }

    public static CaravanBook book(Colony c) {
        return c.get(CaravanBook.TYPE).orElse(CaravanBook.EMPTY);
    }

    public static void setBook(MinecraftServer server, Colony c, CaravanBook b) {
        Colonies.get(server).set(c, CaravanBook.TYPE, b);
    }

    public static Standing standing(MinecraftServer server, Colony from, Colony to) {
        if (from.id().equals(to.id())) {
            return Standing.NONE;
        }
        if (from.founder().equals(to.founder())) {
            return Standing.OWN;
        }
        if (Wars.get(server).atWar(from.asOwner(), to.asOwner())) {
            return Standing.NONE;
        }
        Relation r = from.faction().equals(to.faction()) ? Relation.ALLY
                : Factions.relation(server, from.faction(), to.faction());
        return r == Relation.ALLY || r == Relation.FRIEND ? Standing.FRIENDLY : Standing.NONE;
    }

    /** What happened when a caravan was sent. */
    public record Sent(Optional<Journey> journey, Component why) {}

    /** Sends a caravan now along a route (standing or one-off: {@code id} 0). */
    public static Sent send(MinecraftServer server, Colony home, CaravanBook.Route route) {
        Optional<Colony> dest = Colonies.get(server).get(route.to());
        if (dest.isEmpty()) {
            return new Sent(Optional.empty(), Component.literal("That colony is gone"));
        }
        if (standing(server, home, dest.get()) == Standing.NONE) {
            return new Sent(Optional.empty(), Component.literal(dest.get().name() + " isn't friendly"));
        }
        if (!home.dimension().equals(dest.get().dimension())) {
            return new Sent(Optional.empty(), Component.literal(dest.get().name() + " is in another world"));
        }
        List<UUID> people = new ArrayList<>();
        for (Person p : Society.citizens(server, home)) {
            if (people.size() >= Math.max(1, route.people())) {
                break;
            }
            if (!p.has(CoreComponents.UNIT) && !Journeys.away(server, p.id())) {
                people.add(p.id());
            }
        }
        if (people.isEmpty()) {
            return new Sent(Optional.empty(), Component.literal("Nobody is free to go"));
        }
        JourneySettings s = JourneyData.settings();
        PartyRules.Result party = PartyRules.of(people.size(), List.of(), 0, s.wagonSpeed(), s.personCarry(),
                s.wagonCarry());
        int capacity = party.stacks() * PER_STACK;
        ServerLevel level = server.getLevel(home.dimension());
        Stores stores = new Stores(server, level, home);
        CaravanBook hb = book(home);
        Map<String, Integer> load = CaravanRules.load(route.send(), item -> stores.count(id(item), null),
                hb::keepOf, capacity);
        Map<ResourceLocation, Integer> cargo = new LinkedHashMap<>();
        load.forEach((item, n) -> {
            int took = stores.take(id(item), n, null);
            if (took > 0) {
                cargo.merge(id(item), took, Integer::sum);
            }
        });
        stores.save();
        Journey j = Journey.create(KIND, home.id(), dest.get().id(), home.dimension(), home.hall().getX(),
                home.hall().getZ(), dest.get().hall().getX(), dest.get().hall().getZ(), people, List.of(), 0, cargo,
                party.speed(), server.overworld().getGameTime());
        j.extra.put("route", CaravanBook.Route.CODEC.encodeStart(NbtOps.INSTANCE, route).getOrThrow());
        j.extra.putInt("capacity", capacity);
        Journeys.get(server).start(j);
        return new Sent(Optional.of(j), Component.literal("A caravan of " + people.size() + " sets out for "
                + dest.get().name() + " with " + CaravanRules.size(load) + " goods"));
    }

    /** Standing routes that are due set out. */
    public static void runRoutes(MinecraftServer server) {
        long day = server.overworld().getDayTime() / 24000L;
        for (Colony c : List.copyOf(Colonies.get(server).all())) {
            CaravanBook b = book(c);
            if (b.routes().isEmpty()) {
                continue;
            }
            for (CaravanBook.Route r : b.routes()) {
                if (!due(server, c, r, day) || onTheRoad(server, c, r.id())) {
                    continue;
                }
                Sent sent = send(server, c, r);
                if (sent.journey().isPresent()) {
                    b = b.withRoute(r.sentOn(day));
                    setBook(server, c, b);
                }
                tell(server, c, sent.why());
            }
        }
    }

    /** Whether a standing route should set out now: its days are up, or its trigger holds. */
    static boolean due(MinecraftServer server, Colony home, CaravanBook.Route r, long day) {
        if (r.when().isPresent()) {
            CaravanRules.Trigger t = r.when().get();
            Optional<Colony> watched = Colonies.get(server).get(t.colony());
            if (watched.isEmpty() || !watched.get().founder().equals(home.founder())
                    && !watched.get().id().equals(r.to())) {
                return false; // only your own colonies, or where the route goes
            }
            Stores st = new Stores(server, server.getLevel(watched.get().dimension()), watched.get());
            return t.met(st.count(id(t.item()), null));
        }
        return r.every() > 0 && (r.lastDay() < 0 || day - r.lastDay() >= r.every());
    }

    /** A caravan of this route is still out. */
    public static boolean onTheRoad(MinecraftServer server, Colony c, int route) {
        for (Journey j : Journeys.get(server).from(c.id())) {
            if (j.kind.equals(KIND) && j.stage != Journey.Stage.DONE && routeOf(j).id() == route && route != 0) {
                return true;
            }
        }
        return false;
    }

    static CaravanBook.Route routeOf(Journey j) {
        return CaravanBook.Route.CODEC.parse(NbtOps.INSTANCE, j.extra.get("route")).result()
                .orElse(new CaravanBook.Route(0, j.to, List.of(), List.of(), 0, 1, false, -1));
    }

    static ResourceLocation id(String item) {
        ResourceLocation r = ResourceLocation.tryParse(item);
        return r == null ? ResourceLocation.withDefaultNamespace("air") : r;
    }

    static Map<String, Integer> names(Map<ResourceLocation, Integer> cargo) {
        Map<String, Integer> out = new LinkedHashMap<>();
        cargo.forEach((k, v) -> out.put(k.toString(), v));
        return out;
    }

    static void tell(MinecraftServer server, Colony c, Component msg) {
        ServerPlayer p = server.getPlayerList().getPlayer(c.founder());
        if (p != null) {
            p.sendSystemMessage(Component.literal("[" + c.name() + "] ").append(msg));
        }
    }

    /** What a caravan does at each end. */
    static final class Kind implements JourneyKind {
        @Override
        public void unreachable(MinecraftServer server, Journey j) {
            Colonies.get(server).get(j.from).ifPresent(home -> {
                unload(server, home, j);
                tell(server, home, Component.literal("A caravan found no way there and stayed home"));
            });
        }

        @Override
        public void arrived(MinecraftServer server, Journey j) {
            Colonies colonies = Colonies.get(server);
            Optional<Colony> home = colonies.get(j.from);
            if (j.stage == Journey.Stage.RETURNING) {
                home.ifPresent(h -> {
                    int n = CaravanRules.size(names(j.cargo));
                    unload(server, h, j);
                    tell(server, h, Component.literal("A caravan came home with " + n + " goods"));
                });
                j.stage = Journey.Stage.DONE;
                return;
            }
            Optional<Colony> dest = colonies.get(j.to);
            if (home.isEmpty()) {
                // nowhere to go back to: it stays where it is, goods and all
                dest.ifPresent(d -> unload(server, d, j));
                j.stage = Journey.Stage.DONE;
                return;
            }
            Standing st = dest.map(d -> standing(server, home.get(), d)).orElse(Standing.NONE);
            if (st != Standing.NONE) {
                atDestination(server, home.get(), dest.get(), j, st);
            } else {
                tell(server, home.get(), Component.literal("A caravan found its destination gone or hostile, and "
                        + "turns back"));
            }
            Journeys.get(server).turnBack(j);
        }

        @Override
        public Component label(MinecraftServer server, Journey j) {
            return Component.literal("Caravan to " + Colonies.get(server).get(j.to).map(Colony::name).orElse("?"));
        }
    }

    /** Unloads, trades and loads at the far end. */
    static void atDestination(MinecraftServer server, Colony home, Colony dest, Journey j, Standing st) {
        CaravanBook.Route route = routeOf(j);
        int capacity = j.extra.contains("capacity") ? j.extra.getInt("capacity") : PER_STACK;
        Stores stores = new Stores(server, server.getLevel(dest.dimension()), dest);
        CaravanBook db = book(dest);
        int delivered = 0;
        if (st == Standing.OWN) {
            delivered = CaravanRules.size(names(j.cargo));
            j.cargo.forEach((item, n) -> stores.put(item, n, null));
            j.cargo.clear();
            Map<String, Integer> load = CaravanRules.load(route.bring(), item -> stores.count(id(item), null),
                    db::keepOf, capacity);
            load.forEach((item, n) -> {
                int took = stores.take(id(item), n, null);
                if (took > 0) {
                    j.cargo.merge(id(item), took, Integer::sum);
                }
            });
        } else {
            CaravanRules.Trade t = CaravanRules.trade(names(j.cargo), db.offers(),
                    item -> stores.count(id(item), null), capacity);
            t.paid().forEach((item, n) -> {
                stores.put(id(item), n, null);
                j.cargo.merge(id(item), -n, Integer::sum);
                j.cargo.remove(id(item), 0);
            });
            t.got().forEach((item, n) -> {
                int took = stores.take(id(item), n, null);
                if (took > 0) {
                    j.cargo.merge(id(item), took, Integer::sum);
                }
            });
            if (!route.sell()) {
                // what didn't trade, and isn't what it traded for, is left as supplies
                Map<ResourceLocation, Integer> keep = new LinkedHashMap<>();
                for (var e : j.cargo.entrySet()) {
                    if (t.got().containsKey(e.getKey().toString())) {
                        keep.put(e.getKey(), Math.min(e.getValue(), t.got().get(e.getKey().toString())));
                        int rest = e.getValue() - keep.get(e.getKey());
                        if (rest > 0) {
                            stores.put(e.getKey(), rest, null);
                            delivered += rest;
                        }
                    } else {
                        stores.put(e.getKey(), e.getValue(), null);
                        delivered += e.getValue();
                    }
                }
                j.cargo.clear();
                j.cargo.putAll(keep);
            }
            delivered += CaravanRules.size(t.paid());
        }
        stores.save();
        int back = CaravanRules.size(names(j.cargo));
        j.extra.putInt("delivered", delivered);
        tell(server, home, Component.literal("A caravan reached " + dest.name() + ": left " + delivered
                + " goods, bringing back " + back));
        if (!dest.founder().equals(home.founder())) {
            tell(server, dest, Component.literal("A caravan from " + home.name() + " arrived with " + delivered
                    + " goods"));
        }
    }

    static void unload(MinecraftServer server, Colony c, Journey j) {
        Stores stores = new Stores(server, server.getLevel(c.dimension()), c);
        j.cargo.forEach((item, n) -> stores.put(item, n, null));
        j.cargo.clear();
        stores.save();
    }
}
