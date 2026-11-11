package com.porcupinestudios.lotrport.colony;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.society.ComponentMap;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.Owner;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A colony (RingRealms): a Hall founded by a player or an AI ruler for a faction, and the citizens who moved in. Mutable; changed
 * only through {@link Colonies}, which saves it and writes history.
 */
public final class Colony {
    public static final Codec<Colony> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(c -> c.id),
            Codec.STRING.fieldOf("name").forGetter(c -> c.name),
            UUIDUtil.CODEC.fieldOf("founder").forGetter(c -> c.founder),
            ResourceLocation.CODEC.fieldOf("faction").forGetter(c -> c.faction),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(c -> c.dimension),
            BlockPos.CODEC.fieldOf("hall").forGetter(c -> c.hall),
            Codec.LONG.optionalFieldOf("founded", 0L).forGetter(c -> c.founded),
            Codec.LONG.optionalFieldOf("last_day", 0L).forGetter(c -> c.lastDay),
            Codec.unboundedMap(ResourceLocation.CODEC, Recruitment.CODEC).optionalFieldOf("recruits", Map.of())
                    .forGetter(c -> c.recruits),
            Codec.BOOL.optionalFieldOf("ai", false).forGetter(c -> c.ai),
            Codec.INT.optionalFieldOf("virtual_population", 0).forGetter(c -> c.virtualPopulation),
            ComponentMap.CODEC.optionalFieldOf("components", new ComponentMap()).forGetter(c -> c.components))
            .apply(i, Colony::new));

    final UUID id;
    final String name;
    /** The owner: the founder, or whoever took or was handed the colony since. */
    UUID founder;
    ResourceLocation faction;
    final ResourceKey<Level> dimension;
    final BlockPos hall;
    final long founded;
    long lastDay;
    /** Open recruitment quests by recruit definition id. */
    final Map<ResourceLocation, Recruitment> recruits;
    /** Run by an AI ruler: its people are counted, not spawned ({@link #virtualPopulation}). */
    boolean ai;
    int virtualPopulation;
    /**
     * Everything else a colony has (members and roles, land, treasury...): one component per feature, so a feature
     * never adds a field here. Its people are Persons owned by the colony ({@code Society.citizens}).
     */
    final ComponentMap components;

    Colony(UUID id, String name, UUID founder, ResourceLocation faction, ResourceKey<Level> dimension, BlockPos hall,
            long founded, long lastDay, Map<ResourceLocation, Recruitment> recruits, boolean ai,
            int virtualPopulation, ComponentMap components) {
        this.id = id;
        this.name = name;
        this.founder = founder;
        this.faction = faction;
        this.dimension = dimension;
        this.hall = hall;
        this.founded = founded;
        this.lastDay = lastDay;
        this.recruits = new LinkedHashMap<>(recruits);
        this.ai = ai;
        this.virtualPopulation = virtualPopulation;
        this.components = components;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID founder() {
        return founder;
    }

    public ResourceLocation faction() {
        return faction;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos hall() {
        return hall;
    }

    public long founded() {
        return founded;
    }

    public Map<ResourceLocation, Recruitment> recruits() {
        return Map.copyOf(recruits);
    }

    public <T> Optional<T> get(ComponentType<T> type) {
        return components.get(type);
    }

    /** Read-only view of the colony's components. */
    public ComponentMap components() {
        return components;
    }

    /** The owner as an {@link Owner} key: this colony (its Persons are owned by it). */
    public Owner asOwner() {
        return Owner.colony(id);
    }

    public boolean ai() {
        return ai;
    }

    /** Counted people of an AI colony (no bodies). Living citizens are {@code Society.population}. */
    public int virtualPopulation() {
        return virtualPopulation;
    }
}
