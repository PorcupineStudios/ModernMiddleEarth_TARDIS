package com.porcupinestudios.lotrport.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
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
 * trees grow into each other and into hillsides like LOTR's. Big trees are clipped to the chunks a feature may write.
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

    public TemplateTreeFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        return grow(context.level(), context.random(), context.origin(), context.config(), -1, false);
    }

    /**
     * Grows the tree with its trunk's lowest corner on {@code origin} (shape {@code pick}, or a random one if -1).
     * Saplings call this with the corner of their square.
     */
    public static boolean grow(WorldGenLevel level, RandomSource random, BlockPos origin, Config config, int pick,
            boolean planted) {
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
        if (!template.get().placeInWorld(level, corner, corner, settings, random, 2)) {
            return false;
        }
        // remember the tree, so whoever fells it (colonists) takes all of it (see tree/Trees)
        StructurePlaceSettings unclipped = new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot);
        com.porcupinestudios.lotrport.tree.Trees.record(level.getLevel(), new com.porcupinestudios.lotrport.tree
                .TreeRecord(config.id().orElse(config.templates().get(n)), origin, width,
                        template.get().getBoundingBox(unclipped, corner), planted));
        return true;
    }
}
