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
 * @param trunk where the trunk stands in each template (x, y = the ground layer, z), same order as templates
 */
public final class TemplateTreeFeature extends Feature<TemplateTreeFeature.Config> {
    public record Config(List<ResourceLocation> templates, List<Vec3i> trunk, boolean onSand)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("templates").forGetter(Config::templates),
                Vec3i.CODEC.listOf().fieldOf("trunk").forGetter(Config::trunk),
                Codec.BOOL.optionalFieldOf("on_sand", false).forGetter(Config::onSand))
                .apply(i, Config::new));
    }

    public TemplateTreeFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config config = context.config();
        if (config.templates().isEmpty() || config.templates().size() != config.trunk().size()) {
            return false;
        }
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockPos below = origin.below();
        BlockState soil = level.getBlockState(below);
        boolean soilOk = soil.is(BlockTags.DIRT) || config.onSand() && soil.is(BlockTags.SAND);
        if (!soilOk || !level.getBlockState(origin).canBeReplaced()) {
            return false;
        }
        int n = random.nextInt(config.templates().size());
        Optional<StructureTemplate> template = level.getLevel().getStructureManager().get(config.templates().get(n));
        if (template.isEmpty()) {
            return false;
        }
        Vec3i trunk = config.trunk().get(n);
        Rotation rotation = Rotation.getRandom(random);
        // the template's ground layer goes on the soil block under the origin, its trunk on the origin column
        BlockPos pivot = new BlockPos(trunk.getX(), 0, trunk.getZ());
        BlockPos corner = below.offset(-trunk.getX(), -trunk.getY(), -trunk.getZ());
        ChunkPos chunk = new ChunkPos(origin);
        BoundingBox writable = new BoundingBox(chunk.getMinBlockX() - 16, level.getMinBuildHeight(),
                chunk.getMinBlockZ() - 16, chunk.getMaxBlockX() + 16, level.getMaxBuildHeight() - 1,
                chunk.getMaxBlockZ() + 16);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot)
                .setBoundingBox(writable).setIgnoreEntities(true);
        return template.get().placeInWorld(level, corner, corner, settings, random, 2);
    }
}
