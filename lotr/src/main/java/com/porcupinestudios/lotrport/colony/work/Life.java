package com.porcupinestudios.lotrport.colony.work;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.ColonySystemsCore;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.ColonySystems;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import com.porcupinestudios.lotrport.society.sim.Tier;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A colony's day (RingRealms P2-P3 as numbers, in every tier): homes and jobs, work, wages and taxes, food and
 * happiness. Each is its own system, in that order; switch any off in {@code colony_systems/settings.json}. Workers
 * make goods from their job's {@code produces}, scaled by race proficiency and skill; goods go to the workplace's
 * chests, then storehouses, then the colony stock. Nobody walks to a tree yet: in-person work comes with bodies.
 */
public final class Life {
    public static final ResourceLocation HOMES_JOBS = LotrPort.id("homes_jobs");
    public static final ResourceLocation WORK = LotrPort.id("work");
    public static final ResourceLocation PAY = LotrPort.id("wages_taxes");
    public static final ResourceLocation NEEDS = LotrPort.id("needs");
    public static final ResourceLocation LEAVE_HISTORY = LotrPort.id("citizen_leave");
    /** Counts: items made, wages paid, taxes taken, food eaten, citizens who left. */
    public static final ResourceLocation MADE = LotrPort.id("items_made");
    public static final ResourceLocation WAGES = LotrPort.id("wages");
    public static final ResourceLocation TAXES = LotrPort.id("taxes");
    public static final ResourceLocation EATEN = LotrPort.id("food_eaten");
    public static final ResourceLocation LEFT = LotrPort.id("citizens_left");
    public static final ResourceLocation CARRIED = LotrPort.id("items_carried");

    private Life() {}

    public static void register() {
        WorkComponents.init();
        Races.register();
        JobDef.register();
        LifeSettings.register();
        NeoForge.EVENT_BUS.addListener(Assign::onLost);
        ColonySystems.register(ColonySystem.of(HOMES_JOBS, ColonySystem.Cadence.DAY, Tier.ALL,
                Set.of(ColonySystemsCore.APPLICANTS), ctx -> {
                    if (!ctx.colony().ai()) {
                        Assign.run(ctx.server(), ctx.colony(), ctx.now());
                    }
                }));
        ColonySystems.register(ColonySystem.of(WORK, ColonySystem.Cadence.DAY, Tier.ALL, Set.of(HOMES_JOBS),
                Life::work));
        ColonySystems.register(ColonySystem.of(PAY, ColonySystem.Cadence.DAY, Tier.ALL, Set.of(WORK), Life::pay));
        ColonySystems.register(ColonySystem.of(NEEDS, ColonySystem.Cadence.DAY, Tier.ALL, Set.of(PAY), Life::needs));
        ColonySystems.register(ColonySystem.of(Bodies.SYSTEM, ColonySystem.Cadence.PULSE, Set.of(Tier.ACTIVE),
                Set.of(), Bodies::run));
    }

    /** Workers who aren't striking or starving make their job's goods. */
    static void work(SystemContext ctx) {
        if (ctx.colony().ai()) {
            return;
        }
        LifeSettings s = LifeSettings.get();
        Persons persons = Persons.get(ctx.server());
        Buildings buildings = Buildings.of(ctx.colony());
        Stores stores = new Stores(ctx.server(), ctx.level(), ctx.colony());
        stores.flush();
        // gatherers first, then couriers carry, then the trades that use what was gathered
        List<Person> citizens = Society.citizens(ctx.server(), ctx.colony());
        for (int phase = 0; phase < 3; phase++) {
            for (Person p : citizens) {
                Optional<WorkComponents.Job> job = p.get(WorkComponents.JOB);
                Optional<JobDef> def = job.flatMap(j -> JobDef.get(j.job()));
                if (def.isEmpty() || phase(def.get()) != phase) {
                    continue;
                }
                worker(ctx, s, persons, buildings, stores, p, job.get(), def.get());
            }
        }
        stores.save();
    }

    /** 0 gathers from the land, 1 carries, 2 turns goods into others. */
    static int phase(JobDef def) {
        return def.carries() > 0 ? 1 : def.consumes().isEmpty() ? 0 : 2;
    }

    private static void worker(SystemContext ctx, LifeSettings s, Persons persons, Buildings buildings, Stores stores,
            Person p, WorkComponents.Job job, JobDef def) {
        WorkComponents.Needs needs = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
        Optional<Building> place = buildings.get(job.building());
        if (needs.unpaid() > 0 || needs.food() <= 0 || place.isEmpty()) {
            return;
        }
        double factor = def.proficiency(Races.of(p.type())) * (0.5 + job.skill() / 100.0) * ctx.days();
        Map<ResourceLocation, Double> carry = new HashMap<>(job.carry());
        int made = 0;
        if (def.carries() > 0) {
            ctx.count(CARRIED, stores.carry((int) Math.round(def.carries() * factor), buildings));
        } else if (def.consumes().isEmpty()) {
            for (Map.Entry<ResourceLocation, Double> e : def.produces().entrySet()) {
                double amount = e.getValue() * factor + carry.getOrDefault(e.getKey(), 0.0);
                int whole = (int) Math.floor(amount);
                carry.put(e.getKey(), amount - whole);
                if (whole > 0) {
                    stores.put(e.getKey(), whole, place.get());
                    made += whole;
                }
            }
        } else {
            double want = def.batches() * factor + carry.getOrDefault(WORK, 0.0);
            int batches = (int) Math.floor(want);
            for (Map.Entry<ResourceLocation, Integer> in : def.consumes().entrySet()) {
                batches = Math.min(batches, stores.count(in.getKey(), place.get()) / Math.max(1, in.getValue()));
            }
            carry.put(WORK, batches == (int) Math.floor(want) ? want - batches : 0.0);
            if (batches > 0) {
                for (Map.Entry<ResourceLocation, Integer> in : def.consumes().entrySet()) {
                    stores.take(in.getKey(), in.getValue() * batches, place.get());
                }
                for (Map.Entry<ResourceLocation, Double> out : def.produces().entrySet()) {
                    int n = (int) Math.round(out.getValue() * batches);
                    stores.put(out.getKey(), n, place.get());
                    made += n;
                }
            }
        }
        persons.setQuiet(p, WorkComponents.JOB, job.worked(Math.min(100, job.skill() + s.skillPerDay() * ctx.days()),
                carry));
        ctx.count(MADE, made);
    }

    /**
     * Wages from the treasury into purses (unpaid workers strike), the head tax from purses into the treasury, and a
     * coin each at a staffed tavern.
     */
    static void pay(SystemContext ctx) {
        if (ctx.colony().ai()) {
            return;
        }
        TAVERN.clear();
        Persons persons = Persons.get(ctx.server());
        Colonies colonies = Colonies.get(ctx.server());
        WorkComponents.Economy eco = ctx.colony().get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
        long treasury = ctx.colony().get(CoreComponents.TREASURY).orElse(CoreComponents.Treasury.EMPTY).coins();
        long start = treasury;
        List<Person> citizens = Society.citizens(ctx.server(), ctx.colony());
        boolean tavern = false;
        for (Person p : citizens) {
            Optional<WorkComponents.Job> job = p.get(WorkComponents.JOB);
            if (job.isEmpty()) {
                continue;
            }
            Optional<JobDef> def = JobDef.get(job.get().job());
            WorkComponents.Needs needs = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
            tavern |= def.map(JobDef::tavern).orElse(false) && needs.unpaid() == 0;
            int wage = Math.max(0, def.map(JobDef::wage).orElse(0) + eco.wageBonus());
            long owed = (long) wage * ctx.days();
            long purse = p.get(WorkComponents.PURSE).orElse(WorkComponents.Purse.EMPTY).coins();
            if (treasury >= owed) {
                treasury -= owed;
                persons.setQuiet(p, WorkComponents.PURSE, new WorkComponents.Purse(purse + owed));
                if (needs.unpaid() != 0) {
                    persons.setQuiet(p, WorkComponents.NEEDS, needs.withUnpaid(0));
                }
                ctx.count(WAGES, (int) owed);
            } else {
                persons.setQuiet(p, WorkComponents.NEEDS, needs.withUnpaid(needs.unpaid() + ctx.days()));
            }
        }
        for (Person p : citizens) {
            if (p.has(CoreComponents.UNIT)) {
                continue;
            }
            long purse = p.get(WorkComponents.PURSE).orElse(WorkComponents.Purse.EMPTY).coins();
            long tax = Math.min(purse, (long) Math.max(0, eco.headTax()) * ctx.days());
            long drink = tavern && purse - tax > 0 ? 1 : 0;
            if (tax + drink > 0) {
                persons.setQuiet(p, WorkComponents.PURSE, new WorkComponents.Purse(purse - tax - drink));
                treasury += tax + drink;
                ctx.count(TAXES, (int) tax);
            }
            if (drink > 0) {
                TAVERN.add(p.id());
            }
        }
        if (treasury != start) {
            colonies.set(ctx.colony(), CoreComponents.TREASURY, new CoreComponents.Treasury(treasury));
        }
    }

    /** Who drank at a tavern this pass (read by {@link #needs} right after, then cleared). */
    private static final java.util.Set<java.util.UUID> TAVERN = new java.util.HashSet<>();

    /** Citizens eat from the storehouses; happiness moves toward its target; the long unhappy leave. */
    static void needs(SystemContext ctx) {
        if (ctx.colony().ai()) {
            TAVERN.clear();
            return;
        }
        LifeSettings s = LifeSettings.get();
        Persons persons = Persons.get(ctx.server());
        Buildings buildings = Buildings.of(ctx.colony());
        Stores stores = new Stores(ctx.server(), ctx.level(), ctx.colony());
        WorkComponents.Economy eco = ctx.colony().get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
        for (Person p : Society.citizens(ctx.server(), ctx.colony())) {
            if (p.has(CoreComponents.UNIT)) {
                continue;
            }
            WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
            double food = n.food();
            for (int d = 0; d < ctx.days(); d++) {
                food = Math.max(0, food - s.foodPerDay());
                while (food < s.eatBelow()) {
                    int nutrition = stores.eatOne();
                    if (nutrition <= 0) {
                        break;
                    }
                    ctx.count(EATEN, 1);
                    food = Math.min(20, food + nutrition * s.nutritionFactor());
                }
            }
            Optional<Building> home = p.get(WorkComponents.HOME).flatMap(h -> buildings.get(h.building()));
            double target = s.happinessBase()
                    + home.map(h -> s.homeHappiness() + h.quality() * s.homeQualityFactor()).orElse(0.0)
                    + (food >= s.eatBelow() ? s.fedHappiness() : food > 0 ? s.fedHappiness() / 2 : 0)
                    + (TAVERN.contains(p.id()) ? s.tavernHappiness() : 0)
                    - (n.unpaid() > 0 ? s.unpaidHappiness() : 0)
                    - Math.max(0, eco.headTax() - 1) * s.taxHappiness();
            target = Math.max(0, Math.min(100, target));
            double step = s.happinessPerDay() * ctx.days();
            double h = n.happiness() < target ? Math.min(target, n.happiness() + step)
                    : Math.max(target, n.happiness() - step);
            int unhappy = h < s.leaveBelow() ? n.unhappyDays() + ctx.days() : 0;
            if (unhappy >= s.leaveAfterDays()) {
                leave(ctx, p, "unhappy");
                ctx.count(LEFT, 1);
                continue;
            }
            persons.setQuiet(p, WorkComponents.NEEDS, new WorkComponents.Needs(food, h, unhappy, n.unpaid()));
        }
        TAVERN.clear();
        stores.save();
    }

    /** Average happiness of a colony's citizens (60 when it has none). */
    public static double happiness(net.minecraft.server.MinecraftServer server, Colony colony) {
        return Society.citizens(server, colony).stream().filter(p -> !p.has(CoreComponents.UNIT))
                .mapToDouble(p -> p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW).happiness()).average()
                .orElse(WorkComponents.Needs.NEW.happiness());
    }

    static void leave(SystemContext ctx, Person p, String why) {
        Persons.get(ctx.server()).remove(ctx.server(), p.id());
        ServerLevel level = ctx.level();
        Entity body = level == null ? null : level.getEntity(p.id());
        if (body != null) {
            body.discard();
        }
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", ctx.colony().id());
        tag.putUUID("person", p.id());
        tag.putString("why", why);
        HistoryLog.get(ctx.server()).append(new HistoryEntry(ctx.now(), LEAVE_HISTORY, ctx.colony().dimension(),
                Optional.of(ctx.colony().hall()), tag));
        ServerLevel any = ctx.server().overworld();
        var owner = ctx.server().getPlayerList().getPlayer(ctx.colony().founder());
        if (owner != null && any != null) {
            owner.sendSystemMessage(net.minecraft.network.chat.Component.translatableWithFallback(
                    "colony.lotr_port.citizen_left", "%s left %s: they were unhappy too long",
                    p.name().isEmpty() ? p.type().getPath() : p.name(), ctx.colony().name()));
        }
    }
}
