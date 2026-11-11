package com.porcupinestudios.lotrport.society.sim;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.ColonyData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Runs the {@link ColonySystem}s. Each colony gets a pass once every {@code pulse_ticks}; colonies are spread evenly
 * over those ticks, so 50 colonies with 100-tick pulses cost one colony every other tick. A pass decides the
 * colony's {@link Tier}, runs the pulse systems of that tier, and if a colony day (or several, for a colony that was
 * unloaded) went by, the day systems with the number of days. Timing goes to {@link Perf}.
 */
public final class ColonyScheduler {
    private static final Map<MinecraftServer, ColonyScheduler> BY_SERVER = new WeakHashMap<>();

    private final Perf perf = new Perf();
    private List<UUID>[] buckets;
    private int bucketsFor = -1;
    private int bucketsPulse = -1;

    /** A scheduler of its own (benchmarks); the server's is {@link #get}. */
    public ColonyScheduler() {}

    public static synchronized ColonyScheduler get(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, s -> new ColonyScheduler());
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ColonyScheduler::onTick);
    }

    public Perf perf() {
        return perf;
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        get(server).tick(server, server.overworld().getGameTime());
    }

    /** One server tick at game time {@code now}: passes for the colonies whose turn it is. */
    public void tick(MinecraftServer server, long now) {
        tick(server, now, null);
    }

    /** As {@link #tick(MinecraftServer, long)}, but only for colonies {@code only} accepts (null = all). */
    public void tick(MinecraftServer server, long now, java.util.function.Predicate<Colony> only) {
        long start = System.nanoTime();
        Colonies colonies = Colonies.get(server);
        SimSettings s = SimSettings.get();
        int pulse = Math.max(1, s.pulseTicks());
        if (bucketsFor != colonies.version() || bucketsPulse != pulse) {
            rebuild(colonies, pulse);
        }
        for (UUID id : buckets[(int) Math.floorMod(now, (long) pulse)]) {
            Colony c = colonies.get(id).orElse(null);
            if (c != null && (only == null || only.test(c))) {
                pass(server, colonies, c, now, s);
            }
        }
        perf.tick(System.nanoTime() - start);
    }

    @SuppressWarnings("unchecked")
    private void rebuild(Colonies colonies, int pulse) {
        List<Colony> all = new ArrayList<>(colonies.all());
        all.sort(Comparator.comparing(Colony::id));
        buckets = new List[pulse];
        for (int i = 0; i < pulse; i++) {
            buckets[i] = new ArrayList<>();
        }
        int n = all.size();
        for (int i = 0; i < n; i++) {
            buckets[(int) ((long) i * pulse / n)].add(all.get(i).id());
        }
        bucketsFor = colonies.version();
        bucketsPulse = pulse;
    }

    /** One pass over a colony: pulse systems, then day systems if days went by. */
    public void pass(MinecraftServer server, Colonies colonies, Colony colony, long now, SimSettings s) {
        ServerLevel level = server.getLevel(colony.dimension());
        Tier tier = tier(level, colony, s);
        SystemContext pulse = new SystemContext(server, colony, level, tier, now, 0);
        run(ColonySystem.Cadence.PULSE, pulse, s);
        long day = now / Math.max(1, ColonyData.settings().dayTicks());
        long last = colonies.lastDay(colony);
        if (day > last) {
            int days = (int) Math.min(day - last, Math.max(1, s.maxCatchUpDays()));
            colonies.markDay(colony, day);
            run(ColonySystem.Cadence.DAY, new SystemContext(server, colony, level, tier, now, days), s);
        }
    }

    /** Runs the day systems for one colony now (tests, {@code /lotr colony day}); returns the context's counts. */
    public SystemContext runDay(MinecraftServer server, Colony colony, int days) {
        ServerLevel level = server.getLevel(colony.dimension());
        SimSettings s = SimSettings.get();
        SystemContext ctx = new SystemContext(server, colony, level, tier(level, colony, s),
                server.overworld().getGameTime(), days);
        run(ColonySystem.Cadence.DAY, ctx, s);
        return ctx;
    }

    private void run(ColonySystem.Cadence cadence, SystemContext ctx, SimSettings s) {
        for (ColonySystem system : ColonySystems.ordered(cadence)) {
            if (!system.tiers().contains(ctx.tier()) || s.disabled().contains(system.id())) {
                continue;
            }
            long t = System.nanoTime();
            try {
                system.run(ctx);
            } catch (RuntimeException e) {
                LotrPort.LOG.error("[colony systems] {} failed on {}", system.id(), ctx.colony().name(), e);
            }
            perf.system(system.id(), System.nanoTime() - t);
        }
    }

    public static Tier tier(ServerLevel level, Colony colony, SimSettings s) {
        if (level == null || !level.hasChunkAt(colony.hall())) {
            return Tier.UNLOADED;
        }
        double r2 = (double) s.activeRadius() * s.activeRadius();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.blockPosition().distSqr(colony.hall()) <= r2) {
                return Tier.ACTIVE;
            }
        }
        return Tier.LOADED;
    }

    /** Ids of the systems that would run for a cadence and tier (for {@code /lotr colony perf}). */
    public static List<ResourceLocation> plan(ColonySystem.Cadence cadence, Tier tier) {
        Set<ResourceLocation> off = Set.copyOf(SimSettings.get().disabled());
        return ColonySystems.ordered(cadence).stream().filter(x -> x.tiers().contains(tier))
                .map(ColonySystem::id).filter(id -> !off.contains(id)).toList();
    }
}
