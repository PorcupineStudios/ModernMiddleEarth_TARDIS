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
 * @param maxCatchUpDays most colony days one pass applies to a colony that was unloaded a long time
 * @param budgetMs the per-tick budget for all colony work; {@code /lotr colony perf} warns above it
 * @param disabled system ids switched off
 */
public record SimSettings(int pulseTicks, int activeRadius, int maxCatchUpDays, double budgetMs,
        List<ResourceLocation> disabled) {
    public static final SimSettings DEFAULT = new SimSettings(100, 96, 30, 2.0, List.of());
    public static final Codec<SimSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("pulse_ticks", DEFAULT.pulseTicks()).forGetter(SimSettings::pulseTicks),
            Codec.INT.optionalFieldOf("active_radius", DEFAULT.activeRadius()).forGetter(SimSettings::activeRadius),
            Codec.INT.optionalFieldOf("max_catch_up_days", DEFAULT.maxCatchUpDays())
                    .forGetter(SimSettings::maxCatchUpDays),
            Codec.DOUBLE.optionalFieldOf("budget_ms", DEFAULT.budgetMs()).forGetter(SimSettings::budgetMs),
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
