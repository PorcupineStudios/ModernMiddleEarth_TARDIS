package com.porcupinestudios.lotrport.colony.work;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
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
 * @param recipes more things the trade can make besides {@code consumes -> produces}; which one a worker makes is
 *     picked each batch by work orders and stock targets ({@link Production})
 * @param tool the tool a worker wears out (none: no tool); missing tools slow the work to {@code no_tool_factor}
 * @param toolDays worker-days one tool lasts
 * @param noToolFactor how much a worker makes without a tool
 */
public record JobDef(String name, int priority, int max, int wage, Map<String, Double> races,
        Map<ResourceLocation, Double> produces, Map<ResourceLocation, Integer> consumes, int batches, int carries, boolean tavern,
        List<Recipe> extra, Optional<ResourceLocation> tool, int toolDays, double noToolFactor) {
    /**
     * One thing a trade makes: a batch uses {@code consumes} and makes {@code produces}.
     *
     * @param requires a colony tech ({@code colony_tech/<id>}) that must be researched first, if any
     */
    public record Recipe(Map<ResourceLocation, Integer> consumes, Map<ResourceLocation, Double> produces,
            Optional<ResourceLocation> requires) {
        public static final Codec<Recipe> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("consumes", Map.of())
                        .forGetter(Recipe::consumes),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).fieldOf("produces").forGetter(Recipe::produces),
                ResourceLocation.CODEC.optionalFieldOf("requires").forGetter(Recipe::requires))
                .apply(i, Recipe::new));
    }

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
            Codec.BOOL.optionalFieldOf("tavern", false).forGetter(JobDef::tavern),
            Recipe.CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(JobDef::extra),
            ResourceLocation.CODEC.optionalFieldOf("tool").forGetter(JobDef::tool),
            Codec.INT.optionalFieldOf("tool_days", 20).forGetter(JobDef::toolDays),
            Codec.DOUBLE.optionalFieldOf("no_tool_factor", 0.5).forGetter(JobDef::noToolFactor))
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

    /** Everything the trade makes: the main recipe first (if it has one), then {@code recipes}. */
    public List<Recipe> recipes() {
        List<Recipe> out = new java.util.ArrayList<>();
        if (!produces.isEmpty()) {
            out.add(new Recipe(consumes, produces, Optional.empty()));
        }
        out.addAll(extra);
        return out;
    }

    /** A crafting trade turns goods into others (as opposed to gathering from the land or carrying). */
    public boolean crafts() {
        return carries <= 0 && recipes().stream().anyMatch(r -> !r.consumes().isEmpty());
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
