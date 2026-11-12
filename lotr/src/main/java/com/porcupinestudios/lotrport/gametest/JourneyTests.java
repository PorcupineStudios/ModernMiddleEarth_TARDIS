package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.journey.Journey;
import com.porcupinestudios.lotrport.journey.JourneyKind;
import com.porcupinestudios.lotrport.journey.Journeys;
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
            helper.assertTrue(j.route.found(), "no route from " + fa + " to " + fb);
            helper.assertTrue(ARRIVED.get() > before, "not there yet: " + (int) j.done + " / "
                    + (int) j.route.total());
        });
    }
}
