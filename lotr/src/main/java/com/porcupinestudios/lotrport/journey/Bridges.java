package com.porcupinestudios.lotrport.journey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Bridges players built and marked (SavedData {@code lotr_port_bridges}): routes may cross a river on one. Each was
 * checked by {@link BridgeRules} when marked; one that breaks is found out when a party's bodies can't get across
 * (the bodies' stuck rules), and its owner can mark it again or remove it with the marker.
 */
public final class Bridges extends SavedData {
    public static final String FILE = "lotr_port_bridges";
    public static final int DATA_VERSION = 1;
    /** How close to the line between its ends a route point counts as on the bridge, in blocks. */
    static final double REACH = 3;

    /** A marked bridge, end to end, in a dimension. */
    public record Bridge(UUID id, ResourceKey<Level> dimension, BlockPos a, BlockPos b, UUID owner, long marked) {
        public static final Codec<Bridge> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Bridge::id),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Bridge::dimension),
                BlockPos.CODEC.fieldOf("a").forGetter(Bridge::a),
                BlockPos.CODEC.fieldOf("b").forGetter(Bridge::b),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Bridge::owner),
                Codec.LONG.fieldOf("marked").forGetter(Bridge::marked))
                .apply(i, Bridge::new));

        /** Within {@link #REACH} of the line between its ends. */
        public boolean covers(int x, int z) {
            double ax = a.getX() + 0.5;
            double az = a.getZ() + 0.5;
            double dx = b.getX() - a.getX();
            double dz = b.getZ() - a.getZ();
            double len2 = dx * dx + dz * dz;
            double f = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x + 0.5 - ax) * dx + (z + 0.5 - az) * dz) / len2));
            double px = ax + dx * f - x - 0.5;
            double pz = az + dz * f - z - 0.5;
            return px * px + pz * pz <= REACH * REACH;
        }

        public double length() {
            return Math.sqrt(a.distSqr(b));
        }
    }

    private static final SavedData.Factory<Bridges> FACTORY = new SavedData.Factory<>(Bridges::new, Bridges::load,
            null);

    private final List<Bridge> bridges = new ArrayList<>();
    private int version;

    public static Bridges get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public List<Bridge> all() {
        return List.copyOf(bridges);
    }

    public List<Bridge> in(ResourceKey<Level> dim) {
        return bridges.stream().filter(b -> b.dimension().equals(dim)).toList();
    }

    /** Bumped on every change, so cached routes know to plan again. */
    public int version() {
        return version;
    }

    public void add(Bridge b) {
        bridges.add(b);
        version++;
        setDirty();
    }

    public boolean remove(UUID id) {
        boolean gone = bridges.removeIf(b -> b.id().equals(id));
        if (gone) {
            version++;
            setDirty();
        }
        return gone;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("bridges", Bridge.CODEC.listOf().encodeStart(NbtOps.INSTANCE, bridges).getOrThrow());
        return tag;
    }

    public static Bridges load(CompoundTag tag, HolderLookup.Provider registries) {
        Bridges out = new Bridges();
        Bridge.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("bridges"))
                .resultOrPartial(e -> LotrPort.LOG.error("[bridges] {}", e)).ifPresent(out.bridges::addAll);
        return out;
    }
}
