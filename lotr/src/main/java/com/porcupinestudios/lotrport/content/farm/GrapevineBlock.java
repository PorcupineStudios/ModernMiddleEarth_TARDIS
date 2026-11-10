package com.porcupinestudios.lotrport.content.farm;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A red or white grapevine on its post (LOTRBlockGrapevine). Grape seeds plant it on a grapevine post with farmland at
 * most {@code grape_height} blocks below (through posts and vines). It ages to 7 (chance growth of that farmland /
 * {@code grape_divisor}); ripe grapes are picked by right-click ({@code grape_min}-{@code grape_max}), which leaves
 * the bare post as LOTR did. Breaking a vine also leaves the post.
 */
public class GrapevineBlock extends Block implements BonemealableBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    private static final VoxelShape POST = Block.box(6, 0, 6, 10, 16, 10);
    private final String grapes;
    private final String post;

    public GrapevineBlock(Properties properties, String grapes, String post) {
        super(properties);
        this.grapes = grapes;
        this.post = post;
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    public String postId() {
        return post;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new GrapevineBlock(p, grapes, post));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return POST;
    }

    /** The farmland a vine at {@code pos} grows from, or null: straight down through posts and vines. */
    public static BlockPos farmland(LevelReader level, BlockPos pos, Block postBlock) {
        int max = Farming.settings().grapeHeight();
        for (int i = 1; i <= max; i++) {
            BlockState s = level.getBlockState(pos.below(i));
            if (s.getBlock() instanceof FarmBlock) {
                return pos.below(i);
            }
            if (!s.is(postBlock) && !(s.getBlock() instanceof GrapevineBlock)) {
                return null;
            }
        }
        return null;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 7;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos soil = farmland(level, pos, Farming.block(post));
        if (soil != null && Farming.lit(level, pos)
                && random.nextFloat() < Farming.growth(level, soil.above()) / Farming.settings().grapeDivisor()) {
            level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (state.getValue(AGE) < 7) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Farming.Settings s = Farming.settings();
            Farming.drop(level, pos, Farming.item(grapes),
                    s.grapeMin() + level.random.nextInt(Math.max(1, s.grapeMax() - s.grapeMin() + 1)));
            level.setBlock(pos, Farming.block(post).defaultBlockState(), 3);
            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Breaking a vine leaves its post (LOTR's removedByPlayer). */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
            boolean willHarvest, FluidState fluid) {
        playerWillDestroy(level, pos, state, player);
        return level.setBlock(pos, Farming.block(post).defaultBlockState(), level.isClientSide ? 11 : 3);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) < 7;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(AGE, Math.min(7, state.getValue(AGE) + 1 + random.nextInt(2))), 2);
    }
}
