package com.porcupinestudios.lotrport.colony.tech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.colony.market.Cost;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * A node of the colony tech tree, {@code data/<ns>/colony_tech/<id>.json} (reloads; RingRealms research v2).
 * Nodes sit in a lane (their subject: crafts, farming, civics...) at a stage (1 = first column). Researching one
 * pays its {@link Cost} (research points, goods, coins) once. A {@code fork} names a group of which a colony may only
 * ever take one node: choosing one closes the others for good (the tree's branches).
 *
 * <p>What a node does: {@code work} multiplies every worker's output, {@code jobs} multiplies one trade's,
 * {@code happiness} adds to every citizen's target, {@code conquest} ({@link Conquest}) makes a colony a base for
 * taking land: kills and assaults within its reach push harder. Building types ({@code research}), trade recipes and edicts
 * ({@code requires}) name the node that unlocks them.
 *
 * @param after nodes that must be researched first (all of them)
 */
public record TechDef(String name, String description, String lane, int stage, List<ResourceLocation> after,
        Optional<String> fork, Cost cost, double work, Map<ResourceLocation, Double> jobs, double happiness,
        Optional<Conquest> conquest) {
    /**
     * A conquest perk: {@code pressure} adds to the multiplier on pressure (kills, assaults) within {@code reach} map
     * pixels of the colony; a colony's multiplier is 1 + its perks' pressure, its reach the largest. A new colony has
     * none, a built-up one stacks them.
     */
    public record Conquest(double pressure, double reach) {
        public static final Codec<Conquest> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("pressure", 0.0).forGetter(Conquest::pressure),
                Codec.DOUBLE.optionalFieldOf("reach", 0.0).forGetter(Conquest::reach))
                .apply(i, Conquest::new));
    }

    public static final Codec<TechDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(TechDef::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(TechDef::description),
            Codec.STRING.optionalFieldOf("lane", "general").forGetter(TechDef::lane),
            Codec.INT.optionalFieldOf("stage", 1).forGetter(TechDef::stage),
            ResourceLocation.CODEC.listOf().optionalFieldOf("after", List.of()).forGetter(TechDef::after),
            Codec.STRING.optionalFieldOf("fork").forGetter(TechDef::fork),
            Cost.CODEC.optionalFieldOf("cost", Cost.FREE).forGetter(TechDef::cost),
            Codec.DOUBLE.optionalFieldOf("work", 1.0).forGetter(TechDef::work),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("jobs", Map.of())
                    .forGetter(TechDef::jobs),
            Codec.DOUBLE.optionalFieldOf("happiness", 0.0).forGetter(TechDef::happiness),
            Conquest.CODEC.optionalFieldOf("conquest").forGetter(TechDef::conquest))
            .apply(i, TechDef::new));

    private static final CodecJsonLoader<TechDef> LOADER = new CodecJsonLoader<>("colony_tech", CODEC);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static Map<ResourceLocation, TechDef> all() {
        return LOADER.entries();
    }

    public static Optional<TechDef> get(ResourceLocation id) {
        return Optional.ofNullable(LOADER.entries().get(id));
    }

    /** Every node, by lane, then stage, then id. */
    public static List<Map.Entry<ResourceLocation, TechDef>> sorted() {
        return all().entrySet().stream().sorted(Comparator
                .comparing((Map.Entry<ResourceLocation, TechDef> e) -> e.getValue().lane())
                .thenComparingInt(e -> e.getValue().stage()).thenComparing(e -> e.getKey().toString())).toList();
    }

    public String title(ResourceLocation id) {
        return name.isEmpty() ? id.getPath().replace('_', ' ') : name;
    }

    public static String titleOf(ResourceLocation id) {
        return get(id).map(t -> t.title(id)).orElse(id.getPath().replace('_', ' '));
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }
}
