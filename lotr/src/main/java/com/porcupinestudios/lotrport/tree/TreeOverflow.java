package com.porcupinestudios.lotrport.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The parts of LOTR's big trees (Fangorn oaks reach 25+ blocks from the trunk) that fall outside what a 1.21 feature
 * may write (its chunk and 16 blocks round it), kept per chunk ({@code lotr_port_tree_overflow}) and placed once that
 * chunk is fully generated and loaded, into air, plants and leaves only. Same threading as {@link TreeRecords}.
 */
public final class TreeOverflow extends SavedData {
    public static final String FILE = "lotr_port_tree_overflow";
    public static final int DATA_VERSION = 1;

    public record Entry(BlockPos pos, BlockState state) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Entry::state)).apply(i, Entry::new));
    }

    private static final Codec<Map<String, List<Entry>>> CHUNKS =
            Codec.unboundedMap(Codec.STRING, Entry.CODEC.listOf());
    private static final SavedData.Factory<TreeOverflow> FACTORY = new SavedData.Factory<>(TreeOverflow::new,
            TreeOverflow::load, null);
    private static final Map<ResourceKey<Level>, TreeOverflow> OPEN = new ConcurrentHashMap<>();

    private final Map<Long, List<Entry>> byChunk = new ConcurrentHashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                OPEN.put(level.dimension(), level.getDataStorage().computeIfAbsent(FACTORY, FILE));
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                OPEN.remove(level.dimension());
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 10 == 0) {
                TreeOverflow store = OPEN.get(level.dimension());
                if (store != null) {
                    store.placeLoaded(level);
                }
            }
        });
    }

    /** Keeps a tree block for later; false before the level's store has loaded. */
    public static boolean add(ServerLevel level, BlockPos pos, BlockState state) {
        TreeOverflow store = OPEN.get(level.dimension());
        if (store == null) {
            return false;
        }
        store.put(pos.immutable(), state);
        return true;
    }

    private synchronized void put(BlockPos pos, BlockState state) {
        byChunk.computeIfAbsent(ChunkPos.asLong(pos), k -> new ArrayList<>()).add(new Entry(pos, state));
        setDirty();
    }

    private synchronized List<Entry> take(long chunk) {
        List<Entry> out = byChunk.remove(chunk);
        if (out != null) {
            setDirty();
        }
        return out;
    }

    private void placeLoaded(ServerLevel level) {
        List<Long> ready = new ArrayList<>();
        synchronized (this) {
            for (long key : byChunk.keySet()) {
                if (level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key))) {
                    ready.add(key);
                }
            }
        }
        for (long key : ready) {
            List<Entry> entries = take(key);
            for (Entry e : entries == null ? List.<Entry>of() : entries) {
                BlockState here = level.getBlockState(e.pos());
                if (com.porcupinestudios.lotrport.world.feature.TemplateTreeFeature.fits(e.state(), here)) {
                    level.setBlock(e.pos(), e.state(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        Map<String, List<Entry>> chunks = new java.util.TreeMap<>();
        byChunk.forEach((k, v) -> chunks.put(Long.toString(k), List.copyOf(v)));
        tag.put("chunks", CHUNKS.encodeStart(NbtOps.INSTANCE, chunks).getOrThrow());
        return tag;
    }

    public static TreeOverflow load(CompoundTag tag, HolderLookup.Provider registries) {
        TreeOverflow out = new TreeOverflow();
        if (tag.contains("chunks")) {
            CHUNKS.parse(NbtOps.INSTANCE, tag.get("chunks"))
                    .resultOrPartial(e -> LotrPort.LOG.error("[tree overflow] {}", e))
                    .ifPresent(m -> m.forEach((k, v) -> out.byChunk.put(Long.parseLong(k), new ArrayList<>(v))));
        }
        return out;
    }
}
