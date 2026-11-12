package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.journey.Journey;
import com.porcupinestudios.lotrport.journey.JourneyKind;
import com.porcupinestudios.lotrport.journey.JourneyBodies;
import com.porcupinestudios.lotrport.journey.Journeys;
import com.porcupinestudios.lotrport.journey.Route;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import com.porcupinestudios.lotrport.travel.FastTravel;
import com.porcupinestudios.lotrport.travel.Waypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Journeys as data: a party plans a way between two waypoints over the map, goes along it and arrives. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JourneyTests {
    private static final ResourceLocation TEST_KIND = LotrPort.id("test_walk");
    private static final AtomicInteger ARRIVED = new AtomicInteger();

    private JourneyTests() {}

    static {
        JourneyKind.register(TEST_KIND, new JourneyKind() {
            @Override
            public void unreachable(MinecraftServer server, Journey j) {}

            @Override
            public void arrived(MinecraftServer server, Journey j) {
                ARRIVED.incrementAndGet();
                j.stage = Journey.Stage.DONE;
            }

            @Override
            public Component label(MinecraftServer server, Journey j) {
                return Component.literal("test");
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void partyPlansGoesAndArrives(GameTestHelper helper) {
        List<Waypoint> ws = new ArrayList<>(FastTravel.waypoints().values().stream().filter(w -> !w.hidden())
                .toList());
        // the same pair every run (the loader's map order isn't fixed)
        ws.sort(java.util.Comparator.comparingInt(Waypoint::x).thenComparingInt(Waypoint::z));
        helper.assertTrue(ws.size() >= 2, "no waypoints to travel between");
        Waypoint a = null;
        Waypoint b = null;
        outer:
        for (Waypoint x : ws) {
            for (Waypoint y : ws) {
                double d = Math.hypot(x.x() - y.x(), x.z() - y.z());
                if (d > 400 && d < 1200 && x.region().equals(y.region())) {
                    a = x;
                    b = y;
                    break outer;
                }
            }
        }
        helper.assertTrue(a != null, "no two waypoints 400-1200 blocks apart in one region");
        MinecraftServer server = helper.getLevel().getServer();
        Journeys all = Journeys.get(server);
        Journey j = Journey.create(TEST_KIND, null, null, helper.getLevel().dimension(), a.x(), a.z(), b.x(), b.z(),
                List.of(), List.of(), 0, Map.of(), 1.0, helper.getLevel().getGameTime());
        all.start(j);
        int before = ARRIVED.get();
        final Waypoint fa = a;
        final Waypoint fb = b;
        helper.onEachTick(() -> {
            if (j.stage != Journey.Stage.PLANNING) {
                all.tick(server, 60);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(j.stage != Journey.Stage.PLANNING, "still planning");
            helper.assertTrue(j.route.found(), "no route from " + fa + " to " + fb + " ("
                    + com.porcupinestudios.lotrport.journey.RoutePlanner.lastSearch + ")");
            helper.assertTrue(ARRIVED.get() > before, "not there yet: " + (int) j.done + " / "
                    + (int) j.route.total());
        });
    }

    /**
     * Aaron (2026-11-11): a caravan watched in person must go exactly as fast as one nobody sees. Two parties on the
     * same route set out together; a player stands by one, so it walks in the world as bodies, while the other stays
     * data. After every tick both must be the same distance along.
     */
    @GameTest(template = "empty", timeoutTicks = 900)
    @SuppressWarnings("removal")
    public static void watchingDoesNotChangeSpeed(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Journeys all = Journeys.get(server);
        BlockPos o = helper.absolutePos(new BlockPos(0, 1, 0));
        var player = helper.makeMockServerPlayerInLevel();
        Vec3 at = Vec3.atBottomCenterOf(o);
        player.moveTo(at.x, at.y, at.z);
        Route r = new Route(new int[] {o.getX(), o.getX() + 60}, new int[] {o.getZ(), o.getZ()},
                new double[] {60}, 0);
        Person walker = Persons.get(server).add(server, Person.create(UUID.randomUUID(), LotrPort.id("hobbit"),
                "Watched Walker", Optional.empty(), helper.getLevel().getGameTime()), Optional.empty(),
                Optional.empty());
        Journey seen = Journey.create(TEST_KIND, null, null, helper.getLevel().dimension(), o.getX(), o.getZ(),
                o.getX() + 60, o.getZ(), List.of(walker.id()), List.of(), 0, Map.of(), 1.0,
                helper.getLevel().getGameTime());
        Journey unseen = Journey.create(TEST_KIND, null, null, helper.getLevel().dimension(), o.getX(), o.getZ(),
                o.getX() + 60, o.getZ(), List.of(), List.of(), 0, Map.of(), 1.0, helper.getLevel().getGameTime());
        for (Journey j : List.of(seen, unseen)) {
            j.route = r;
            j.stage = Journey.Stage.OUTBOUND;
            all.start(j);
        }
        boolean[] wasSeen = {false};
        helper.onEachTick(() -> {
            wasSeen[0] |= seen.embodied && !JourneyBodies.bodiesOf(seen.id).isEmpty();
            if (Math.abs(seen.done - unseen.done) > 1e-9 || seen.stage != unseen.stage) {
                helper.fail("watched " + seen.done + " " + seen.stage + " vs unwatched " + unseen.done + " "
                        + unseen.stage);
            }
            // keep the player beside the party so it stays in person
            double[] p = seen.position();
            player.moveTo(p[0], at.y, p[1]);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(wasSeen[0], "the watched party never came into the world");
            helper.assertTrue(seen.stage == Journey.Stage.DONE && unseen.stage == Journey.Stage.DONE,
                    "not both there yet: " + (int) seen.done + " / " + (int) unseen.done);
            Persons.get(server).remove(server, walker.id());
        });
    }

    /**
     * Aaron (2026-11-11): a long trip, Hobbiton to the Last Bridge (~35,000 blocks along the Great East Road over the
     * Brandywine Bridge). It must find the way, keep mostly to the road, and arrive as data.
     */
    @GameTest(template = "empty", timeoutTicks = 40000)
    public static void hobbitonToTheLastBridge(GameTestHelper helper) {
        Waypoint a = FastTravel.waypoint(LotrPort.id("hobbiton")).orElseThrow();
        Waypoint b = FastTravel.waypoint(LotrPort.id("last_bridge")).orElseThrow();
        MinecraftServer server = helper.getLevel().getServer();
        Journeys all = Journeys.get(server);
        Journey j = Journey.create(TEST_KIND, null, null, helper.getLevel().dimension(), a.x(), a.z(), b.x(), b.z(),
                List.of(), List.of(), 0, Map.of(), 1.0, helper.getLevel().getGameTime());
        all.start(j);
        long t0 = System.nanoTime();
        double[] took = {-1};
        boolean[] checked = {false};
        helper.onEachTick(() -> {
            if (j.stage != Journey.Stage.PLANNING && took[0] < 0) {
                took[0] = (System.nanoTime() - t0) / 1e9;
                LotrPort.LOG.info("[journeys] Hobbiton plan done after {} s: found {}, {}", took[0], j.route.found(),
                        com.porcupinestudios.lotrport.journey.RoutePlanner.lastSearch);
                if (j.route.found()) {
                    LotrPort.LOG.info("[journeys] Hobbiton to the Last Bridge: {} points, cost {}, road {}%, {} s at"
                            + " walking pace, planned in {} s ({})", j.route.points(), (int) j.route.total(),
                            (int) (100 * j.route.roadShare()), (int) j.secondsLeft(
                                    com.porcupinestudios.lotrport.journey.JourneyData.settings()), took[0],
                            com.porcupinestudios.lotrport.journey.RoutePlanner.lastSearch);
                }
            }
            if (j.route.found() && !checked[0]) {
                checked[0] = true;
                helper.assertTrue(j.route.roadShare() > 0.6, "only " + (int) (100 * j.route.roadShare())
                        + "% of the way on roads");
            }
            if (j.moving()) {
                all.tick(server, 120);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(j.stage != Journey.Stage.PLANNING, "still planning");
            helper.assertTrue(j.route.found(), "no route from Hobbiton to the Last Bridge ("
                    + com.porcupinestudios.lotrport.journey.RoutePlanner.lastSearch + ")");
            helper.assertTrue(j.stage == Journey.Stage.DONE, "not there yet: " + (int) j.done + " / "
                    + (int) j.route.total());
        });
    }
}
