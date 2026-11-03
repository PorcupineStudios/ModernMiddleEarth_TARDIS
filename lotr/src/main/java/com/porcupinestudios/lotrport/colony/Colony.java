package com.porcupinestudios.lotrport.colony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    /** One person living in the colony. {@code rare} = joined by a recruitment quest rather than migration. */
    public record Citizen(UUID entity, ResourceLocation type, String name, long joined, boolean rare) {
        public static final Codec<Citizen> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("entity").forGetter(Citizen::entity),
                ResourceLocation.CODEC.fieldOf("type").forGetter(Citizen::type),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Citizen::name),
                Codec.LONG.optionalFieldOf("joined", 0L).forGetter(Citizen::joined),
                Codec.BOOL.optionalFieldOf("rare", false).forGetter(Citizen::rare))
                .apply(i, Citizen::new));
    }

    public static final Codec<Colony> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(c -> c.id),
            Codec.STRING.fieldOf("name").forGetter(c -> c.name),
            UUIDUtil.CODEC.fieldOf("founder").forGetter(c -> c.founder),
            ResourceLocation.CODEC.fieldOf("faction").forGetter(c -> c.faction),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(c -> c.dimension),
            BlockPos.CODEC.fieldOf("hall").forGetter(c -> c.hall),
            Codec.LONG.optionalFieldOf("founded", 0L).forGetter(c -> c.founded),
            Codec.LONG.optionalFieldOf("last_day", 0L).forGetter(c -> c.lastDay),
            Citizen.CODEC.listOf().optionalFieldOf("citizens", List.of()).forGetter(c -> c.citizens),
            Codec.unboundedMap(ResourceLocation.CODEC, Recruitment.CODEC).optionalFieldOf("recruits", Map.of())
                    .forGetter(c -> c.recruits),
            Codec.BOOL.optionalFieldOf("ai", false).forGetter(c -> c.ai),
            Codec.INT.optionalFieldOf("virtual_population", 0).forGetter(c -> c.virtualPopulation))
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
    final List<Citizen> citizens;
    /** Open recruitment quests by recruit definition id. */
    final Map<ResourceLocation, Recruitment> recruits;
    /** Run by an AI ruler: its people are counted, not spawned ({@link #virtualPopulation}). */
    boolean ai;
    int virtualPopulation;

    Colony(UUID id, String name, UUID founder, ResourceLocation faction, ResourceKey<Level> dimension, BlockPos hall,
            long founded, long lastDay, List<Citizen> citizens, Map<ResourceLocation, Recruitment> recruits,
            boolean ai, int virtualPopulation) {
        this.id = id;
        this.name = name;
        this.founder = founder;
        this.faction = faction;
        this.dimension = dimension;
        this.hall = hall;
        this.founded = founded;
        this.lastDay = lastDay;
        this.citizens = new ArrayList<>(citizens);
        this.recruits = new LinkedHashMap<>(recruits);
        this.ai = ai;
        this.virtualPopulation = virtualPopulation;
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

    public List<Citizen> citizens() {
        return List.copyOf(citizens);
    }

    public Map<ResourceLocation, Recruitment> recruits() {
        return Map.copyOf(recruits);
    }

    public boolean ai() {
        return ai;
    }

    /** Citizens with bodies plus counted ones (AI colonies). */
    public int population() {
        return citizens.size() + virtualPopulation;
    }

    public boolean hasCitizen(UUID entity) {
        return citizens.stream().anyMatch(c -> c.entity().equals(entity));
    }
}
