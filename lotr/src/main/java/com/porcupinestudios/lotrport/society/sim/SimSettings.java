package com.porcupinestudios.lotrport.society.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Scheduler numbers, {@code data/<ns>/colony_systems/settings.json} (reloads).
 *
 * @param pulseTicks ticks between two pulses of one colony; colonies are spread over these ticks
 * @param activeRadius a player this close to the Hall makes the colony {@link Tier#ACTIVE}
 * @param maxCatchUpDays most colony days one pass applies to a colony that was unloaded (or held back) a long time
 * @param budgetMs the per-tick budget for all colony work: a colony day that would start past it waits in the
 *     backlog; {@code /lotr colony perf} warns above it
 * @param load how the scheduler gives way to a busy server (portals, chunk generation, other mods)
 * @param disabled system ids switched off
 */
public record SimSettings(int pulseTicks, int activeRadius, int maxCatchUpDays, double budgetMs, Load load,
        List<ResourceLocation> disabled) {
    /**
     * Server load levels, by the server's own tick time (smoothed, without the colony work).
     *
     * @param lightMs below this the server has time to spare: the backlog is caught up with up to
     *     {@code light_budget_factor} times the budget
     * @param heavyMs above this colony days wait in the backlog; only the pulses of colonies a player is in run
     * @param overloadedMs above this (the server is falling behind its 50 ms) nothing runs but nearby bodies
     * @param lightBudgetFactor see {@code lightMs}
     * @param smoothing weight of the newest tick in the smoothed tick time (0-1)
     */
    public record Load(double lightMs, double heavyMs, double overloadedMs, double lightBudgetFactor,
            double smoothing) {
        public static final Load DEFAULT = new Load(25, 40, 48, 2.0, 0.1);
        public static final Codec<Load> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("light_ms", DEFAULT.lightMs()).forGetter(Load::lightMs),
                Codec.DOUBLE.optionalFieldOf("heavy_ms", DEFAULT.heavyMs()).forGetter(Load::heavyMs),
                Codec.DOUBLE.optionalFieldOf("overloaded_ms", DEFAULT.overloadedMs()).forGetter(Load::overloadedMs),
                Codec.DOUBLE.optionalFieldOf("light_budget_factor", DEFAULT.lightBudgetFactor())
                        .forGetter(Load::lightBudgetFactor),
                Codec.DOUBLE.optionalFieldOf("smoothing", DEFAULT.smoothing()).forGetter(Load::smoothing))
                .apply(i, Load::new));
    }

    public static final SimSettings DEFAULT = new SimSettings(100, 96, 30, 2.0, Load.DEFAULT, List.of());
    public static final Codec<SimSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("pulse_ticks", DEFAULT.pulseTicks()).forGetter(SimSettings::pulseTicks),
            Codec.INT.optionalFieldOf("active_radius", DEFAULT.activeRadius()).forGetter(SimSettings::activeRadius),
            Codec.INT.optionalFieldOf("max_catch_up_days", DEFAULT.maxCatchUpDays())
                    .forGetter(SimSettings::maxCatchUpDays),
            Codec.DOUBLE.optionalFieldOf("budget_ms", DEFAULT.budgetMs()).forGetter(SimSettings::budgetMs),
            Load.CODEC.optionalFieldOf("load", Load.DEFAULT).forGetter(SimSettings::load),
            ResourceLocation.CODEC.listOf().optionalFieldOf("disabled", List.of()).forGetter(SimSettings::disabled))
            .apply(i, SimSettings::new));

    private static final ResourceLocation FILE = LotrPort.id("settings");
    private static final CodecJsonLoader<SimSettings> LOADER = new CodecJsonLoader<>("colony_systems", CODEC);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static SimSettings get() {
        return LOADER.entries().getOrDefault(FILE, DEFAULT);
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }
}
