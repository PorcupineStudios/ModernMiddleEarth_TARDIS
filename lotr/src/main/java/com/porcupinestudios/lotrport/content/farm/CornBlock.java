package com.porcupinestudios.lotrport.content.farm;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LOTR's corn stalk (LOTRBlockCorn): planted on soil or farmland, it ages to 7 and then grows a new stalk on top, up
 * to {@code corn_height} high (sugar-cane style). Stalks above the bottom one grow a cob (chance growth /
 * {@code corn_cob_divisor}); right-click picks it. Breaking a stalk breaks the ones above.
 */
public class CornBlock extends Block {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    public static final BooleanProperty CORN = BooleanProperty.create("corn");
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    private final String cob;

    public CornBlock(Properties properties, String cob) {
        super(properties);
        this.cob = cob;
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(CORN, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new CornBlock(p, cob));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, CORN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) || below.is(BlockTags.DIRT) || below.getBlock() instanceof FarmBlock
                || below.is(Blocks.FARMLAND);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level,
            BlockPos pos, BlockPos otherPos) {
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    private int height(LevelReader level, BlockPos pos) {
        int h = 1;
        while (level.getBlockState(pos.below(h)).is(this)) {
            h++;
        }
        return h;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!Farming.lit(level, pos)) {
            return;
        }
        Farming.Settings s = Farming.settings();
        BlockPos bottom = pos.below(height(level, pos) - 1);
        float growth = Farming.growth(level, bottom);
        int h = height(level, pos);
        if (level.isEmptyBlock(pos.above()) && h < s.cornHeight() && random.nextFloat() < growth / s.cornDivisor()) {
            int age = state.getValue(AGE);
            if (age >= 7) {
                level.setBlock(pos.above(), defaultBlockState(), 3);
            } else {
                level.setBlock(pos, state.setValue(AGE, age + 1), 4);
            }
        }
        if (!state.getValue(CORN) && h > 1 && random.nextFloat() < growth / s.cornCobDivisor()) {
            level.setBlock(pos, level.getBlockState(pos).setValue(CORN, true), 3);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!state.getValue(CORN)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Farming.drop(level, pos, Farming.item(cob), 1);
            level.setBlock(pos, state.setValue(CORN, false), 3);
            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
