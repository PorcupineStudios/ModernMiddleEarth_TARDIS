package com.porcupinestudios.lotrport.colony.work;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * A colony job, {@code data/<ns>/colony_job/<id>.json} (reloads; RingRealms' jobs). Building types name the job their
 * workers do.
 *
 * @param priority lower is filled first: every workplace of a lower-priority job gets one worker before the next
 * @param max most workers one workplace takes (slots are 1 + quality / 25, up to this)
 * @param wage coins a worker is paid a day from the treasury
 * @param races proficiency by race (1 when not listed): how much a worker of that race makes
 * @param produces items a worker makes a day at proficiency 1 and middling skill; with {@code consumes}, per batch
 * @param consumes what one batch uses up (none: a gatherer, making {@code produces} from the land)
 * @param batches most batches a worker makes a day
 * @param carries items a worker carries a day from workplaces to storehouses (couriers)
 * @param tavern drinks are served: on a day it is staffed, each citizen with a coin spends it here and is happier
 */
public record JobDef(String name, int priority, int max, int wage, Map<String, Double> races,
        Map<ResourceLocation, Double> produces, Map<ResourceLocation, Integer> consumes, int batches, int carries, boolean tavern) {
    public static final Codec<JobDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(JobDef::name),
            Codec.INT.optionalFieldOf("priority", 100).forGetter(JobDef::priority),
            Codec.INT.optionalFieldOf("max", 3).forGetter(JobDef::max),
            Codec.INT.optionalFieldOf("wage", 2).forGetter(JobDef::wage),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("races", Map.of()).forGetter(JobDef::races),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("produces", Map.of())
                    .forGetter(JobDef::produces),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("consumes", Map.of())
                    .forGetter(JobDef::consumes),
            Codec.INT.optionalFieldOf("batches", 0).forGetter(JobDef::batches),
            Codec.INT.optionalFieldOf("carries", 0).forGetter(JobDef::carries),
            Codec.BOOL.optionalFieldOf("tavern", false).forGetter(JobDef::tavern))
            .apply(i, JobDef::new));

    private static final CodecJsonLoader<JobDef> LOADER = new CodecJsonLoader<>("colony_job", CODEC);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static Map<ResourceLocation, JobDef> all() {
        return LOADER.entries();
    }

    public static Optional<JobDef> get(ResourceLocation id) {
        return Optional.ofNullable(LOADER.entries().get(id));
    }

    public static java.util.List<String> loadProblems() {
        return LOADER.lastErrors();
    }

    public double proficiency(String race) {
        return races.getOrDefault(race, 1.0);
    }

    /** Worker slots of a workplace of this quality (RingRealms: level 1-5 = 1 + quality / 25). */
    public int slots(int quality) {
        return Math.max(1, Math.min(max, 1 + quality / 25));
    }

    public static Component titleOf(ResourceLocation id) {
        String key = get(id).map(JobDef::name).filter(n -> !n.isEmpty())
                .orElse("colony_job." + id.getNamespace() + "." + id.getPath());
        return Component.translatableWithFallback(key, id.getPath().replace('_', ' '));
    }
}
