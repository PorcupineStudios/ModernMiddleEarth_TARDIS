package com.porcupinestudios.lotrport.colony.work;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Colony life by the day, {@code data/<ns>/colony_life/settings.json} (reloads). RingRealms' per-minute numbers
 * turned into a colony day.
 *
 * @param foodPerDay food a citizen uses a day (food runs 0 to 20)
 * @param eatBelow a citizen eats while food is below this
 * @param nutritionFactor food an item gives per point of its nutrition
 * @param skillPerDay skill a worker gains a day (0-100)
 * @param happinessPerDay how far happiness moves toward its target in a day
 * @param leaveBelow happiness under this is unhappy
 * @param leaveAfterDays unhappy days in a row before a citizen leaves
 * @param happinessBase the target's base
 * @param homeHappiness added with a home
 * @param homeQualityFactor added per point of the home's quality
 * @param fedHappiness added when fed (food at eatBelow or more); half when hungry, none when starving
 * @param tavernHappiness added on a day the citizen drank at a staffed tavern (1 coin)
 * @param unpaidHappiness taken while a worker's wages are owed (they strike)
 * @param taxHappiness taken per coin of head tax above 1
 * @param happyArrivals average happiness above which one more migrant comes a day
 */
public record LifeSettings(double foodPerDay, double eatBelow, double nutritionFactor, double skillPerDay,
        double happinessPerDay, double leaveBelow, int leaveAfterDays, double happinessBase, double homeHappiness,
        double homeQualityFactor, double fedHappiness, double tavernHappiness, double unpaidHappiness,
        double taxHappiness, double happyArrivals) {
    public static final LifeSettings DEFAULT = new LifeSettings(20, 14, 2, 5, 20, 25, 2, 20, 20, 0.2, 20, 10, 15, 5,
            70);
    public static final Codec<LifeSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("food_per_day", DEFAULT.foodPerDay()).forGetter(LifeSettings::foodPerDay),
            Codec.DOUBLE.optionalFieldOf("eat_below", DEFAULT.eatBelow()).forGetter(LifeSettings::eatBelow),
            Codec.DOUBLE.optionalFieldOf("nutrition_factor", DEFAULT.nutritionFactor())
                    .forGetter(LifeSettings::nutritionFactor),
            Codec.DOUBLE.optionalFieldOf("skill_per_day", DEFAULT.skillPerDay()).forGetter(LifeSettings::skillPerDay),
            Codec.DOUBLE.optionalFieldOf("happiness_per_day", DEFAULT.happinessPerDay())
                    .forGetter(LifeSettings::happinessPerDay),
            Codec.DOUBLE.optionalFieldOf("leave_below", DEFAULT.leaveBelow()).forGetter(LifeSettings::leaveBelow),
            Codec.INT.optionalFieldOf("leave_after_days", DEFAULT.leaveAfterDays())
                    .forGetter(LifeSettings::leaveAfterDays),
            Codec.DOUBLE.optionalFieldOf("happiness_base", DEFAULT.happinessBase())
                    .forGetter(LifeSettings::happinessBase),
            Codec.DOUBLE.optionalFieldOf("home_happiness", DEFAULT.homeHappiness())
                    .forGetter(LifeSettings::homeHappiness),
            Codec.DOUBLE.optionalFieldOf("home_quality_factor", DEFAULT.homeQualityFactor())
                    .forGetter(LifeSettings::homeQualityFactor),
            Codec.DOUBLE.optionalFieldOf("fed_happiness", DEFAULT.fedHappiness()).forGetter(LifeSettings::fedHappiness),
            Codec.DOUBLE.optionalFieldOf("tavern_happiness", DEFAULT.tavernHappiness())
                    .forGetter(LifeSettings::tavernHappiness),
            Codec.DOUBLE.optionalFieldOf("unpaid_happiness", DEFAULT.unpaidHappiness())
                    .forGetter(LifeSettings::unpaidHappiness),
            Codec.DOUBLE.optionalFieldOf("tax_happiness", DEFAULT.taxHappiness()).forGetter(LifeSettings::taxHappiness),
            Codec.DOUBLE.optionalFieldOf("happy_arrivals", DEFAULT.happyArrivals())
                    .forGetter(LifeSettings::happyArrivals))
            .apply(i, LifeSettings::new));

    private static final ResourceLocation FILE = LotrPort.id("settings");
    private static final CodecJsonLoader<LifeSettings> LOADER = new CodecJsonLoader<>("colony_life", CODEC);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static LifeSettings get() {
        return LOADER.entries().getOrDefault(FILE, DEFAULT);
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }
}
