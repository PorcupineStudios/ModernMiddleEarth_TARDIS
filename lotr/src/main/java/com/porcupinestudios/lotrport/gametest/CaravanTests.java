package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.journey.CaravanBook;
import com.porcupinestudios.lotrport.journey.CaravanRules;
import com.porcupinestudios.lotrport.journey.Caravans;
import com.porcupinestudios.lotrport.journey.Journey;
import com.porcupinestudios.lotrport.journey.Journeys;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.travel.FastTravel;
import com.porcupinestudios.lotrport.travel.Waypoint;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Caravans between colonies, as data (Hobbiton to the Brandywine Bridge and back): deliver to your own colony and
 * bring its goods home, go just to pick up, and trade with a friend's colony by its standing offer.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CaravanTests {
    private static final ResourceLocation BREAD = ResourceLocation.withDefaultNamespace("bread");
    private static final ResourceLocation IRON = ResourceLocation.withDefaultNamespace("iron_ingot");
    private static final ResourceLocation WOOL = ResourceLocation.withDefaultNamespace("white_wool");
    private static final ResourceLocation HOBBIT = LotrPort.id("hobbit");

    private CaravanTests() {}

    private static int stock(MinecraftServer server, UUID colony, ResourceLocation item) {
        return Colonies.get(server).get(colony).flatMap(c -> c.get(WorkComponents.STOCK))
                .map(s -> s.items().getOrDefault(item, 0)).orElse(0);
    }

    private static Colony colony(MinecraftServer server, GameTestHelper helper, UUID ruler, String name,
            BlockPos hall, Map<ResourceLocation, Integer> stock, int people) {
        Colonies colonies = Colonies.get(server);
        Colony c = colonies.foundAi(server, UUID.randomUUID(), ruler, name, helper.getLevel().dimension(), hall,
                HOBBIT, 0).orElseThrow(() -> new AssertionError("couldn't found " + name + " at " + hall));
        colonies.set(c, WorkComponents.STOCK, new WorkComponents.Stock(stock));
        Persons persons = Persons.get(server);
        for (int i = 0; i < people; i++) {
            Person p = persons.migrated(UUID.randomUUID(), HOBBIT, name + " carter " + i, 0, c.asOwner());
            persons.setQuiet(p, CoreComponents.CITIZEN, new CoreComponents.Citizen(0, false));
        }
        return c;
    }

    private static CaravanBook.Route route(UUID to, List<CaravanRules.Amount> send, List<CaravanRules.Amount> bring,
            boolean sell) {
        return new CaravanBook.Route(0, to, send, bring, 0, 1, sell, -1);
    }

    private static CaravanRules.Amount a(ResourceLocation item, int n) {
        return new CaravanRules.Amount(item.toString(), n);
    }

    @GameTest(template = "empty", timeoutTicks = 12000)
    public static void caravansDeliverPickUpAndTrade(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Waypoint w1 = FastTravel.waypoint(LotrPort.id("hobbiton")).orElseThrow();
        Waypoint w2 = FastTravel.waypoint(LotrPort.id("brandywine_bridge")).orElseThrow();
        UUID me = UUID.randomUUID();
        // test halls: offset from the waypoints so a rerun or other tests' colonies aren't in the way
        int off = (int) (Math.abs(me.getLeastSignificantBits()) % 40);
        Colony home = colony(server, helper, me, "Caravan Home", new BlockPos(w1.x() + off, 70, w1.z()),
                Map.of(BREAD, 100), 2);
        Colony mine = colony(server, helper, me, "Caravan Outpost", new BlockPos(w2.x() + off, 70, w2.z()),
                Map.of(IRON, 30), 0);
        Colony friend = colony(server, helper, UUID.randomUUID(), "Caravan Friend",
                new BlockPos(w1.x() + 300 + off, 70, w1.z()), Map.of(WOOL, 10), 0);
        Caravans.setBook(server, home, Caravans.book(home).withKeep(Map.of(BREAD.toString(), 20)));
        Caravans.setBook(server, Colonies.get(server).get(mine.id()).orElseThrow(),
                Caravans.book(mine).withKeep(Map.of(IRON.toString(), 25)));
        Caravans.setBook(server, Colonies.get(server).get(friend.id()).orElseThrow(), Caravans.book(friend)
                .withOffers(List.of(new CaravanRules.Offer(a(WOOL, 2), a(BREAD, 1), 0, 0))));
        Journey[] cur = {null};
        Journeys all = Journeys.get(server);
        helper.onEachTick(() -> {
            if (cur[0] != null && cur[0].moving()) {
                all.tick(server, 120);
            }
        });
        java.util.function.Consumer<CaravanBook.Route> go = r -> {
            Caravans.Sent s = Caravans.send(server, Colonies.get(server).get(home.id()).orElseThrow(), r);
            helper.assertTrue(s.journey().isPresent(), "not sent: " + s.why().getString());
            cur[0] = s.journey().get();
        };
        helper.startSequence()
                // 1. deliver 80 bread (20 kept home) to my outpost, bring back iron above its floor of 25
                .thenExecute(() -> go.accept(route(mine.id(), List.of(a(BREAD, 200)), List.of(a(IRON, 10)), false)))
                .thenWaitUntil(() -> helper.assertTrue(cur[0].stage == Journey.Stage.DONE, "1 not back: "
                        + cur[0].stage + " " + (int) cur[0].done + "/" + (int) cur[0].route.total()))
                .thenExecute(() -> {
                    helper.assertTrue(cur[0].route.found(), "no way from Hobbiton to the Brandywine Bridge");
                    helper.assertTrue(stock(server, home.id(), BREAD) == 20, "home bread "
                            + stock(server, home.id(), BREAD) + ", wanted 20 (its floor)");
                    helper.assertTrue(stock(server, mine.id(), BREAD) == 80, "outpost bread "
                            + stock(server, mine.id(), BREAD));
                    helper.assertTrue(stock(server, home.id(), IRON) == 5 && stock(server, mine.id(), IRON) == 25,
                            "iron home " + stock(server, home.id(), IRON) + " outpost " + stock(server, mine.id(), IRON)
                                    + ", wanted 5 and 25 (its floor)");
                    // 2. pick up only: nothing to send, bring 30 bread back
                    go.accept(route(mine.id(), List.of(), List.of(a(BREAD, 30)), false));
                })
                .thenWaitUntil(() -> helper.assertTrue(cur[0].stage == Journey.Stage.DONE, "2 not back"))
                .thenExecute(() -> {
                    helper.assertTrue(stock(server, home.id(), BREAD) == 50 && stock(server, mine.id(), BREAD) == 50,
                            "after pick-up: home " + stock(server, home.id(), BREAD) + " outpost "
                                    + stock(server, mine.id(), BREAD));
                    // 3. a friend's colony: 10 bread offered, it gives 2 wool a bread and has 10 wool -> 5 trade
                    Caravans.setBook(server, Colonies.get(server).get(home.id()).orElseThrow(),
                            Caravans.book(home).withKeep(Map.of()));
                    go.accept(route(friend.id(), List.of(a(BREAD, 10)), List.of(), true));
                })
                .thenWaitUntil(() -> helper.assertTrue(cur[0].stage == Journey.Stage.DONE, "3 not back"))
                .thenExecute(() -> {
                    helper.assertTrue(cur[0].route.found(), "no way to the friend's colony");
                    helper.assertTrue(stock(server, home.id(), WOOL) == 10 && stock(server, friend.id(), WOOL) == 0,
                            "wool home " + stock(server, home.id(), WOOL) + " friend "
                                    + stock(server, friend.id(), WOOL) + " (friend offers "
                                    + Colonies.get(server).get(friend.id()).map(c -> Caravans.book(c).offers().size())
                                    .orElse(-1) + ", caravan " + cur[0].extra + ", cargo " + cur[0].cargo
                                    + ", friend bread " + stock(server, friend.id(), BREAD) + ")");
                    helper.assertTrue(stock(server, friend.id(), BREAD) == 5 && stock(server, home.id(), BREAD) == 45,
                            "bread friend " + stock(server, friend.id(), BREAD) + " home "
                                    + stock(server, home.id(), BREAD) + " (5 sold, 5 brought back)");
                    Colonies cs = Colonies.get(server);
                    for (UUID id : List.of(home.id(), mine.id(), friend.id())) {
                        cs.get(id).ifPresent(c -> cs.disband(server, c, "test"));
                    }
                })
                .thenSucceed();
    }

    /**
     * Aaron (2026-11-12): "when the outpost's bread is below 10, send the excess over 30". The route goes by itself
     * while the outpost is short, takes only what is above 30 at home, and doesn't go again once the outpost is full.
     */
    // its own batch: it uses the same dry spots as the test above (Halls must be 96 apart)
    @GameTest(template = "empty", timeoutTicks = 8000, batch = "caravan_trigger")
    public static void aTriggeredRouteSendsTheExcess(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Waypoint w1 = FastTravel.waypoint(LotrPort.id("hobbiton")).orElseThrow();
        UUID me = UUID.randomUUID();
        int off = (int) (Math.abs(me.getLeastSignificantBits()) % 40);
        Colony home = colony(server, helper, me, "Excess Home", new BlockPos(w1.x() + off, 70, w1.z()),
                Map.of(BREAD, 100), 1);
        Colony outpost = colony(server, helper, me, "Excess Outpost", new BlockPos(w1.x() + 300 + off, 70, w1.z()),
                Map.of(), 0);
        CaravanBook.Route r = new CaravanBook.Route(1, outpost.id(), List.of(new CaravanRules.Amount(BREAD.toString(),
                0, 30)), List.of(), 0, 1, false, -1, java.util.Optional.of(new CaravanRules.Trigger(outpost.id(),
                        BREAD.toString(), false, 10)));
        Caravans.setBook(server, home, Caravans.book(home).withRoute(r));
        Journeys all = Journeys.get(server);
        helper.onEachTick(() -> all.from(home.id()).forEach(j -> {
            if (j.moving()) {
                all.tick(server, 120);
            }
        }));
        Journey[] sent = {null};
        helper.startSequence()
                .thenExecute(() -> Caravans.runRoutes(server))
                .thenExecute(() -> {
                    List<Journey> out = all.from(home.id());
                    helper.assertTrue(out.size() == 1, "the trigger sent " + out.size() + " caravans");
                    sent[0] = out.get(0);
                    helper.assertTrue(out.get(0).cargo.getOrDefault(BREAD, 0) == 70, "it took "
                            + out.get(0).cargo + ", wanted the 70 over 30");
                })
                .thenWaitUntil(() -> helper.assertTrue(sent[0].stage == Journey.Stage.DONE, "not back yet: "
                        + sent[0].stage))
                .thenExecute(() -> {
                    helper.assertTrue(sent[0].route.found(), "no way to the outpost ("
                            + com.porcupinestudios.lotrport.journey.RoutePlanner.lastSearch + ")");
                    helper.assertTrue(stock(server, outpost.id(), BREAD) == 70 && stock(server, home.id(), BREAD)
                            == 30, "outpost " + stock(server, outpost.id(), BREAD) + ", home "
                                    + stock(server, home.id(), BREAD));
                    int before = all.from(home.id()).size();
                    Caravans.runRoutes(server);
                    helper.assertTrue(all.from(home.id()).size() == before, "it went again with the outpost full");
                    Colonies cs = Colonies.get(server);
                    for (UUID id : List.of(home.id(), outpost.id())) {
                        cs.get(id).ifPresent(c -> cs.disband(server, c, "test"));
                    }
                })
                .thenSucceed();
    }
}
