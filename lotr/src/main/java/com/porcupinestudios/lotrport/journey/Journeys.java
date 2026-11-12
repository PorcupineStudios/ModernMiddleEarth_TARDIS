package com.porcupinestudios.lotrport.journey;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.lotrport.world.map.Roads;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Every party on the road (SavedData {@code lotr_port_journeys}). Once a second each moving journey goes on by its
 * speed; routes are planned off the server thread on one worker against a snapshot of the map, roads and marked
 * bridges. Whether a party is data or bodies in the world is up to {@link JourneyBodies}; while it is bodies, they
 * only follow: {@code done} always comes from here, so a party seen in person goes exactly as fast as one that
 * isn't (GameTest {@code JourneyTests.watchingDoesNotChangeSpeed}).
 */
public final class Journeys extends SavedData {
    public static final String FILE = "lotr_port_journeys";
    public static final int DATA_VERSION = 1;
    /** Finished journeys stay this long (game ticks) for the map and ledgers. */
    static final long KEEP_DONE = 20 * 60;

    private static final SavedData.Factory<Journeys> FACTORY = new SavedData.Factory<>(Journeys::new, Journeys::load,
            null);
    private static ExecutorService planner;

    private final Map<UUID, Journey> journeys = new LinkedHashMap<>();
    private final Map<UUID, CompletableFuture<Route>> planning = new LinkedHashMap<>();
    private final Map<UUID, Long> doneAt = new LinkedHashMap<>();

    public static Journeys get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % 20 == 0) {
                get(e.getServer()).tick(e.getServer(), 1.0);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            if (planner != null) {
                planner.shutdownNow();
                planner = null;
            }
        });
        JourneyBodies.register();
    }

    public Collection<Journey> all() {
        return List.copyOf(journeys.values());
    }

    public Optional<Journey> get(UUID id) {
        return Optional.ofNullable(journeys.get(id));
    }

    public List<Journey> from(UUID colony) {
        return journeys.values().stream().filter(j -> colony.equals(j.from)).toList();
    }

    /** Whether a person is on the road (their colony leaves their body to the journey). */
    public static boolean away(MinecraftServer server, UUID person) {
        for (Journey j : get(server).journeys.values()) {
            if (j.stage != Journey.Stage.DONE && j.people.contains(person)) {
                return true;
            }
        }
        return false;
    }

    /** Sets a new journey on its way: it plans first. */
    public void start(Journey j) {
        journeys.put(j.id, j);
        setDirty();
    }

    /** Turns a journey round: it goes back the way it came. */
    public void turnBack(Journey j) {
        double back = j.route.total() - j.done;
        j.route = j.route.reversed();
        j.done = back;
        j.stage = Journey.Stage.RETURNING;
        setDirty();
    }

    /** Ends a journey now (it is kept a moment for the map). */
    public void finish(MinecraftServer server, Journey j) {
        j.stage = Journey.Stage.DONE;
        doneAt.put(j.id, server.overworld().getGameTime());
        setDirty();
    }

    /** Runs every planned journey's next {@code seconds}; tests call it with large steps. */
    public void tick(MinecraftServer server, double seconds) {
        JourneySettings s = JourneyData.settings();
        long now = server.overworld().getGameTime();
        for (Journey j : new ArrayList<>(journeys.values())) {
            switch (j.stage) {
                case PLANNING -> plan(server, j);
                case OUTBOUND, RETURNING -> {
                    j.done = Math.min(j.route.total(), j.done + j.speed * s.walkSpeed() * seconds);
                    setDirty();
                    if (j.done >= j.route.total()) {
                        arrive(server, j);
                    }
                }
                case DONE -> {
                    long at = doneAt.computeIfAbsent(j.id, k -> now);
                    if (now - at > KEEP_DONE) {
                        journeys.remove(j.id);
                        doneAt.remove(j.id);
                        setDirty();
                    }
                }
            }
        }
    }

    /** At the end of its route: its kind says what next. Bodies call this too. */
    public void arrive(MinecraftServer server, Journey j) {
        if (j.embodied) {
            ServerLevel level = server.getLevel(j.dimension);
            if (level != null) {
                JourneyBodies.dissolve(level, j);
            }
        }
        Optional<JourneyKind> kind = JourneyKind.of(j.kind);
        if (kind.isEmpty()) {
            LotrPort.LOG.warn("[journeys] {} has unknown kind {}; ending it", j.id, j.kind);
            finish(server, j);
            return;
        }
        kind.get().arrived(server, j);
        if (j.stage == Journey.Stage.DONE) {
            finish(server, j);
        }
        setDirty();
    }

    /** Everyone in it is gone. */
    public void lose(MinecraftServer server, Journey j) {
        JourneyKind.of(j.kind).ifPresent(k -> k.lost(server, j));
        finish(server, j);
    }

    private void plan(MinecraftServer server, Journey j) {
        CompletableFuture<Route> f = planning.get(j.id);
        if (f == null) {
            planning.put(j.id, planRoute(server, j.fromX, j.fromZ, j.toX, j.toZ));
            return;
        }
        if (!f.isDone()) {
            return;
        }
        planning.remove(j.id);
        Route r = f.getNow(Route.NONE);
        if (!r.found()) {
            JourneyKind.of(j.kind).ifPresent(k -> k.unreachable(server, j));
            finish(server, j);
            return;
        }
        j.route = r;
        j.done = 0;
        j.stage = Journey.Stage.OUTBOUND;
        j.started = server.overworld().getGameTime();
        JourneyKind.of(j.kind).ifPresent(k -> k.planned(server, j));
        setDirty();
    }

    /** Plans a route on the planner thread. */
    public static CompletableFuture<Route> planRoute(MinecraftServer server, int x1, int z1, int x2, int z2) {
        MapTerrain land = terrain(server);
        RoutePlanner.Settings ps = JourneyData.settings().planner();
        if (planner == null) {
            planner = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "lotr_port journey planner");
                t.setDaemon(true);
                return t;
            });
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return RoutePlanner.plan(land, ps, x1, z1, x2, z2);
            } catch (RuntimeException ex) {
                LotrPort.LOG.error("[journeys] planning failed", ex);
                return Route.NONE;
            }
        }, planner);
    }

    /** The land as routes see it now (bridges copied, so the planner thread never touches saved data). */
    public static MapTerrain terrain(MinecraftServer server) {
        return new MapTerrain(MiddleEarthMap.get(), Roads.get(), JourneyData.terrain(),
                Bridges.get(server).in(server.overworld().dimension()));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("journeys", Journey.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(journeys.values()))
                .getOrThrow());
        return tag;
    }

    public static Journeys load(CompoundTag tag, HolderLookup.Provider registries) {
        Journeys out = new Journeys();
        Journey.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("journeys"))
                .resultOrPartial(e -> LotrPort.LOG.error("[journeys] {}", e))
                .ifPresent(l -> l.forEach(j -> out.journeys.put(j.id, j)));
        return out;
    }
}
