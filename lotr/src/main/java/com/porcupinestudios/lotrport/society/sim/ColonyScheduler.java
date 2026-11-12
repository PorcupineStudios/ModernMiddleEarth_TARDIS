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
 *
 * <p>It gives way to a busy server ({@link SimSettings.Load}). The server's own tick time (without colony work) is
 * smoothed into a {@link Load}: when it is heavy (portals, chunk generation, other mods), colony days that fall due
 * are not run but put in a backlog, and only colonies a player is in get their pulses (their bodies); when the server
 * is falling behind, only those pulses run. A day that would start after the tick's budget is spent waits in the
 * backlog too. Nothing is lost by waiting: a colony day is marked done only when it runs, and then it runs for every
 * day that went by (up to {@code max_catch_up_days}). In light ticks the backlog is worked off with up to
 * {@code light_budget_factor} times the budget; in normal ticks with what is left of the budget.
 */
public final class ColonyScheduler {
    private static final Map<MinecraftServer, ColonyScheduler> BY_SERVER = new WeakHashMap<>();

    /** How busy the server is. */
    public enum Load {
        /** Time to spare: catch up the backlog. */
        LIGHT,
        NORMAL,
        /** Colony days wait; only colonies a player is in get pulses. */
        HEAVY,
        /** Only colonies a player is in get pulses. */
        OVERLOADED
    }

    private final Perf perf = new Perf();
    private List<UUID>[] buckets;
    private int bucketsFor = -1;
    private int bucketsPulse = -1;
    /** Colonies whose day fell due but waited, oldest first. */
    private final java.util.LinkedHashSet<UUID> backlog = new java.util.LinkedHashSet<>();
    /** The server's tick time without colony work, smoothed (ms); -1 until measured. */
    private double serverTickMs = -1;
    private long tickStartNanos;
    private Load forced;

    /** A scheduler of its own (benchmarks); the server's is {@link #get}. */
    public ColonyScheduler() {}

    public static synchronized ColonyScheduler get(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, s -> new ColonyScheduler());
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ColonyScheduler::onTickStart);
        NeoForge.EVENT_BUS.addListener(ColonyScheduler::onTick);
    }

    public Perf perf() {
        return perf;
    }

    private static void onTickStart(ServerTickEvent.Pre event) {
        get(event.getServer()).tickStartNanos = System.nanoTime();
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ColonyScheduler s = get(server);
        if (s.tickStartNanos != 0) {
            s.observe((System.nanoTime() - s.tickStartNanos) / 1_000_000.0, SimSettings.get().load());
        }
        s.tick(server, server.overworld().getGameTime());
    }

    /** Feeds one server tick's time (ms, without colony work) into the smoothed tick time. */
    public void observe(double tickMs, SimSettings.Load l) {
        double a = Math.max(0, Math.min(1, l.smoothing()));
        serverTickMs = serverTickMs < 0 ? tickMs : serverTickMs * (1 - a) + tickMs * a;
    }

    /** Pins the load (tests, {@code /lotr colony load}); null goes back to measuring. */
    public void forceLoad(Load load) {
        forced = load;
    }

    /** The load colony work answers to now. */
    public Load load(SimSettings s) {
        if (forced != null) {
            return forced;
        }
        SimSettings.Load l = s.load();
        if (serverTickMs < 0 || serverTickMs < l.lightMs()) {
            return Load.LIGHT;
        }
        return serverTickMs >= l.overloadedMs() ? Load.OVERLOADED : serverTickMs >= l.heavyMs() ? Load.HEAVY
                : Load.NORMAL;
    }

    public double serverTickMs() {
        return serverTickMs;
    }

    /** Colonies waiting for their day to run. */
    public int backlog() {
        return backlog.size();
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
        Load load = load(s);
        long budget = (long) (s.budgetMs() * 1_000_000);
        for (UUID id : buckets[(int) Math.floorMod(now, (long) pulse)]) {
            Colony c = colonies.get(id).orElse(null);
            if (c != null && (only == null || only.test(c))) {
                pass(server, colonies, c, now, s, load, start, budget);
            }
        }
        // the backlog, with what the tick has left (more when the server has time to spare)
        if ((load == Load.LIGHT || load == Load.NORMAL) && !backlog.isEmpty()) {
            long limit = load == Load.LIGHT ? (long) (budget * Math.max(1, s.load().lightBudgetFactor())) : budget;
            java.util.Iterator<UUID> it = backlog.iterator();
            while (it.hasNext() && System.nanoTime() - start < limit) {
                Colony c = colonies.get(it.next()).orElse(null);
                if (c == null) {
                    it.remove();
                    continue;
                }
                if (only != null && !only.test(c)) {
                    continue;
                }
                it.remove();
                ServerLevel level = server.getLevel(c.dimension());
                runDays(server, colonies, c, level, tier(level, c, s), now, s);
                perf.caughtUp();
            }
        }
        perf.tick(System.nanoTime() - start, budget);
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

    /** One pass over a colony, whatever the load: pulse systems, then day systems if days went by (tests). */
    public void pass(MinecraftServer server, Colonies colonies, Colony colony, long now, SimSettings s) {
        pass(server, colonies, colony, now, s, Load.NORMAL, System.nanoTime(), Long.MAX_VALUE);
    }

    private void pass(MinecraftServer server, Colonies colonies, Colony colony, long now, SimSettings s, Load load,
            long tickStart, long budget) {
        ServerLevel level = server.getLevel(colony.dimension());
        Tier tier = tier(level, colony, s);
        boolean due = now / Math.max(1, ColonyData.settings().dayTicks()) > colonies.lastDay(colony);
        if (load == Load.HEAVY || load == Load.OVERLOADED) {
            // a busy server: only the bodies of colonies a player is in; their days wait
            if (tier == Tier.ACTIVE) {
                run(ColonySystem.Cadence.PULSE, new SystemContext(server, colony, level, tier, now, 0), s);
            }
            if (due && backlog.add(colony.id())) {
                perf.deferred();
            }
            return;
        }
        run(ColonySystem.Cadence.PULSE, new SystemContext(server, colony, level, tier, now, 0), s);
        if (!due) {
            return;
        }
        if (System.nanoTime() - tickStart > budget) {
            // the tick's budget is spent: this day waits for a later tick
            if (backlog.add(colony.id())) {
                perf.deferred();
            }
            return;
        }
        backlog.remove(colony.id());
        runDays(server, colonies, colony, level, tier, now, s);
    }

    /** The day systems for every day that went by since the colony's last (up to the catch-up cap). */
    private void runDays(MinecraftServer server, Colonies colonies, Colony colony, ServerLevel level, Tier tier,
            long now, SimSettings s) {
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
