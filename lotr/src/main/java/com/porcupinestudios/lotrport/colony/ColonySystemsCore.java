package com.porcupinestudios.lotrport.colony;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.ColonySystem.Cadence;
import com.porcupinestudios.lotrport.society.sim.ColonySystems;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import com.porcupinestudios.lotrport.society.sim.Tier;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The colony systems this mod ships. Each is small and runs on its own; to change one, replace or switch it off
 * ({@code colony_systems/settings.json} {@code disabled}) rather than editing the others.
 */
public final class ColonySystemsCore {
    public static final ResourceLocation APPLICANTS = LotrPort.id("applicants");
    public static final ResourceLocation MIGRATION = LotrPort.id("migration");
    public static final ResourceLocation RECRUITS = LotrPort.id("recruits");
    public static final ResourceLocation SOLDIER_PAY = LotrPort.id("soldier_pay");
    /** Count: migrants who came this pass. */
    public static final ResourceLocation ARRIVALS = LotrPort.id("arrivals");
    /** Count: coins paid to soldiers this pass. */
    public static final ResourceLocation WAGES_PAID = LotrPort.id("wages_paid");

    private ColonySystemsCore() {}

    public static void register() {
        // Applicants age in every tier, so one who waits out an unloaded colony is gone when it loads.
        ColonySystems.register(ColonySystem.of(APPLICANTS, Cadence.DAY, Tier.ALL, Set.of(), ctx -> {
            if (ColonyData.settings().selectiveMigration()) {
                Migrants.age(ctx.server(), ctx.colony(), ctx.days());
            }
        }));
        // Migrants need a loaded Hall (beds are counted from blocks); an AI colony grows by numbers instead.
        ColonySystems.register(ColonySystem.of(MIGRATION, Cadence.DAY, Tier.IN_WORLD, Set.of(APPLICANTS), ctx -> {
            if (!ctx.colony().ai() && ctx.level() != null) {
                ctx.count(ARRIVALS, Colonies.get(ctx.server()).arrivals(ctx.level(), ctx.colony()));
            }
        }));
        ColonySystems.register(ColonySystem.of(RECRUITS, Cadence.DAY, Tier.IN_WORLD, Set.of(MIGRATION),
                ctx -> Recruiting.tryJoinAll(ctx.server(), ctx.colony())));
        ColonySystems.register(ColonySystem.of(SOLDIER_PAY, Cadence.DAY, Tier.ALL, Set.of(), ColonySystemsCore::pay));
    }

    /**
     * Soldiers (Persons the colony owns with a unit wage) are paid from the treasury for each day that passed. When
     * the coins run out the rest go unpaid and count the days; what unpaid soldiers do is the units' business.
     */
    static void pay(SystemContext ctx) {
        Persons persons = Persons.get(ctx.server());
        Colonies colonies = Colonies.get(ctx.server());
        long coins = ctx.colony().get(CoreComponents.TREASURY).orElse(CoreComponents.Treasury.EMPTY).coins();
        long start = coins;
        for (Person p : Society.soldiers(ctx.server(), ctx.colony())) {
            CoreComponents.Unit u = p.get(CoreComponents.UNIT).orElseThrow();
            if (u.wage() <= 0) {
                continue;
            }
            long owed = (long) u.wage() * ctx.days();
            if (coins >= owed) {
                coins -= owed;
                if (u.unpaid() != 0) {
                    persons.setQuiet(p, CoreComponents.UNIT, u.withUnpaid(0));
                }
            } else {
                int paidDays = (int) (coins / u.wage());
                coins -= (long) paidDays * u.wage();
                persons.setQuiet(p, CoreComponents.UNIT, u.withUnpaid(u.unpaid() + ctx.days() - paidDays));
            }
        }
        if (coins != start) {
            colonies.set(ctx.colony(), CoreComponents.TREASURY, new CoreComponents.Treasury(coins));
            ctx.count(WAGES_PAID, (int) Math.min(Integer.MAX_VALUE, start - coins));
        }
    }
}
