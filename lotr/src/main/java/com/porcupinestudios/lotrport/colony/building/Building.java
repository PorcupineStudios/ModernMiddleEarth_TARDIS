package com.porcupinestudios.lotrport.colony.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One colony building: a room measured from its door, of a {@link BuildingType}. Its own blocks ({@code cells}: open
 * space and furnishings) decide what belongs to it, so an L-shaped house and a storehouse in its empty corner never
 * share a chest. Immutable; {@link ColonyBuildings} replaces it when it is measured again.
 *
 * @param door the doorway's lowest block
 * @param origin the smallest corner of the cells; cells are packed as 10-bit x, y, z offsets from it
 * @param counts the room's measures (floor, beds, chests...), see {@link RoomScan}
 * @param checked game time of the last measure
 */
public record Building(UUID id, ResourceLocation type, BlockPos door, Optional<String> name, BlockPos origin,
        int[] cells, Map<String, Integer> counts, int quality, long built, long checked) {
    public static final Codec<Building> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Building::id),
            ResourceLocation.CODEC.fieldOf("type").forGetter(Building::type),
            BlockPos.CODEC.fieldOf("door").forGetter(Building::door),
            Codec.STRING.optionalFieldOf("name").forGetter(Building::name),
            BlockPos.CODEC.fieldOf("origin").forGetter(Building::origin),
            Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of).fieldOf("cells").forGetter(Building::cells),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counts", Map.of())
                    .forGetter(Building::counts),
            Codec.INT.optionalFieldOf("quality", 0).forGetter(Building::quality),
            Codec.LONG.optionalFieldOf("built", 0L).forGetter(Building::built),
            Codec.LONG.optionalFieldOf("checked", 0L).forGetter(Building::checked))
            .apply(i, Building::new));

    /** Largest room side a building can hold (10 bits per axis). */
    public static final int MAX_SIDE = 1023;

    public int count(String counter) {
        return counts.getOrDefault(counter, 0);
    }

    public int floor() {
        return count(RoomScan.FLOOR);
    }

    public Building measured(ResourceLocation newType, BlockPos newOrigin, int[] newCells, Map<String, Integer> newCounts,
            int newQuality, long now) {
        return new Building(id, newType, door, name, newOrigin, newCells, Map.copyOf(newCounts), newQuality, built, now);
    }

    public Building withType(ResourceLocation newType) {
        return new Building(id, newType, door, name, origin, cells, counts, quality, built, checked);
    }

    /** Its blocks as {@link BlockPos#asLong}. */
    public LongSet blocks() {
        LongOpenHashSet out = new LongOpenHashSet(cells.length);
        for (int c : cells) {
            out.add(BlockPos.asLong(origin.getX() + (c >> 20 & MAX_SIDE), origin.getY() + (c >> 10 & MAX_SIDE),
                    origin.getZ() + (c & MAX_SIDE)));
        }
        return out;
    }

    /** "Gimli's Forge (smithy)" or just the type. */
    public Component label() {
        Component t = BuildingType.titleOf(type);
        return name.<Component>map(n -> Component.literal(n + " (").append(t).append(")")).orElse(t);
    }

    /** Packs cells relative to their smallest corner; null if the room is wider than {@link #MAX_SIDE}. */
    static Packed pack(LongSet cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long l : cells) {
            int x = BlockPos.getX(l), y = BlockPos.getY(l), z = BlockPos.getZ(l);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        if (cells.isEmpty() || maxX - minX > MAX_SIDE || maxY - minY > MAX_SIDE || maxZ - minZ > MAX_SIDE) {
            return null;
        }
        int[] out = new int[cells.size()];
        int n = 0;
        for (long l : cells) {
            out[n++] = (BlockPos.getX(l) - minX) << 20 | (BlockPos.getY(l) - minY) << 10 | (BlockPos.getZ(l) - minZ);
        }
        java.util.Arrays.sort(out);
        return new Packed(new BlockPos(minX, minY, minZ), out);
    }

    record Packed(BlockPos origin, int[] cells) {}

    @Override
    public boolean equals(Object o) {
        return o instanceof Building b && b.id.equals(id) && b.type.equals(type) && b.door.equals(door)
                && b.name.equals(name) && b.origin.equals(origin) && java.util.Arrays.equals(b.cells, cells)
                && b.counts.equals(counts) && b.quality == quality && b.built == built && b.checked == checked;
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + java.util.Arrays.hashCode(cells);
    }
}
