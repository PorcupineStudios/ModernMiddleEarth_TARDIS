package com.porcupinestudios.lotrport.land;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.Owner;
import com.porcupinestudios.lotrport.society.SocietyEvent;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Every land claim, saved in the overworld's data storage, with an in-memory index of chunk to claim per dimension
 * (rebuilt on load) so the protection checks on every block break and click cost one map lookup. Claims of
 * different owners never overlap; a colony's own banners may overlap its other land.
 */
public final class Claims extends SavedData {
    public static final String FILE = "lotr_port_claims";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation CLAIMED_HISTORY = LotrPort.id("land_claimed");
    public static final ResourceLocation RELEASED_HISTORY = LotrPort.id("land_released");
    private static final SavedData.Factory<Claims> FACTORY = new SavedData.Factory<>(Claims::new, Claims::load, null);

    private final Map<UUID, Claim> claims = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<List<Claim>>> index = new HashMap<>();

    public static Claims get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public static void register() {
        LandSettings.register();
        NeoForge.EVENT_BUS.addListener((SocietyEvent.ColonyFounded e) -> get(e.server()).add(e.server(),
                Claim.around(e.colony().id(), e.colony().asOwner(), e.colony().dimension(), e.colony().hall(),
                        LandSettings.get().hallRadius(), Optional.empty())));
        NeoForge.EVENT_BUS.addListener((SocietyEvent.ColonyDisbanded e) -> {
            Claims c = get(e.server());
            for (Claim claim : c.ownedBy(e.colony().asOwner())) {
                c.remove(e.server(), claim.id());
            }
        });
    }

    public Collection<Claim> all() {
        return claims.values();
    }

    public Optional<Claim> get(UUID id) {
        return Optional.ofNullable(claims.get(id));
    }

    public List<Claim> ownedBy(Owner owner) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : claims.values()) {
            if (c.owner().equals(owner)) {
                out.add(c);
            }
        }
        return out;
    }

    /** The claim on this chunk, if any (the first, for a colony's overlapping banners: same owner either way). */
    public Optional<Claim> at(ResourceKey<Level> dimension, int cx, int cz) {
        Long2ObjectOpenHashMap<List<Claim>> d = index.get(dimension);
        if (d == null) {
            return Optional.empty();
        }
        List<Claim> l = d.get(ChunkPos.asLong(cx, cz));
        return l == null || l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    public Optional<Claim> at(ResourceKey<Level> dimension, BlockPos pos) {
        return at(dimension, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** Whether a claim like this would cover land another owner holds. */
    public Optional<Claim> conflict(Claim claim) {
        for (int x = claim.centerX() - claim.radius(); x <= claim.centerX() + claim.radius(); x++) {
            for (int z = claim.centerZ() - claim.radius(); z <= claim.centerZ() + claim.radius(); z++) {
                Optional<Claim> other = at(claim.dimension(), x, z);
                if (other.isPresent() && !other.get().owner().equals(claim.owner())) {
                    return other;
                }
            }
        }
        return Optional.empty();
    }

    /** Adds a claim (refused, returning false, if it overlaps another owner's land). History {@code land_claimed}. */
    public boolean add(MinecraftServer server, Claim claim) {
        if (conflict(claim).isPresent()) {
            return false;
        }
        Claim old = claims.put(claim.id(), claim);
        if (old != null) {
            unindex(old);
        }
        index(claim);
        setDirty();
        log(server, CLAIMED_HISTORY, claim);
        return true;
    }

    public void remove(MinecraftServer server, UUID id) {
        Claim c = claims.remove(id);
        if (c != null) {
            unindex(c);
            setDirty();
            log(server, RELEASED_HISTORY, c);
        }
    }

    /** Hands every claim of one owner to another (a colony taken in war keeps its land). */
    public void transfer(Owner from, Owner to) {
        for (Claim c : ownedBy(from)) {
            unindex(c);
            Claim moved = new Claim(c.id(), to, c.dimension(), c.centerX(), c.centerZ(), c.radius(), c.banner());
            claims.put(c.id(), moved);
            index(moved);
        }
        setDirty();
    }

    private void index(Claim c) {
        Long2ObjectOpenHashMap<List<Claim>> d = index.computeIfAbsent(c.dimension(), k -> new Long2ObjectOpenHashMap<>());
        for (int x = c.centerX() - c.radius(); x <= c.centerX() + c.radius(); x++) {
            for (int z = c.centerZ() - c.radius(); z <= c.centerZ() + c.radius(); z++) {
                d.computeIfAbsent(ChunkPos.asLong(x, z), k -> new ArrayList<>(1)).add(c);
            }
        }
    }

    private void unindex(Claim c) {
        Long2ObjectOpenHashMap<List<Claim>> d = index.get(c.dimension());
        if (d == null) {
            return;
        }
        for (int x = c.centerX() - c.radius(); x <= c.centerX() + c.radius(); x++) {
            for (int z = c.centerZ() - c.radius(); z <= c.centerZ() + c.radius(); z++) {
                long k = ChunkPos.asLong(x, z);
                List<Claim> l = d.get(k);
                if (l != null) {
                    l.removeIf(o -> o.id().equals(c.id()));
                    if (l.isEmpty()) {
                        d.remove(k);
                    }
                }
            }
        }
    }

    private static void log(MinecraftServer server, ResourceLocation type, Claim c) {
        CompoundTag tag = (CompoundTag) Claim.CODEC.encodeStart(NbtOps.INSTANCE, c).getOrThrow();
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), type, c.dimension(),
                Optional.of(c.banner().orElse(c.center().getMiddleBlockPosition(64))), tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("claims", Codec.list(Claim.CODEC).encodeStart(NbtOps.INSTANCE, List.copyOf(claims.values()))
                .getOrThrow());
        return tag;
    }

    public static Claims load(CompoundTag tag, HolderLookup.Provider registries) {
        Claims out = new Claims();
        if (tag.contains("claims")) {
            Codec.list(Claim.CODEC).parse(NbtOps.INSTANCE, tag.get("claims"))
                    .resultOrPartial(e -> LotrPort.LOG.error("[claims] {}", e))
                    .ifPresent(l -> l.forEach(c -> {
                        out.claims.put(c.id(), c);
                        out.index(c);
                    }));
        }
        return out;
    }
}
