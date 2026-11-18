package com.porcupinestudios.lotrport.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Which NPCs wander each biome: {@code data/<namespace>/npc_spawns/<biome namespace>/<biome path>.json}, reloaded with
 * /reload. Used by {@link NpcSpawner}.
 */
public final class NpcSpawns {
    /** One kind of group: the NPC type, how likely, and how many at once. */
    /**
     * One kind of group: the NPC type (or a mob's entity type: wargs, spiders), how likely, how many at once, and
     * whether LOTR spawns it only in the dark (its spawnsInDarkness: orcs, wargs, spiders).
     */
    public record Entry(ResourceLocation npc, int weight, int min, int max, boolean dark) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("npc").forGetter(Entry::npc),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Entry::weight),
                Codec.INT.optionalFieldOf("min", 1).forGetter(Entry::min),
                Codec.INT.optionalFieldOf("max", 1).forGetter(Entry::max),
                Codec.BOOL.optionalFieldOf("dark", false).forGetter(Entry::dark))
                .apply(i, Entry::new));
    }

    /**
     * @param cap how many NPCs may be within {@code radius} of a player before no more spawn there
     * @param chance chance per spawn attempt (every {@code interval} ticks per player) that a group spawns
     * @param interval ticks between spawn attempts
     * @param hostilesInDay LOTR's canSpawnHostilesInDay (Mordor...): dark entries spawn in daylight too
     * @param radius how far around the player the cap counts
     * @param countOnlyAnimals the cap counts only animal NPCs (types in any npc_animals table), not people, so a
     *     village doesn't keep the deer away (animal tables, as vanilla's separate creature cap)
     * @param generationChance chance that a newly generated chunk gets a group, as vanilla's creatures at world
     *     generation (LOTR: 0.1, its biomes' spawning chance); 0 = none
     */
    public record Table(List<Entry> entries, int cap, float chance, int interval, boolean hostilesInDay, int radius,
            boolean countOnlyAnimals, float generationChance) {
        public static final Codec<Table> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Table::entries),
                Codec.INT.optionalFieldOf("cap", 8).forGetter(Table::cap),
                Codec.FLOAT.optionalFieldOf("chance", 0.25f).forGetter(Table::chance),
                Codec.INT.optionalFieldOf("interval", 100).forGetter(Table::interval),
                Codec.BOOL.optionalFieldOf("hostiles_in_day", false).forGetter(Table::hostilesInDay),
                Codec.INT.optionalFieldOf("radius", 64).forGetter(Table::radius),
                Codec.BOOL.optionalFieldOf("count_only_animals", false).forGetter(Table::countOnlyAnimals),
                Codec.FLOAT.optionalFieldOf("generation_chance", 0f).forGetter(Table::generationChance))
                .apply(i, Table::new));
    }

    private static final CodecJsonLoader<Table> LOADER = new CodecJsonLoader<>("npc_spawns", Table.CODEC);
    /** LOTR's animals per biome (its creature lists), same format: {@code npc_animals/<biome ns>/<biome path>}. */
    private static final CodecJsonLoader<Table> ANIMALS = new CodecJsonLoader<>("npc_animals", Table.CODEC);

    private NpcSpawns() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(LOADER);
            event.addListener(ANIMALS);
        });
    }

    /** The table for a biome; files are named {@code <biome namespace>/<biome path>}. */
    public static Optional<Table> forBiome(ResourceLocation biome) {
        return Optional.ofNullable(LOADER.entries().get(ResourceLocation.fromNamespaceAndPath(
                "lotr_port", biome.getNamespace() + "/" + biome.getPath())));
    }

    public static Optional<Table> animalsForBiome(ResourceLocation biome) {
        return Optional.ofNullable(ANIMALS.entries().get(ResourceLocation.fromNamespaceAndPath(
                "lotr_port", biome.getNamespace() + "/" + biome.getPath())));
    }

    /**
     * Whether an NPC type is one of LOTR's animals (in some npc_animals table): animals don't despawn when no player
     * is near (LOTR's were EntityAnimals), and an animal table's cap counts only them.
     */
    public static boolean isAnimal(ResourceLocation type) {
        java.util.Set<ResourceLocation> set = animalTypes;
        if (set == null || animalSource != ANIMALS.entries()) {
            var source = ANIMALS.entries();
            set = new java.util.HashSet<>();
            for (Table t : source.values()) {
                for (Entry e : t.entries()) {
                    set.add(e.npc());
                }
            }
            animalTypes = set;
            animalSource = source;
        }
        return set.contains(type);
    }

    private static volatile java.util.Set<ResourceLocation> animalTypes;
    private static volatile java.util.Map<ResourceLocation, Table> animalSource;

    public static int count() {
        return LOADER.entries().size();
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }
}
