package com.porcupinestudios.lotrport.colony.building;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.society.ComponentType;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * A colony's buildings (colony component {@code lotr_port:buildings}). The block index (which building owns a block)
 * is built from it on first use and never saved.
 */
public record Buildings(List<Building> list) {
    public static final Codec<Buildings> CODEC = Building.CODEC.listOf().xmap(Buildings::new, Buildings::list);
    public static final ComponentType<Buildings> TYPE = ComponentType.register(LotrPort.id("buildings"), CODEC);
    public static final Buildings NONE = new Buildings(List.of());

    /** Indexes by component value: a changed list is a new value, so a stale index is never used. */
    private static final Map<Buildings, Long2ObjectOpenHashMap<Building>> INDEX = new WeakHashMap<>();

    public Buildings {
        list = List.copyOf(list);
    }

    public static Buildings of(Colony colony) {
        return colony.get(TYPE).orElse(NONE);
    }

    public Optional<Building> get(UUID id) {
        return list.stream().filter(b -> b.id().equals(id)).findFirst();
    }

    public Optional<Building> atDoor(BlockPos door) {
        return list.stream().filter(b -> b.door().equals(door)).findFirst();
    }

    /** The building whose own blocks hold {@code pos}. */
    public Optional<Building> at(BlockPos pos) {
        return Optional.ofNullable(index().get(pos.asLong()));
    }

    public List<Building> ofType(ResourceLocation type) {
        return list.stream().filter(b -> b.type().equals(type)).toList();
    }

    /** Homes: buildings whose type is a home. */
    public List<Building> homes() {
        return list.stream().filter(b -> BuildingType.get(b.type()).map(BuildingType::home).orElse(false)).toList();
    }

    public Buildings with(Building b) {
        List<Building> out = new ArrayList<>(list);
        out.removeIf(x -> x.id().equals(b.id()));
        out.add(b);
        return new Buildings(out);
    }

    public Buildings without(UUID id) {
        List<Building> out = new ArrayList<>(list);
        out.removeIf(x -> x.id().equals(id));
        return new Buildings(out);
    }

    private Long2ObjectOpenHashMap<Building> index() {
        synchronized (INDEX) {
            return INDEX.computeIfAbsent(this, k -> {
                Long2ObjectOpenHashMap<Building> m = new Long2ObjectOpenHashMap<>();
                for (Building b : list) {
                    for (long l : b.blocks()) {
                        m.put(l, b);
                    }
                }
                return m;
            });
        }
    }

    /** Loads the class (registers the component) from the mod constructor. */
    public static void init() {}
}
