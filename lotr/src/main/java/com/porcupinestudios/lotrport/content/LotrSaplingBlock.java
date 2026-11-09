package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.world.feature.TemplateTreeFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LOTR's saplings: grow like vanilla's (random ticks in light, two stages; bone meal) into what
 * {@link SaplingRules} says, checking LOTR's arrangements first: a 9x9 on elven grass (the mallorn's giant), 5x5 and
 * 4x4 squares (redwood, dragonblood), 3x3 (party trees), the mallorn's plus of five, 2x2, then a lone sapling with
 * an optional chance for a large tree. The saplings of the arrangement become the trunk: LOTR's tree shapes are set
 * with their trunk on the square; vanilla-shaped stand-ins grow from its middle.
 */
public class LotrSaplingBlock extends BushBlock implements BonemealableBlock {
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;
    public static final MapCodec<LotrSaplingBlock> CODEC = simpleCodec(LotrSaplingBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);

    public LotrSaplingBlock(Properties properties) {
        super(properties.randomTicks());
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getMaxLocalRawBrightness(pos.above()) >= 9 && random.nextInt(7) == 0) {
            advance(level, pos, state, random);
        }
    }

    private void advance(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), Block.UPDATE_NONE);
        } else {
            grow(level, pos, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return random.nextFloat() < 0.45f;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        advance(level, pos, state, random);
    }

    /** Grows the first rule that fits; true if a tree grew. */
    public boolean grow(ServerLevel level, BlockPos pos, RandomSource random) {
        Optional<SaplingRules> rules = SaplingRules.of(BuiltInRegistries.BLOCK.getKey(this));
        if (rules.isEmpty()) {
            return false;
        }
        for (SaplingRules.Rule rule : rules.get().rules()) {
            List<BlockPos> shape;
            BlockPos corner;
            int size;
            if (rule.square() > 1) {
                corner = findSquare(level, pos, rule.square(), rule);
                if (corner == null) {
                    continue;
                }
                size = rule.square();
                shape = new ArrayList<>();
                for (int dx = 0; dx < size; dx++) {
                    for (int dz = 0; dz < size; dz++) {
                        shape.add(corner.offset(dx, 0, dz));
                    }
                }
            } else if (rule.cross()) {
                BlockPos centre = findCross(level, pos);
                if (centre == null) {
                    continue;
                }
                shape = List.of(centre, centre.north(), centre.south(), centre.east(), centre.west());
                corner = centre;
                size = 1;
            } else {
                if (rule.chance() > 1 && random.nextInt(rule.chance()) != 0) {
                    continue;
                }
                shape = List.of(pos);
                corner = pos;
                size = 1;
            }
            if (place(level, rule.tree(), corner, size, shape, random)) {
                return true;
            }
            // a tree that wouldn't fit leaves the saplings as they were and the next rule may still grow
        }
        return false;
    }

    private boolean place(ServerLevel level, net.minecraft.resources.ResourceLocation tree, BlockPos corner, int size,
            List<BlockPos> shape, RandomSource random) {
        Optional<Holder.Reference<ConfiguredFeature<?, ?>>> feature = level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolder(ResourceKey.create(Registries.CONFIGURED_FEATURE, tree));
        if (feature.isEmpty()) {
            return false;
        }
        List<BlockState> before = new ArrayList<>();
        for (BlockPos p : shape) {
            before.add(level.getBlockState(p));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
        }
        ConfiguredFeature<?, ?> f = feature.get().value();
        boolean grown;
        if (f.config() instanceof TemplateTreeFeature.Config config) {
            int pick = random.nextInt(Math.max(1, config.templates().size()));
            int width = pick < config.trunkWidth().size() ? Math.max(1, config.trunkWidth().get(pick)) : 1;
            int in = Math.max(0, (size - width) / 2);
            grown = TemplateTreeFeature.grow(level, random, corner.offset(in, 0, in), config, pick, true,
                    size > 1 && size % 2 == 1 ? corner.offset(size / 2, 0, size / 2) : null);
        } else {
            int in = (size - 1) / 2;
            grown = f.place(level, level.getChunkSource().getGenerator(), random, corner.offset(in, 0, in));
            if (grown) {
                // vanilla-shaped stand-in: no exact shape to record, so a generous box round the trunk
                BlockPos trunk = corner.offset(in, 0, in);
                com.porcupinestudios.lotrport.tree.Trees.record(level, new com.porcupinestudios.lotrport.tree
                        .TreeRecord(tree, trunk, 1, new net.minecraft.world.level.levelgen.structure.BoundingBox(
                                trunk.getX() - 8, trunk.getY() - 2, trunk.getZ() - 8, trunk.getX() + 8,
                                trunk.getY() + 32, trunk.getZ() + 8), true));
            }
        }
        if (!grown) {
            for (int i = 0; i < shape.size(); i++) {
                level.setBlock(shape.get(i), before.get(i), Block.UPDATE_NONE);
            }
        }
        return grown;
    }

    private boolean same(BlockGetter level, BlockPos p, SaplingRules.Rule rule) {
        if (!level.getBlockState(p).is(this)) {
            return false;
        }
        return rule == null || rule.soil().map(s -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(p.below())
                .getBlock()).equals(s)).orElse(true);
    }

    /** The lowest corner of a size x size square of this sapling containing pos, or null. */
    private BlockPos findSquare(BlockGetter level, BlockPos pos, int size, SaplingRules.Rule rule) {
        for (int ox = 0; ox < size; ox++) {
            for (int oz = 0; oz < size; oz++) {
                BlockPos corner = pos.offset(-ox, 0, -oz);
                boolean all = true;
                for (int dx = 0; dx < size && all; dx++) {
                    for (int dz = 0; dz < size && all; dz++) {
                        all = same(level, corner.offset(dx, 0, dz), rule);
                    }
                }
                if (all) {
                    return corner;
                }
            }
        }
        return null;
    }

    /** The centre of a plus of five of this sapling that pos is part of, or null. */
    private BlockPos findCross(BlockGetter level, BlockPos pos) {
        for (BlockPos c : List.of(pos, pos.north(), pos.south(), pos.east(), pos.west())) {
            if (same(level, c, null) && same(level, c.north(), null) && same(level, c.south(), null)
                    && same(level, c.east(), null) && same(level, c.west(), null)) {
                return c;
            }
        }
        return null;
    }
}
