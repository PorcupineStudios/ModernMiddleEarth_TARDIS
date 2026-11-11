package com.porcupinestudios.lotrport.colony.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * A kind of colony building, {@code data/<ns>/colony_building/<id>.json} (reloads; RingRealms' building types). A
 * room is this type when every requirement holds. Counters are the room's measures: {@code floor}, {@code storeys},
 * {@code volume}, {@code lights}, and one per block tag {@code <ns>:colony/<counter>} (beds count their heads), so a
 * pack adds a counter by adding the tag and naming it here.
 *
 * @param name lang key or text
 * @param description what the room needs, shown when nothing fits
 * @param priority the writ picks the highest-priority type that fits when the player didn't choose one
 * @param home people live here: its beds are homes
 * @param job the job its workers do (none: nobody works here)
 * @param workers most workers it takes
 * @param requires all must hold
 */
public record BuildingType(String name, String description, int priority, boolean home, Optional<ResourceLocation> job,
        int workers, List<Requirement> requires) {
    public static final Codec<BuildingType> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(BuildingType::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(BuildingType::description),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(BuildingType::priority),
            Codec.BOOL.optionalFieldOf("home", false).forGetter(BuildingType::home),
            ResourceLocation.CODEC.optionalFieldOf("job").forGetter(BuildingType::job),
            Codec.INT.optionalFieldOf("workers", 1).forGetter(BuildingType::workers),
            Requirement.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(BuildingType::requires))
            .apply(i, BuildingType::new));

    private static final CodecJsonLoader<BuildingType> LOADER = new CodecJsonLoader<>("colony_building", CODEC);

    /**
     * One rule: {@code count >= min + each x per}. {@code {"count": "floor", "per": "beds", "each": 3}} = 3 floor per
     * bed.
     */
    public record Requirement(String count, int min, Optional<String> per, int each, String message) {
        public static final Codec<Requirement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("count").forGetter(Requirement::count),
                Codec.INT.optionalFieldOf("min", 0).forGetter(Requirement::min),
                Codec.STRING.optionalFieldOf("per").forGetter(Requirement::per),
                Codec.INT.optionalFieldOf("each", 0).forGetter(Requirement::each),
                Codec.STRING.optionalFieldOf("message", "").forGetter(Requirement::message))
                .apply(i, Requirement::new));

        public boolean holds(Map<String, Integer> counts) {
            int need = min + per.map(p -> each * counts.getOrDefault(p, 0)).orElse(0);
            return counts.getOrDefault(count, 0) >= need;
        }

        public Component why() {
            if (!message.isEmpty()) {
                return Component.translatableWithFallback(message, message);
            }
            return per.isPresent() ? Component.literal("needs " + each + " " + count + " per " + per.get())
                    : Component.literal("needs " + min + " " + count);
        }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static Map<ResourceLocation, BuildingType> all() {
        return LOADER.entries();
    }

    public static Optional<BuildingType> get(ResourceLocation id) {
        return Optional.ofNullable(LOADER.entries().get(id));
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }

    /** Every counter some type asks for: the scan counts only these (plus floor, storeys, volume, lights). */
    public static Set<String> counters() {
        Set<String> out = new LinkedHashSet<>();
        for (BuildingType t : all().values()) {
            for (Requirement r : t.requires()) {
                out.add(r.count());
                r.per().ifPresent(out::add);
            }
        }
        out.add("beds");
        out.add("chests");
        out.add("tables");
        return out;
    }

    /** The first requirement that fails, if any. */
    public Optional<Requirement> missing(Map<String, Integer> counts) {
        return requires.stream().filter(r -> !r.holds(counts)).findFirst();
    }

    /** Types the room fits, best first. */
    public static List<ResourceLocation> fitting(Map<String, Integer> counts) {
        return all().entrySet().stream().filter(e -> e.getValue().missing(counts).isEmpty())
                .sorted(Comparator.comparingInt((Map.Entry<ResourceLocation, BuildingType> e) -> -e.getValue().priority())
                        .thenComparing(e -> e.getKey().toString()))
                .map(Map.Entry::getKey).toList();
    }

    public Component title(ResourceLocation id) {
        String key = name.isEmpty() ? "colony_building." + id.getNamespace() + "." + id.getPath() : name;
        return Component.translatableWithFallback(key, id.getPath().replace('_', ' '));
    }

    public static Component titleOf(ResourceLocation id) {
        return get(id).map(t -> t.title(id)).orElse(Component.literal(id.getPath().replace('_', ' ')));
    }
}
