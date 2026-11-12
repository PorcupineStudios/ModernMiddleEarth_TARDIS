package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.society.ComponentMap;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Owner;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.colony.ColonyData;
import com.porcupinestudios.lotrport.colony.ColonySystemsCore;
import com.porcupinestudios.lotrport.society.sim.ColonyScheduler;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.Perf;
import com.porcupinestudios.lotrport.society.sim.SimSettings;
import com.porcupinestudios.lotrport.society.sim.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The society core (colony-first architecture): Person records, owners, components, old saves moved over, and the
 * scale target (50 colonies x 150 people) saved and read back quickly.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SocietyTests {
    private static final ResourceLocation SOLDIER = LotrPort.id("gondor_soldier");
    private static final ResourceLocation GONDOR = LotrPort.id("gondor");

    private SocietyTests() {}

    @GameTest(template = "empty")
    public static void ownersAndComponentsSurviveSavingAndUnknownDataIsKept(GameTestHelper helper) {
        for (Owner o : List.of(Owner.player(UUID.randomUUID()), Owner.colony(UUID.randomUUID()),
                new Owner.OfRealm(LotrPort.id("realm/3")), Owner.NONE)) {
            helper.assertTrue(Owner.parse(o.encode()).result().equals(Optional.of(o)), "owner did not survive: " + o);
        }
        ComponentMap map = new ComponentMap();
        CompoundTag saved = new CompoundTag();
        CompoundTag unit = new CompoundTag();
        unit.putInt("v", 1);
        CompoundTag data = new CompoundTag();
        data.putString("order", "guard");
        data.putLong("hired", 42);
        unit.put("d", data);
        saved.put("lotr_port:unit", unit);
        CompoundTag future = new CompoundTag();
        future.putInt("v", 3);
        future.putString("d", "a feature this build does not have");
        saved.put("someaddon:morale", future);
        map = ComponentMap.load(saved);
        Optional<CoreComponents.Unit> read = map.get(CoreComponents.UNIT);
        helper.assertTrue(read.map(u -> u.order() == CoreComponents.Order.GUARD && u.hired() == 42).orElse(false),
                "the unit component was not read: " + read);
        CompoundTag again = map.save();
        helper.assertTrue(again.getCompound("someaddon:morale").equals(future),
                "data of an unknown component was not kept: " + again);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldSavesMoveIntoThePersonRegistry(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // version 1 People: one hired unit
        UUID unitId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        CompoundTag people = new CompoundTag();
        people.putInt("dataVersion", 1);
        ListTag list = new ListTag();
        CompoundTag rec = new CompoundTag();
        rec.putUUID("entity", unitId);
        rec.putString("type", SOLDIER.toString());
        rec.putString("name", "Beregond");
        rec.putString("faction", GONDOR.toString());
        rec.putUUID("owner", player);
        rec.putLong("since", 7);
        rec.putBoolean("alive", true);
        list.add(rec);
        people.put("people", list);
        Persons persons = Persons.load(people, server.registryAccess());
        Person p = persons.get(unitId).orElse(null);
        helper.assertTrue(p != null && p.owner().equals(Owner.player(player)) && p.has(CoreComponents.UNIT)
                && p.faction().equals(Optional.of(GONDOR)), "the hired unit did not move over: " + p);
        helper.assertTrue(persons.ownedBy(Owner.player(player), CoreComponents.UNIT).size() == 1,
                "the owner index misses the unit");

        // version 1 Colonies: a colony with a citizen and an applicant in its own lists
        UUID colonyId = UUID.randomUUID();
        UUID citizen = UUID.randomUUID();
        UUID applicant = UUID.randomUUID();
        CompoundTag c = new CompoundTag();
        c.put("id", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, colonyId).getOrThrow());
        c.putString("name", "Old Hold");
        c.put("founder", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, player).getOrThrow());
        c.putString("faction", GONDOR.toString());
        c.putString("dimension", Level.OVERWORLD.location().toString());
        c.put("hall", new IntArrayTag(new int[] {100000, 64, 100000}));
        ListTag citizens = new ListTag();
        citizens.add(legacy(citizen, "joined", 5, true));
        c.put("citizens", citizens);
        ListTag applicants = new ListTag();
        CompoundTag a = legacy(applicant, "days", 2, false);
        applicants.add(a);
        c.put("applicants", applicants);
        CompoundTag colonies = new CompoundTag();
        colonies.putInt("dataVersion", 1);
        ListTag cl = new ListTag();
        cl.add(c);
        colonies.put("colonies", cl);
        Colonies old = Colonies.load(colonies, server.registryAccess());
        Colony colony = old.get(colonyId).orElse(null);
        helper.assertTrue(colony != null, "the old colony did not load");
        old.migratePeople(server);
        Person cit = Society.person(server, citizen).orElse(null);
        helper.assertTrue(cit != null && cit.owner().equals(colony.asOwner())
                && cit.get(CoreComponents.CITIZEN).map(CoreComponents.Citizen::rare).orElse(false),
                "the old citizen did not move over: " + cit);
        Person app = Society.person(server, applicant).orElse(null);
        helper.assertTrue(app != null && app.get(CoreComponents.APPLICANT).map(x -> x.days() == 2).orElse(false),
                "the old applicant did not move over: " + app);
        helper.assertTrue(Society.citizenCount(server, colony) == 1, "citizens counted wrong");
        Society.persons(server).remove(server, citizen);
        Society.persons(server).remove(server, applicant);
        helper.succeed();
    }

    private static CompoundTag legacy(UUID id, String key, int value, boolean rare) {
        CompoundTag t = new CompoundTag();
        t.put("entity", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, id).getOrThrow());
        t.putString("type", SOLDIER.toString());
        t.putString("name", "Old " + key);
        if ("joined".equals(key)) {
            t.putLong(key, value);
            t.putBoolean("rare", rare);
        } else {
            t.putInt(key, value);
        }
        return t;
    }

    /** The scale target: 50 colonies x 150 people saved, read back, and queried by colony, within a time budget. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fiftyColoniesOfOneHundredFiftyPeopleSaveAndLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Persons persons = new Persons();
        List<UUID> colonies = new ArrayList<>();
        for (int c = 0; c < 50; c++) {
            UUID colony = UUID.randomUUID();
            colonies.add(colony);
            for (int i = 0; i < 150; i++) {
                Person p = persons.migrated(UUID.randomUUID(), SOLDIER, "P" + c + "_" + i, i, Owner.colony(colony));
                persons.setQuiet(p, CoreComponents.CITIZEN, new CoreComponents.Citizen(i, i % 50 == 0));
                if (i % 10 == 0) {
                    persons.setQuiet(p, CoreComponents.UNIT, CoreComponents.Unit.hired(i).withWage(2));
                }
            }
        }
        long t0 = System.nanoTime();
        CompoundTag saved = persons.save(new CompoundTag(), server.registryAccess());
        long t1 = System.nanoTime();
        Persons back = Persons.load(saved, server.registryAccess());
        long t2 = System.nanoTime();
        int citizens = 0;
        int soldiers = 0;
        for (UUID c : colonies) {
            citizens += back.count(Owner.colony(c), CoreComponents.CITIZEN);
            soldiers += back.ownedBy(Owner.colony(c), CoreComponents.UNIT).size();
        }
        long t3 = System.nanoTime();
        LotrPort.LOG.info("[society] 7500 persons: save {} ms, load {} ms, 100 colony queries {} us",
                (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1000);
        helper.assertTrue(back.size() == 7500, "persons after loading: " + back.size());
        helper.assertTrue(citizens == 7500 && soldiers == 750, "citizens " + citizens + ", soldiers " + soldiers);
        helper.assertTrue((t3 - t2) < 20_000_000L, "colony queries took " + (t3 - t2) / 1000 + " us");
        helper.assertTrue((t2 - t0) < 2_000_000_000L, "saving and loading took " + (t2 - t0) / 1_000_000 + " ms");
        helper.succeed();
    }

    /**
     * Done-when of the scheduler step: 50 unloaded colonies of 150 people (15 paid soldiers each) run 10 colony days
     * through the real scheduler, staying under the per-tick budget, and pay exactly what they owe.
     */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void fiftyColoniesRunTenDaysWithinTheBudget(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Colonies colonies = Colonies.get(server);
        Persons persons = Persons.get(server);
        List<Colony> mine = new ArrayList<>();
        List<UUID> people = new ArrayList<>();
        for (int c = 0; c < 50; c++) {
            Colony colony = colonies.foundAi(server, UUID.randomUUID(), UUID.randomUUID(), "Bench " + c,
                    Level.OVERWORLD, new BlockPos(3_000_000 + c * 400, 70, 3_000_000), GONDOR, 0).orElseThrow();
            // a player's colony, so every life system runs: 10 houses of 15 beds, 5 farmsteads, 3 kitchens, a
            // storehouse (records only: these colonies are unloaded), and bread in the stock
            colonies.transfer(server, colony, UUID.randomUUID(), GONDOR, false, "benchmark");
            colonies.set(colony, CoreComponents.TREASURY, new CoreComponents.Treasury(1000));
            com.porcupinestudios.lotrport.colony.building.Buildings b =
                    com.porcupinestudios.lotrport.colony.building.Buildings.NONE;
            for (int k = 0; k < 19; k++) {
                String type = k < 10 ? "house" : k < 15 ? "farmstead" : k < 18 ? "kitchen" : "storehouse";
                b = b.with(new com.porcupinestudios.lotrport.colony.building.Building(UUID.randomUUID(),
                        LotrPort.id(type), colony.hall().offset(k * 8, 0, 20), Optional.empty(),
                        colony.hall().offset(k * 8, 0, 21), new int[] {0},
                        java.util.Map.of("beds", type.equals("house") ? 15 : 0, "chests", 1), 50, 0, 0));
            }
            colonies.set(colony, com.porcupinestudios.lotrport.colony.building.Buildings.TYPE, b);
            colonies.set(colony, com.porcupinestudios.lotrport.colony.work.WorkComponents.STOCK,
                    new com.porcupinestudios.lotrport.colony.work.WorkComponents.Stock(java.util.Map.of(
                            ResourceLocation.withDefaultNamespace("bread"), 5000)));
            mine.add(colony);
            for (int i = 0; i < 150; i++) {
                Person p = persons.migrated(UUID.randomUUID(), SOLDIER, "B" + c + "_" + i, 0, colony.asOwner());
                persons.setQuiet(p, CoreComponents.CITIZEN, new CoreComponents.Citizen(0, false));
                if (i % 10 == 0) {
                    persons.setQuiet(p, CoreComponents.UNIT, CoreComponents.Unit.hired(0).withWage(2));
                }
                people.add(p.id());
            }
        }
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        mine.forEach(c -> ids.add(c.id()));
        ColonyScheduler scheduler = new ColonyScheduler();
        // measured as a light server would be: a day that misses its tick's budget waits for the next
        scheduler.forceLoad(ColonyScheduler.Load.NORMAL);
        long dayTicks = ColonyData.settings().dayTicks();
        long start = (colonies.lastDay(mine.get(0)) + 1) * dayTicks;
        try {
            // a warm-up day (the JIT compiles the hot paths), then the measured ten
            for (long t = start; t < start + dayTicks; t++) {
                scheduler.tick(server, t, c -> ids.contains(c.id()));
            }
            scheduler.perf().reset();
            for (long t = start + dayTicks; t < start + 11 * dayTicks; t++) {
                scheduler.tick(server, t, c -> ids.contains(c.id()));
            }
            // a little longer, for days that waited in the backlog to run
            long end = start + 11 * dayTicks + SimSettings.get().pulseTicks() * 3L;
            for (long t = start + 11 * dayTicks; t < end; t++) {
                scheduler.tick(server, t, c -> ids.contains(c.id()));
            }
            Perf.Stat ticks = scheduler.perf().ticks();
            double meanMs = ticks.meanMicros() / 1000.0;
            double maxMs = ticks.maxNanos / 1_000_000.0;
            StringBuilder per = new StringBuilder();
            long dayNanos = 0;
            for (var e : scheduler.perf().systems().entrySet()) {
                per.append(String.format(java.util.Locale.ROOT, " %s %.1f/%.1f us", e.getKey(),
                        e.getValue().meanMicros(), e.getValue().maxNanos / 1000.0));
                if (ColonyScheduler.plan(ColonySystem.Cadence.DAY, Tier.UNLOADED).contains(e.getKey())) {
                    dayNanos += e.getValue().totalNanos;
                }
            }
            // the cost of a colony day in units of a fixed reference job timed now on this machine, so the check
            // holds on a slow CI runner and a fast PC alike (ticks over budget and milliseconds don't)
            double calib = calibrationNanos();
            long colonyDays = 50L * 10;
            double perDay = dayNanos / (double) colonyDays;
            double units = perDay / calib;
            LotrPort.LOG.info(String.format(java.util.Locale.ROOT,
                    "[society] 50 x 150, 10 days: %d ticks, %.4f ms mean, %.3f ms longest, %d over budget, %d days "
                            + "waited, %d caught up; a colony day %.1f us = %.2f reference jobs (%.1f us each);%s",
                    ticks.runs, meanMs, maxMs, scheduler.perf().overBudget(), scheduler.perf().deferredDays(),
                    scheduler.perf().caughtUpDays(), perDay / 1000, units, calib / 1000, per));
            for (Colony c : mine) {
                helper.assertTrue(c.tier(server) == Tier.UNLOADED, c.name() + " is not unloaded");
                List<Person> people2 = Society.citizens(server, c);
                long working = people2.stream().filter(p -> p.has(
                        com.porcupinestudios.lotrport.colony.work.WorkComponents.JOB)).count();
                long housed = people2.stream().filter(p -> p.has(
                        com.porcupinestudios.lotrport.colony.work.WorkComponents.HOME)).count();
                // 5 farmsteads and 3 kitchens of quality 50 take 3 each, the storehouse 2 couriers; soldiers never
                // work; 150 beds house everyone
                helper.assertTrue(people2.size() == 150 && working == 26 && housed == 150,
                        c.name() + ": " + people2.size() + " citizens, " + working + " working, " + housed + " housed");
            }
            // every colony ran all ten days, waiting or not
            long lastDay = (end - 1) / dayTicks;
            for (Colony c : mine) {
                helper.assertTrue(colonies.lastDay(c) == lastDay, c.name() + " is behind: day " + colonies.lastDay(c)
                        + " of " + lastDay);
            }
            helper.assertTrue(scheduler.backlog() == 0, scheduler.backlog() + " colony days still waiting");
            // a colony day of 150 people may cost at most MAX_DAY_UNITS reference jobs (measured 2026-11-12: see
            // the log line; raise it only for work that is worth it)
            helper.assertTrue(units <= MAX_DAY_UNITS, String.format(java.util.Locale.ROOT,
                    "a colony day costs %.2f reference jobs (limit %.1f): %.1f us against %.1f us", units,
                    MAX_DAY_UNITS, perDay / 1000, calib / 1000));

            // a colony unloaded for 40 days catches up at most max_catch_up_days at once, and pays what it can
            Colony late = mine.get(0);
            colonies.set(late, CoreComponents.TREASURY, new CoreComponents.Treasury(100));
            // only the soldiers draw on its treasury here: no workplaces, no head tax
            colonies.set(late, com.porcupinestudios.lotrport.colony.building.Buildings.TYPE,
                    com.porcupinestudios.lotrport.colony.building.Buildings.NONE);
            colonies.set(late, com.porcupinestudios.lotrport.colony.work.WorkComponents.ECONOMY,
                    new com.porcupinestudios.lotrport.colony.work.WorkComponents.Economy(0, 0));
            long later = start + 51 * dayTicks;
            scheduler.pass(server, colonies, late, later, SimSettings.get());
            int catchUp = SimSettings.get().maxCatchUpDays();
            long left = late.get(CoreComponents.TREASURY).map(CoreComponents.Treasury::coins).orElse(-1L);
            // 100 coins pay one soldier all 30 days (60) and a second 20; the other 14 go unpaid and desert
            int stayed = Society.soldiers(server, late).size();
            helper.assertTrue(catchUp == 30 && left == 0 && stayed == 1,
                    "catch-up: " + left + " coins left, " + stayed + " soldiers stayed");
        } finally {
            people.forEach(id -> persons.remove(server, id));
            mine.forEach(c -> colonies.disband(server, c, "test"));
        }
        helper.succeed();
    }

    /** Most a colony day of 150 people may cost, in reference jobs ({@link #calibrationNanos}). */
    static final double MAX_DAY_UNITS = 4.0;

    /**
     * The reference job: what a colony day does most (maps keyed by id, lists, sorting, small objects), at a fixed
     * size, timed best of 9 after a warm-up. Its time moves with the machine as colony work does.
     */
    static double calibrationNanos() {
        long best = Long.MAX_VALUE;
        long sink = 0;
        for (int run = 0; run < 12; run++) {
            long t = System.nanoTime();
            java.util.Random r = new java.util.Random(42);
            java.util.Map<UUID, double[]> map = new java.util.HashMap<>();
            List<UUID> ids = new ArrayList<>(3000);
            for (int i = 0; i < 3000; i++) {
                UUID id = new UUID(r.nextLong(), r.nextLong());
                ids.add(id);
                map.put(id, new double[] {r.nextDouble(), r.nextDouble()});
            }
            ids.sort(java.util.Comparator.naturalOrder());
            for (UUID id : ids) {
                double[] v = map.get(id);
                sink += (long) (v[0] * 1000 + v[1]);
            }
            long took = System.nanoTime() - t;
            if (run >= 3) {
                best = Math.min(best, took);
            }
        }
        if (sink == 42) {
            LotrPort.LOG.debug("calibration sink {}", sink);
        }
        return best;
    }

    /**
     * A busy server holds colony days back and a quiet one catches them up, none lost: under a heavy load no day
     * runs; when the load lifts every colony runs all the days that went by, in one pass each.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void colonyDaysWaitOutABusyServer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Colonies colonies = Colonies.get(server);
        List<Colony> mine = new ArrayList<>();
        for (int c = 0; c < 3; c++) {
            mine.add(colonies.foundAi(server, UUID.randomUUID(), UUID.randomUUID(), "Busy " + c, Level.OVERWORLD,
                    new BlockPos(4_000_000 + c * 400, 70, 4_000_000), GONDOR, 0).orElseThrow());
        }
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        mine.forEach(c -> ids.add(c.id()));
        ColonyScheduler scheduler = new ColonyScheduler();
        long dayTicks = ColonyData.settings().dayTicks();
        try {
            long start = (colonies.lastDay(mine.get(0)) + 1) * dayTicks;
            scheduler.forceLoad(ColonyScheduler.Load.HEAVY);
            for (long t = start; t < start + 2 * dayTicks; t++) {
                scheduler.tick(server, t, c -> ids.contains(c.id()));
            }
            long before = start / dayTicks - 1;
            for (Colony c : mine) {
                helper.assertTrue(colonies.lastDay(c) == before, c.name() + " ran a day under heavy load");
            }
            helper.assertTrue(scheduler.backlog() == 3, "waiting: " + scheduler.backlog());
            scheduler.forceLoad(ColonyScheduler.Load.LIGHT);
            long now = start + 2 * dayTicks;
            for (long t = now; t < now + SimSettings.get().pulseTicks(); t++) {
                scheduler.tick(server, t, c -> ids.contains(c.id()));
            }
            for (Colony c : mine) {
                helper.assertTrue(colonies.lastDay(c) == now / dayTicks, c.name() + " didn't catch up: day "
                        + colonies.lastDay(c) + " of " + now / dayTicks);
            }
            helper.assertTrue(scheduler.backlog() == 0, "still waiting: " + scheduler.backlog());
        } finally {
            mine.forEach(c -> colonies.disband(server, c, "test"));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void daySystemsRunInOrder(GameTestHelper helper) {
        List<ResourceLocation> order = ColonyScheduler.plan(ColonySystem.Cadence.DAY, Tier.ACTIVE);
        helper.assertTrue(order.indexOf(ColonySystemsCore.APPLICANTS) < order.indexOf(ColonySystemsCore.MIGRATION)
                && order.indexOf(ColonySystemsCore.MIGRATION) < order.indexOf(ColonySystemsCore.RECRUITS),
                "day systems out of order: " + order);
        helper.assertTrue(!ColonyScheduler.plan(ColonySystem.Cadence.DAY, Tier.UNLOADED)
                .contains(ColonySystemsCore.MIGRATION), "migration runs in unloaded colonies");
        helper.assertTrue(SimSettings.loadProblems().isEmpty(), "settings: " + SimSettings.loadProblems());
        helper.succeed();
    }
}
