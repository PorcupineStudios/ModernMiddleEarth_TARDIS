package com.porcupinestudios.lotrport.road;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.map.Roads;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Lays road works on the ground (lotr/docs/roads.md): a stretch's tier goes down in every loaded chunk it touches, a
 * chunk at a time ({@code road_works/settings.json columns_per_tick}), and again when a chunk it hasn't been laid in
 * loads. Only natural ground ({@code #lotr_port:road_surface}), road tiers' own blocks and LOTR's road blocks are
 * replaced, never what players built; water and air columns are skipped, and plants on the road are cleared.
 */
public final class RoadPaving {
    public static final TagKey<Block> SURFACE = TagKey.create(Registries.BLOCK, LotrPort.id("road_surface"));

    private record Job(int stretch, long chunk) {}

    private static final Map<ResourceKey<Level>, ArrayDeque<Job>> JOBS = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Map.Entry<ResourceKey<Level>, Long>> LOADED =
            new ConcurrentLinkedQueue<>();

    private RoadPaving() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load e) -> {
            if (e.getLevel() instanceof ServerLevel level && e.getChunk() instanceof LevelChunk chunk) {
                LOADED.add(Map.entry(level.dimension(), chunk.getPos().toLong()));
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level && level == level.getServer().overworld()) {
                tick(level.getServer());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            JOBS.clear();
            LOADED.clear();
        });
    }

    /** Queues the stretch's loaded, not yet laid chunks. */
    static void queue(MinecraftServer server, RoadWorks.Stretch s) {
        ServerLevel level = server.getLevel(s.dimension);
        if (level == null) {
            return;
        }
        for (long c : s.chunks(halfWidth(s))) {
            if (!s.paved.contains(c) && level.hasChunk(ChunkPos.getX(c), ChunkPos.getZ(c))) {
                JOBS.computeIfAbsent(s.dimension, k -> new ArrayDeque<>()).add(new Job(s.id, c));
            }
        }
    }

    static double halfWidth(RoadWorks.Stretch s) {
        return RoadData.tier(s.tier()).map(RoadTier::halfWidth).orElse(2.0);
    }

    /** Runs the queue now, all of it (tests and commands). */
    public static void flush(MinecraftServer server) {
        while (step(server, Integer.MAX_VALUE)) {
            // until nothing is left
        }
    }

    private static void tick(MinecraftServer server) {
        step(server, RoadData.settings().columnsPerTick());
    }

    /** Lays up to {@code budget} columns; whether anything was done. */
    private static boolean step(MinecraftServer server, int budget) {
        RoadWorks works = RoadWorks.get(server);
        Map.Entry<ResourceKey<Level>, Long> loaded;
        while ((loaded = LOADED.poll()) != null) {
            for (RoadWorks.Stretch s : works.touching(loaded.getKey(), loaded.getValue())) {
                if (!s.paved.contains(loaded.getValue())) {
                    JOBS.computeIfAbsent(s.dimension, k -> new ArrayDeque<>()).add(new Job(s.id, loaded.getValue()));
                }
            }
        }
        boolean did = false;
        Set<Block> replaceable = null;
        for (Map.Entry<ResourceKey<Level>, ArrayDeque<Job>> e : JOBS.entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            ArrayDeque<Job> jobs = e.getValue();
            while (budget > 0 && !jobs.isEmpty()) {
                Job job = jobs.poll();
                RoadWorks.Stretch s = works.stretch(job.stretch).orElse(null);
                if (level == null || s == null || s.paved.contains(job.chunk)
                        || !level.hasChunk(ChunkPos.getX(job.chunk), ChunkPos.getZ(job.chunk))) {
                    continue;
                }
                RoadTier tier = RoadData.tier(s.tier()).orElse(null);
                if (tier == null) {
                    continue;
                }
                if (replaceable == null) {
                    replaceable = replaceable();
                }
                lay(level, s, tier, job.chunk, replaceable);
                works.markPaved(s, job.chunk);
                budget -= 256;
                did = true;
            }
        }
        return did;
    }

    /** What road works may lay over: natural ground, every tier's blocks, LOTR's road blocks. */
    private static Set<Block> replaceable() {
        Set<Block> out = new HashSet<>();
        for (RoadTier t : RoadData.tiers().values()) {
            t.surface().forEach(p -> BuiltInRegistries.BLOCK.getOptional(p.block()).ifPresent(out::add));
        }
        for (String id : Roads.get().topBlocks()) {
            BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).ifPresent(out::add);
        }
        return out;
    }

    private static void lay(ServerLevel level, RoadWorks.Stretch s, RoadTier tier, long chunk, Set<Block> replaceable) {
        int x0 = ChunkPos.getX(chunk) << 4;
        int z0 = ChunkPos.getZ(chunk) << 4;
        double w = tier.halfWidth();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                if (s.distanceSq(x + 0.5, z + 0.5) > w * w) {
                    continue;
                }
                long h = hash(x, z, s.id);
                if (tier.coverage() < 1 && (h >>> 11) * 0x1.0p-53 >= tier.coverage()) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                pos.set(x, y, z);
                BlockState top = level.getBlockState(pos);
                if (!top.getFluidState().isEmpty() || !(top.is(SURFACE) || replaceable.contains(top.getBlock()))) {
                    continue;
                }
                Block block = pick(level, tier, pos, h);
                if (block == null || top.is(block)) {
                    continue;
                }
                level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
                pos.move(0, 1, 0);
                BlockState above = level.getBlockState(pos);
                if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The tier's block for a column: its own weighted list, or the biome's LOTR road ({@code biome_style}). */
    private static Block pick(ServerLevel level, RoadTier tier, BlockPos pos, long h) {
        List<RoadTier.Surface> own = tier.surface();
        if (tier.biomeStyle()) {
            String biome = level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("");
            Roads.Style style = Roads.get().style(biome);
            if (style != null && !style.top().isEmpty()) {
                double total = style.top().stream().mapToDouble(Roads.Pick::weight).sum();
                double r = (h & 0x7FFFFF) / (double) 0x800000 * total;
                for (Roads.Pick p : style.top()) {
                    r -= p.weight();
                    if (r < 0) {
                        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(p.block())).orElse(null);
                    }
                }
            }
        }
        if (own.isEmpty()) {
            return null;
        }
        double total = own.stream().mapToDouble(RoadTier.Surface::weight).sum();
        double r = (h & 0x7FFFFF) / (double) 0x800000 * total;
        for (RoadTier.Surface p : own) {
            r -= p.weight();
            if (r < 0) {
                return BuiltInRegistries.BLOCK.getOptional(p.block()).orElse(null);
            }
        }
        return BuiltInRegistries.BLOCK.getOptional(own.get(own.size() - 1).block()).orElse(null);
    }

    private static long hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }
}
