package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Feature {@code lotr_port:delve}: builds a delve (Moria; later Goblin Town) chunk by chunk. Its config is the
 * delve's whole spec ({@link DelveSpec}, kept as JSON), so the configured feature file is the one place a pack maker
 * tunes it. The plan ({@link DelvePlanner}) is made once per world seed from the map's ground height and the delve's
 * template rooms ({@code structure/<templates>/**.nbt}, their jigsaw blocks the doorways), then every chunk draws only
 * its own columns. Placed once per chunk in every Middle-earth biome ({@code underground_structures} step); chunks
 * outside the plan's box return at once.
 */
public final class DelveFeature extends Feature<DelveFeature.Config> {
    /** The spec as raw JSON, read by the game-free {@link DelveSpec}. */
    public record Config(JsonElement spec) implements FeatureConfiguration {
        private static final Codec<JsonElement> JSON = Codec.PASSTHROUGH.xmap(
                d -> d.convert(JsonOps.INSTANCE).getValue(), j -> new Dynamic<>(JsonOps.INSTANCE, j));
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                JSON.fieldOf("spec").forGetter(Config::spec)).apply(i, Config::new));
    }

    private record Cached(Config config, long seed, DelvePlan plan) {}

    private static final List<Cached> PLANS = new CopyOnWriteArrayList<>();

    public DelveFeature() {
        super(Config.CODEC);
    }

    /** Forgets every plan (a new world may have another seed or other datapacks). */
    public static void clear() {
        PLANS.clear();
        SCATTERED.clear();
    }

    /** The plan of a delve for a world seed, made on first use. */
    public static DelvePlan plan(MinecraftServer server, Config config, long seed) {
        for (Cached c : PLANS) {
            if (c.config == config && c.seed == seed) {
                return c.plan;
            }
        }
        synchronized (PLANS) {
            for (Cached c : PLANS) {
                if (c.config == config && c.seed == seed) {
                    return c.plan;
                }
            }
            DelvePlan plan = makePlan(server, config, seed);
            PLANS.add(new Cached(config, seed, plan));
            return plan;
        }
    }

    /** A fresh plan, never cached (the GameTests check that two come out the same). */
    public static DelvePlan makePlan(MinecraftServer server, Config config, long seed) {
        long t = System.nanoTime();
        DelveSpec spec = DelveSpec.parse(config.spec().getAsJsonObject());
        List<DelvePlanner.TemplateInfo> templates = templates(server, spec.templates);
        DelvePlan plan;
        if (spec.layout.equals("goblin")) {
            int[] anchor = spec.json.has("anchor") ? J.ints(spec.json.getAsJsonArray("anchor")) : new int[2];
            plan = spec.scatter != null ? null : GoblinPlanner.plan(spec, seed, ground(), anchor[0], anchor[1]);
            if (plan == null) {
                plan = empty(spec, seed);
            }
        } else {
            plan = DelvePlanner.plan(spec, seed, ground(), templates);
        }
        LotrPort.LOG.info("[delve] {} planned in {} ms with {} template rooms", spec.name,
                (System.nanoTime() - t) / 1_000_000, templates.size());
        for (String line : plan.summary.split("\n")) {
            LotrPort.LOG.info("[delve] {}", line);
        }
        return plan;
    }

    private static DelvePlan empty(DelveSpec spec, long seed) {
        return new DelvePlan(spec, seed, 0, 0, List.of(), List.of(), List.of(), List.of(), false,
                "delve " + spec.name + ": nothing placed", Map.of());
    }

    /** The map's ground, and its biome ids for scattered delves. */
    public static Ground ground() {
        MiddleEarthMap map = MiddleEarthMap.get();
        return new Ground() {
            @Override
            public double height(int x, int z) {
                return map.heightAt(x, z);
            }

            @Override
            public String biome(int x, int z) {
                int i = map.biomeIndexAt(x, z);
                return i >= 0 && i < map.biomes().size() ? map.biomes().get(i).id() : null;
            }
        };
    }

    // ---- scattered delves: a copy per region at most ----

    private record Scattered(Config config, long seed, DelveSpec spec, Map<Long, Optional<DelvePlan>> regions) {}

    private static final List<Scattered> SCATTERED = new CopyOnWriteArrayList<>();
    private static final int REGION_CACHE = 512;

    private static Scattered scattered(Config config, long seed) {
        for (Scattered s : SCATTERED) {
            if (s.config == config && s.seed == seed) {
                return s;
            }
        }
        synchronized (SCATTERED) {
            for (Scattered s : SCATTERED) {
                if (s.config == config && s.seed == seed) {
                    return s;
                }
            }
            Map<Long, Optional<DelvePlan>> cache = java.util.Collections.synchronizedMap(
                    new java.util.LinkedHashMap<>(64, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<Long, Optional<DelvePlan>> e) {
                            return size() > REGION_CACHE;
                        }
                    });
            Scattered s = new Scattered(config, seed, DelveSpec.parse(config.spec().getAsJsonObject()), cache);
            SCATTERED.add(s);
            return s;
        }
    }

    /** The copy of a scattered delve in one region (planned once, kept while used), if it has one. */
    private static Optional<DelvePlan> region(Scattered s, int rx, int rz) {
        long key = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
        Optional<DelvePlan> got = s.regions.get(key);
        if (got != null) {
            return got;
        }
        DelveSpec.Scatter sc = s.spec.scatter;
        long h = Rng.hash(s.seed ^ s.spec.salt, rx, rz, 401);
        Optional<DelvePlan> plan = Optional.empty();
        if (Rng.unit(h) < sc.chance()) {
            int span = Math.max(1, sc.region() - 2 * sc.margin());
            int x = rx * sc.region() + sc.margin() + Rng.range(Rng.mix(h), 0, span - 1);
            int z = rz * sc.region() + sc.margin() + Rng.range(Rng.mix(h + 1), 0, span - 1);
            Ground g = ground();
            String biome = g.biome(x, z);
            if (biome != null && sc.biomes().contains(biome) && !excluded(s.spec, x, z)) {
                plan = Optional.ofNullable(GoblinPlanner.plan(s.spec, s.seed, g, x, z));
            }
        }
        s.regions.put(key, plan);
        return plan;
    }

    private static boolean excluded(DelveSpec spec, int x, int z) {
        if (!spec.json.getAsJsonObject("scatter").has("exclude")) {
            return false;
        }
        for (var e : spec.json.getAsJsonObject("scatter").getAsJsonArray("exclude")) {
            int[] c = J.ints(e.getAsJsonArray());
            if (Math.hypot(x - c[0], z - c[1]) < c[2]) {
                return true;
            }
        }
        return false;
    }

    /** The plans (one, or the scattered copies) that reach into these columns. */
    public static List<DelvePlan> plansNear(MinecraftServer server, Config config, long seed, int x0, int z0, int x1,
                                            int z1) {
        DelveSpec.Scatter sc = scattered(config, seed).spec.scatter;
        if (sc == null) {
            DelvePlan p = plan(server, config, seed);
            return p.touches(x0, z0, x1, z1) ? List.of(p) : List.of();
        }
        Scattered s = scattered(config, seed);
        List<DelvePlan> out = new ArrayList<>();
        for (int rx = Math.floorDiv(x0 - sc.reach(), sc.region()); rx <= Math.floorDiv(x1 + sc.reach(), sc.region()); rx++) {
            for (int rz = Math.floorDiv(z0 - sc.reach(), sc.region()); rz <= Math.floorDiv(z1 + sc.reach(), sc.region()); rz++) {
                region(s, rx, rz).filter(p -> p.touches(x0, z0, x1, z1)).ifPresent(out::add);
            }
        }
        return out;
    }

    /** The config of a configured delve feature by id ({@code lotr_port:moria}), if the datapacks have it. */
    public static Optional<Config> config(MinecraftServer server, ResourceLocation id) {
        return server.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getOptional(id)
                .map(ConfiguredFeature::config).filter(Config.class::isInstance).map(Config.class::cast);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        List<DelvePlan> plans = plansNear(level.getServer(), context.config(), level.getSeed(), x0, z0, x0 + 15,
                z0 + 15);
        int built = 0;
        for (DelvePlan plan : plans) {
            built += plan.build(new LevelCanvas(level, x0, z0, context.random()));
        }
        return built > 0;
    }

    // ---- template rooms ----

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** Every template under {@code structure/<folder>/} (any namespace), with its size and jigsaw doorways. */
    static List<DelvePlanner.TemplateInfo> templates(MinecraftServer server, String folder) {
        List<DelvePlanner.TemplateInfo> out = new ArrayList<>();
        if (folder == null || server == null) {
            return out;
        }
        Map<ResourceLocation, Resource> found = server.getResourceManager().listResources("structure/" + folder,
                rl -> rl.getPath().endsWith(".nbt"));
        List<ResourceLocation> ids = new ArrayList<>(found.keySet());
        ids.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation file : ids) {
            String path = file.getPath().substring("structure/".length(), file.getPath().length() - ".nbt".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
            Optional<StructureTemplate> t = server.getStructureManager().get(id);
            if (t.isEmpty()) {
                continue;
            }
            List<int[]> doors = new ArrayList<>();
            for (StructureTemplate.StructureBlockInfo info : t.get().filterBlocks(BlockPos.ZERO,
                    new StructurePlaceSettings(), Blocks.JIGSAW)) {
                Direction front = info.state().getValue(JigsawBlock.ORIENTATION).front();
                int side = List.of(SIDES).indexOf(front);
                if (side >= 0) {
                    doors.add(new int[] {info.pos().getX(), info.pos().getY(), info.pos().getZ(), side});
                }
            }
            var size = t.get().getSize();
            out.add(new DelvePlanner.TemplateInfo(id.toString(), size.getX(), size.getY(), size.getZ(), doors));
        }
        return out;
    }

    // ---- writing blocks ----

    private static final Rotation[] ROTATIONS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180,
        Rotation.COUNTERCLOCKWISE_90};

    /** The block state for a delve block (cached on the {@link Mat}); unknown blocks are stone, logged once. */
    static BlockState state(Mat m) {
        Object r = m.resolved;
        if (r instanceof BlockState s) {
            return s;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(m.id)).orElse(null);
        if (block == null) {
            LotrPort.LOG.warn("[delve] no block {}, using stone", m.id);
            block = Blocks.STONE;
        }
        BlockState s = block.defaultBlockState();
        for (Map.Entry<String, String> e : m.props.entrySet()) {
            Property<?> p = block.getStateDefinition().getProperty(e.getKey());
            if (p != null) {
                s = with(s, p, e.getValue());
            }
        }
        m.resolved = s;
        return s;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, String value) {
        return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
    }

    /** Whether a delve block id names a real block (the GameTests check every palette). */
    public static boolean blockExists(String id) {
        return id.equals("keep") || BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(id));
    }

    /** One chunk's columns of a world being generated. */
    private static final class LevelCanvas implements Canvas {
        private final WorldGenLevel level;
        private final int x0;
        private final int z0;
        private final RandomSource random;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        LevelCanvas(WorldGenLevel level, int x0, int z0, RandomSource random) {
            this.level = level;
            this.x0 = x0;
            this.z0 = z0;
            this.random = random;
        }

        @Override
        public int minX() {
            return x0;
        }

        @Override
        public int maxX() {
            return x0 + 15;
        }

        @Override
        public int minZ() {
            return z0;
        }

        @Override
        public int maxZ() {
            return z0 + 15;
        }

        @Override
        public int minY() {
            return level.getMinBuildHeight();
        }

        @Override
        public int maxY() {
            return level.getMaxBuildHeight() - 1;
        }

        @Override
        public void set(int x, int y, int z, Mat mat) {
            if (mat == Mat.KEEP) {
                return;
            }
            BlockState s = state(mat);
            pos.set(x, y, z);
            level.setBlock(pos, s, Block.UPDATE_CLIENTS);
            Block b = s.getBlock();
            if (b instanceof WallBlock || b instanceof CrossCollisionBlock || b instanceof StairBlock) {
                level.getChunk(pos).markPosForPostprocessing(pos);
            }
            if (mat.loot != null) {
                RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(),
                        ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(mat.loot)));
            }
        }

        @Override
        public boolean solid(int x, int y, int z) {
            BlockState s = level.getBlockState(pos.set(x, y, z));
            return !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced();
        }

        @Override
        public void template(String id, int rotation, int x, int y, int z) {
            ServerLevel server = level.getLevel();
            Optional<StructureTemplate> t = server.getStructureManager().get(ResourceLocation.parse(id));
            if (t.isEmpty()) {
                return;
            }
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(ROTATIONS[rotation & 3])
                    .setBoundingBox(new BoundingBox(minX(), minY(), minZ(), maxX(), maxY(), maxZ()))
                    .addProcessor(JigsawReplacementProcessor.INSTANCE)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
            BoundingBox box = t.get().getBoundingBox(settings, BlockPos.ZERO);
            BlockPos at = new BlockPos(x - box.minX(), y - box.minY(), z - box.minZ());
            t.get().placeInWorld(level, at, at, settings, random, Block.UPDATE_CLIENTS);
        }
    }

    /** Whether a loot table exists (for the GameTests). */
    public static boolean lootExists(MinecraftServer server, String id) {
        return server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.parse(id))) != LootTable.EMPTY;
    }

    /** The spec of a config, parsed (for tests and tools). */
    public static DelveSpec spec(Config config) {
        JsonObject o = config.spec().getAsJsonObject();
        return DelveSpec.parse(o);
    }
}
