package com.porcupinestudios.lotrport.colony.building;

import com.porcupinestudios.lotrport.LotrPort;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * RingRealms' 3D room fill ({@code Validator.flood3d}): from the open block inside the door, spread through every
 * open block in six directions, so stairs, ladders and holes in a floor reach the other storeys. Blocks that stop
 * movement are walls, floors and ceilings, unless they are furnishings (a block entity, a bed, anything a counter tag
 * names, anything that gives light): those belong to the room and are counted, but the fill doesn't pass them. The
 * doorway is never a way out. Every open block needs a roof within {@code max_roof}.
 */
public final class RoomScan {
    /** Counter for blocks that give light (any block, no tag). */
    public static final String LIGHTS = "lights";
    public static final String FLOOR = "floor";
    public static final String STOREYS = "storeys";
    public static final String VOLUME = "volume";

    private RoomScan() {}

    /**
     * What a fill found.
     *
     * @param problem why it isn't a room (null when it is)
     * @param cells the room's own blocks: open space and furnishings ({@link BlockPos#asLong})
     */
    public record Room(Component problem, LongSet cells, Map<String, Integer> counts) {
        public boolean ok() {
            return problem == null;
        }

        public int count(String counter) {
            return counts.getOrDefault(counter, 0);
        }

        static Room fail(String key, String fallback, Object... args) {
            return new Room(Component.translatableWithFallback(key, fallback, args), new LongOpenHashSet(), Map.of());
        }
    }

    public static TagKey<Block> tag(String counter) {
        return TagKey.create(Registries.BLOCK, LotrPort.id("colony/" + counter));
    }

    /** Open = something a person walks through (air, plants, torches, carpets, open gates) or climbs. */
    public static boolean open(Level level, BlockPos pos, BlockState state) {
        return state.getCollisionShape(level, pos).isEmpty() || state.is(BlockTags.CLIMBABLE);
    }

    static boolean furnishing(BlockState state, Map<String, TagKey<Block>> tags) {
        if (state.hasBlockEntity() || state.getBlock() instanceof BedBlock || state.getLightEmission() > 0) {
            return true;
        }
        for (TagKey<Block> t : tags.values()) {
            if (state.is(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fills the room from {@code start}, never stepping into {@code doorway}.
     *
     * @param counters the counters to count (block tags {@code lotr_port:colony/<counter>})
     */
    public static Room scan(Level level, BlockPos start, Set<BlockPos> doorway, BuildingSettings s, Set<String> counters) {
        Map<String, TagKey<Block>> tags = new LinkedHashMap<>();
        for (String c : counters) {
            if (!c.equals(FLOOR) && !c.equals(STOREYS) && !c.equals(VOLUME) && !c.equals(LIGHTS)) {
                tags.put(c, tag(c));
            }
        }
        LongOpenHashSet door = new LongOpenHashSet();
        doorway.forEach(p -> door.add(p.asLong()));
        int maxVolume = Math.max(1, s.maxFloor() * s.volumePerFloor());
        LongOpenHashSet seen = new LongOpenHashSet();
        LongOpenHashSet cells = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        if (!level.isLoaded(start) || !open(level, start, level.getBlockState(start))) {
            return Room.fail("colony.lotr_port.room.blocked", "The doorway is blocked inside");
        }
        seen.add(start.asLong());
        queue.enqueue(start.asLong());
        Map<String, Integer> counts = new HashMap<>();
        Map<Integer, Integer> floorByY = new TreeMap<>();
        int open = 0;
        while (!queue.isEmpty()) {
            long p = queue.dequeueLong();
            at.set(p);
            cells.add(p);
            open++;
            if (open > maxVolume) {
                return Room.fail("colony.lotr_port.room.leaks", "The room is open to the outside, or too big");
            }
            if (!roofed(level, at, s.maxRoof())) {
                return Room.fail("colony.lotr_port.room.no_roof", "Part of the room has no roof (at %s %s %s)",
                        at.getX(), at.getY(), at.getZ());
            }
            BlockState here = level.getBlockState(at);
            count(here, tags, counts);
            for (Direction d : Direction.values()) {
                long n = BlockPos.offset(p, d);
                if (door.contains(n) || !seen.add(n)) {
                    continue;
                }
                BlockPos np = BlockPos.of(n);
                if (!level.isLoaded(np)) {
                    return Room.fail("colony.lotr_port.room.unloaded", "Part of the room isn't loaded");
                }
                if (level.isOutsideBuildHeight(np)) {
                    return Room.fail("colony.lotr_port.room.leaks", "The room is open to the outside, or too big");
                }
                BlockState ns = level.getBlockState(np);
                if (open(level, np, ns)) {
                    queue.enqueue(n);
                } else if (furnishing(ns, tags)) {
                    cells.add(n);
                    count(ns, tags, counts);
                }
            }
        }
        // floor: a room block (open or a furnishing) standing on the building itself, with room above it; a block
        // standing on a chest or bed is not floor (RingRealms' 11x11 L counts 56)
        for (long l : cells) {
            long below = BlockPos.offset(l, Direction.DOWN);
            if (!cells.contains(below) && cells.contains(BlockPos.offset(l, Direction.UP))) {
                floorByY.merge(BlockPos.getY(l), 1, Integer::sum);
            }
        }
        int floor = floorByY.values().stream().mapToInt(Integer::intValue).sum();
        if (floor > s.maxFloor()) {
            return Room.fail("colony.lotr_port.room.too_big", "The room has %s floor blocks; at most %s", floor,
                    s.maxFloor());
        }
        counts.put(FLOOR, floor);
        counts.put(STOREYS, (int) floorByY.values().stream().filter(n -> n >= s.storeyFloor()).count());
        counts.put(VOLUME, open);
        return new Room(null, cells, counts);
    }

    private static void count(BlockState state, Map<String, TagKey<Block>> tags, Map<String, Integer> counts) {
        if (state.isAir()) {
            return;
        }
        // a bed is two blocks: count its head
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) != BedPart.HEAD) {
            return;
        }
        if (state.getLightEmission() > 0) {
            counts.merge(LIGHTS, 1, Integer::sum);
        }
        for (Map.Entry<String, TagKey<Block>> e : tags.entrySet()) {
            if (state.is(e.getValue())) {
                counts.merge(e.getKey(), 1, Integer::sum);
            }
        }
    }

    private static boolean roofed(Level level, BlockPos pos, int maxRoof) {
        BlockPos.MutableBlockPos up = pos.mutable();
        for (int i = 0; i < maxRoof; i++) {
            up.move(Direction.UP);
            if (level.isOutsideBuildHeight(up)) {
                return false;
            }
            if (!open(level, up, level.getBlockState(up))) {
                return true;
            }
        }
        return false;
    }
}
