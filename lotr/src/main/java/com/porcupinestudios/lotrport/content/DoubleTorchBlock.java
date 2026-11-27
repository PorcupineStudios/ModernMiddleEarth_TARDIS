package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LOTR's orc and Taurëdain torches (LOTRBlockDoubleTorch): a thin post two blocks tall, standing on any block with a
 * solid centre, lit and burning at the top half only. Vanilla's double plant does the halves (placing the top with
 * the bottom, breaking both, one drop from the lower half's loot table); this changes what it may stand on.
 */
public class DoubleTorchBlock extends DoublePlantBlock {
    public static final MapCodec<DoubleTorchBlock> CODEC = simpleCodec(DoubleTorchBlock::new);
    private static final VoxelShape LOWER = Block.box(6.4, 0, 6.4, 9.6, 16, 9.6);
    private static final VoxelShape UPPER = Block.box(6.4, 0, 6.4, 9.6, 8.6, 9.6);

    public DoubleTorchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends DoublePlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP, net.minecraft.world.level.block.SupportType.CENTER);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            double x = pos.getX() + 0.5, y = pos.getY() + 0.6, z = pos.getZ() + 0.5;
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
        }
    }
}
