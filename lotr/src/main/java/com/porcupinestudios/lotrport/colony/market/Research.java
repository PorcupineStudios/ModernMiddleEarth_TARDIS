package com.porcupinestudios.lotrport.colony.market;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.Skills;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.ColonySystem.Cadence;
import com.porcupinestudios.lotrport.society.sim.ColonySystems;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import com.porcupinestudios.lotrport.society.sim.Tier;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * The colony's research points: researchers working in a library earn them each day
 * ({@code colony_market/settings.json} {@code points_per_researcher}, times their skill), and upgrades spend them
 * (the market's daily intake limits first; the research tree later).
 */
public final class Research {
    public static final ResourceLocation SYSTEM = LotrPort.id("research");
    public static final ResourceLocation JOB = LotrPort.id("researcher");

    /** Unspent research points of a colony. */
    public static final ComponentType<Double> POINTS = ComponentType.register(LotrPort.id("research_points"),
            Codec.DOUBLE);

    private Research() {}

    public static void register() {
        ColonySystems.register(ColonySystem.of(SYSTEM, Cadence.DAY, Tier.ALL, Set.of(LotrPort.id("needs")),
                Research::day));
    }

    public static double points(Colony c) {
        return c.get(POINTS).orElse(0.0);
    }

    /** What the colony's researchers earn in a day. */
    public static double perDay(MinecraftServer server, Colony c) {
        Buildings buildings = Buildings.of(c);
        double per = Market.settings().pointsPerResearcher();
        double sum = 0;
        for (Person p : Society.citizens(server, c)) {
            var job = p.get(WorkComponents.JOB).orElse(null);
            if (job == null || !job.job().equals(JOB) || buildings.get(job.building()).isEmpty()) {
                continue;
            }
            sum += per * Skills.workFactor(p, JOB);
        }
        return sum;
    }

    static void day(SystemContext ctx) {
        Colony c = ctx.colony();
        if (c.ai()) {
            return;
        }
        double add = perDay(ctx.server(), c) * ctx.days();
        if (add > 0) {
            Colonies.get(ctx.server()).set(c, POINTS, points(c) + add);
        }
    }

    /** Spends points; false (nothing spent) if there are too few. */
    public static boolean spend(MinecraftServer server, Colony c, double n) {
        Colony now = Colonies.get(server).get(c.id()).orElse(c);
        double have = points(now);
        if (have + 1e-9 < n) {
            return false;
        }
        Colonies.get(server).set(now, POINTS, have - n);
        return true;
    }
}
