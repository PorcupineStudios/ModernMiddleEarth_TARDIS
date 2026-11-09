package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Feature {@code lotr_port:template_tree}: one of LOTR's hand-made tree shapes, grown by LOTR's own generators in the
 * exporter and saved as templates ({@code converter/trees.py}). Picks one of the shapes, turns it at random and sets
 * its trunk on the surface block at the origin, which must be soil a sapling could grow on (grass, dirt, podzol,
 * sand for desert trees when {@code on_sand}). Only blocks of the tree are placed; the air around it is left alone, so
 * trees grow into each other and into hillsides like LOTR's. The parts of big trees beyond the chunks a feature may
 * write are placed later, when their chunk is loaded ({@link com.porcupinestudios.lotrport.tree.TreeOverflow}).
 *
 * @param templates the shapes; their trunk base is at template {@code x0, ground, z0}
 * @param trunk the lowest x/z corner of each template's trunk, on its ground layer (same order as templates)
 * @param trunkWidth each trunk's width (2x2 and 3x3 trees); the trunk's corner lands on the origin however the tree
 *     is turned, so a square of saplings becomes the trunk
 */
public final class TemplateTreeFeature extends Feature<TemplateTreeFeature.Config> {
    public record Config(Optional<ResourceLocation> id, List<ResourceLocation> templates, List<Vec3i> trunk,
            List<Integer> trunkWidth, boolean onSand)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("id").forGetter(Config::id),
                ResourceLocation.CODEC.listOf().fieldOf("templates").forGetter(Config::templates),
                Vec3i.CODEC.listOf().fieldOf("trunk").forGetter(Config::trunk),
                Codec.INT.listOf().optionalFieldOf("trunk_width", List.of()).forGetter(Config::trunkWidth),
                Codec.BOOL.optionalFieldOf("on_sand", false).forGetter(Config::onSand))
                .apply(i, Config::new));
    }

    /** A tree block (template or world position). */
    public record Placed(BlockPos pos, BlockState state) {}

    /** Tests only (the world check's TreeCheck): told every tree grown, with the blocks it placed or left
     * for {@link com.porcupinestudios.lotrport.tree.TreeOverflow} (an empty list: refused, another tree in the way). Called from worldgen threads. */
    @javax.annotation.Nullable
    public static volatile java.util.function.Consumer<List<Placed>> audit;

    private static final Map<ResourceLocation, List<Placed>> BLOCKS = new ConcurrentHashMap<>();

    /** A template's blocks (read once from its saved form: trees have one palette and no processors). */
    private static List<Placed> blocks(WorldGenLevel level, ResourceLocation id, StructureTemplate template) {
        return BLOCKS.computeIfAbsent(id, k -> {
            CompoundTag tag = template.save(new CompoundTag());
            HolderGetter<Block> lookup = level.holderLookup(Registries.BLOCK);
            ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
            List<BlockState> states = new ArrayList<>();
            for (int i = 0; i < palette.size(); i++) {
                states.add(NbtUtils.readBlockState(lookup, palette.getCompound(i)));
            }
            List<Placed> out = new ArrayList<>();
            for (Tag t : tag.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag b = (CompoundTag) t;
                ListTag p = b.getList("pos", Tag.TAG_INT);
                out.add(new Placed(new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2)), states.get(b.getInt("state"))));
            }
            return List.copyOf(out);
        });
    }

    public TemplateTreeFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        return grow(context.level(), context.random(), context.origin(), context.config(), -1, false, null);
    }

    /**
     * Grows the tree with its trunk's lowest corner on {@code origin} (shape {@code pick}, or a random one if -1).
     * Saplings call this with the corner of their square and, for an odd square, its {@code middle}: LOTR grew those
     * trees round the middle, so the trunk log nearest the trunk's centre goes there (trunks are irregular).
     */
    public static boolean grow(WorldGenLevel level, RandomSource random, BlockPos origin, Config config, int pick,
            boolean planted, @javax.annotation.Nullable BlockPos middle) {
        if (config.templates().isEmpty() || config.templates().size() != config.trunk().size()) {
            return false;
        }
        BlockPos below = origin.below();
        BlockState soil = level.getBlockState(below);
        boolean soilOk = soil.is(BlockTags.DIRT) || config.onSand() && soil.is(BlockTags.SAND);
        if (!soilOk || !level.getBlockState(origin).canBeReplaced()) {
            return false;
        }
        int n = pick >= 0 && pick < config.templates().size() ? pick : random.nextInt(config.templates().size());
        int width = n < config.trunkWidth().size() ? Math.max(1, config.trunkWidth().get(n)) : 1;
        Optional<StructureTemplate> template = level.getLevel().getStructureManager().get(config.templates().get(n));
        if (template.isEmpty()) {
            return false;
        }
        Vec3i trunk = config.trunk().get(n);
        Rotation rotation = Rotation.getRandom(random);
        // the template's ground layer goes on the soil block under the origin, its trunk on the origin column
        BlockPos pivot = new BlockPos(trunk.getX(), 0, trunk.getZ());
        // turning about the trunk's corner block moves a wide trunk off it; shift it back onto the origin square
        int w = width - 1;
        int sx = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.CLOCKWISE_180 ? w : 0;
        int sz = rotation == Rotation.CLOCKWISE_180 || rotation == Rotation.COUNTERCLOCKWISE_90 ? w : 0;
        BlockPos corner = below.offset(-trunk.getX() + sx, -trunk.getY(), -trunk.getZ() + sz);
        ChunkPos chunk = new ChunkPos(origin);
        BoundingBox writable = new BoundingBox(chunk.getMinBlockX() - 16, level.getMinBuildHeight(),
                chunk.getMinBlockZ() - 16, chunk.getMaxBlockX() + 16, level.getMaxBuildHeight() - 1,
                chunk.getMaxBlockZ() + 16);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot)
                .setBoundingBox(writable).setIgnoreEntities(true);
        BlockPos shift = BlockPos.ZERO;
        if (middle != null) {
            double cx = origin.getX() + (width - 1) / 2.0;
            double cz = origin.getZ() + (width - 1) / 2.0;
            BlockPos nearest = null;
            double best = Double.MAX_VALUE;
            for (Placed b : blocks(level, config.templates().get(n), template.get())) {
                if (b.pos().getY() == trunk.getY() + 1 && b.state().is(BlockTags.LOGS)) {
                    BlockPos at = StructureTemplate.calculateRelativePosition(settings, b.pos()).offset(corner);
                    double d = (at.getX() - cx) * (at.getX() - cx) + (at.getZ() - cz) * (at.getZ() - cz);
                    if (d < best) {
                        best = d;
                        nearest = at;
                    }
                }
            }
            if (nearest != null) {
                shift = new BlockPos(middle.getX() - nearest.getX(), 0, middle.getZ() - nearest.getZ());
                corner = corner.offset(shift);
            }
        }
        List<Placed> tree = new ArrayList<>();
        for (Placed b : blocks(level, config.templates().get(n), template.get())) {
            BlockPos pos = StructureTemplate.calculateRelativePosition(settings, b.pos()).offset(corner);
            if (!level.isOutsideBuildHeight(pos) && !b.state().isAir()) {
                tree.add(new Placed(pos, b.state().rotate(rotation)));
            }
        }
        // LOTR's generators gave up when the trunk was blocked: no log into another tree's log, no trunk through
        // another tree's crown (trees grow apart instead of into each other)
        double tx = origin.getX() + shift.getX() + (width - 1) / 2.0;
        double tz = origin.getZ() + shift.getZ() + (width - 1) / 2.0;
        for (Placed b : tree) {
            if (!writable.isInside(b.pos()) || !b.state().is(BlockTags.LOGS)) {
                continue;
            }
            BlockState here = level.getBlockState(b.pos());
            boolean trunkColumn = Math.abs(b.pos().getX() - tx) <= width / 2.0
                    && Math.abs(b.pos().getZ() - tz) <= width / 2.0;
            if (here.is(BlockTags.LOGS) || trunkColumn && here.is(BlockTags.LEAVES)) {
                java.util.function.Consumer<List<Placed>> watcher = audit;
                if (watcher != null) {
                    watcher.accept(List.of()); // refused
                }
                return false;
            }
        }
        List<Placed> placed = new ArrayList<>();
        for (Placed b : tree) {
            if (!writable.isInside(b.pos())) {
                if (com.porcupinestudios.lotrport.tree.TreeOverflow.add(level.getLevel(), b.pos(), b.state())) {
                    placed.add(b);
                }
            } else if (fits(b.state(), level.getBlockState(b.pos()))) {
                level.setBlock(b.pos(), b.state(), 2);
                placed.add(b);
            }
        }
        java.util.function.Consumer<List<Placed>> watcher = audit;
        if (watcher != null) {
            watcher.accept(placed);
        }
        // remember the tree, so whoever fells it (colonists) takes all of it (see tree/Trees)
        StructurePlaceSettings unclipped = new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot);
        com.porcupinestudios.lotrport.tree.Trees.record(level.getLevel(), new com.porcupinestudios.lotrport.tree
                .TreeRecord(config.id().orElse(config.templates().get(n)), origin.offset(shift), width,
                        template.get().getBoundingBox(unclipped, corner), planted));
        return true;
    }

    /** LOTR's rule: logs go into air, plants, leaves and soil (roots); leaves and the rest only into air and plants. */
    public static boolean fits(BlockState tree, BlockState here) {
        if (here.canBeReplaced()) {
            return true;
        }
        return tree.is(BlockTags.LOGS) && (here.is(BlockTags.LEAVES) || here.is(BlockTags.DIRT) || here.is(BlockTags.SAND));
    }
}
