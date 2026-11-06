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
    public record Entry(ResourceLocation npc, int weight, int min, int max) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("npc").forGetter(Entry::npc),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Entry::weight),
                Codec.INT.optionalFieldOf("min", 1).forGetter(Entry::min),
                Codec.INT.optionalFieldOf("max", 1).forGetter(Entry::max))
                .apply(i, Entry::new));
    }

    /**
     * @param cap how many NPCs may be near a player before no more spawn there
     * @param chance chance per spawn attempt (every {@code interval} ticks per player) that a group spawns
     * @param interval ticks between spawn attempts
     */
    public record Table(List<Entry> entries, int cap, float chance, int interval) {
        public static final Codec<Table> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Table::entries),
                Codec.INT.optionalFieldOf("cap", 8).forGetter(Table::cap),
                Codec.FLOAT.optionalFieldOf("chance", 0.25f).forGetter(Table::chance),
                Codec.INT.optionalFieldOf("interval", 100).forGetter(Table::interval))
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

    public static int count() {
        return LOADER.entries().size();
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }
}
