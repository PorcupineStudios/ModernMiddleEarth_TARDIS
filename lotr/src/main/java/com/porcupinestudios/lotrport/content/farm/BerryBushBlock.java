package com.porcupinestudios.lotrport.content.farm;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A berry bush (LOTRBlockBerryBush). LOTR kept "has berries" in the metadata; here the ripe and the bare bush are two
 * blocks. A bare bush grows berries on random ticks (chance growth / {@code berry_divisor}, in light); right-clicking a
 * ripe one picks {@code berry_min}-{@code berry_max} berries and leaves it bare. Bone meal ripens it.
 */
public class BerryBushBlock extends Block implements BonemealableBlock {
    private final String ripe;
    private final String bare;
    private final String berries;
    private final boolean hasBerries;

    public BerryBushBlock(Properties properties, String ripe, String bare, String berries, boolean hasBerries) {
        super(properties);
        this.ripe = ripe;
        this.bare = bare;
        this.berries = berries;
        this.hasBerries = hasBerries;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new BerryBushBlock(p, ripe, bare, berries, hasBerries));
    }

    public boolean hasBerries() {
        return hasBerries;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !hasBerries;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Farming.lit(level, pos)
                && random.nextFloat() < Farming.growth(level, pos) / Farming.settings().berryDivisor()) {
            ripen(level, pos);
        }
    }

    private void ripen(Level level, BlockPos pos) {
        level.setBlock(pos, Farming.block("lotr_port:" + ripe).defaultBlockState(), 3);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!hasBerries) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Farming.Settings s = Farming.settings();
            Farming.drop(level, pos, Farming.item(berries), s.berryMin() + level.random.nextInt(
                    Math.max(1, s.berryMax() - s.berryMin() + 1)));
            level.setBlock(pos, Farming.block("lotr_port:" + bare).defaultBlockState(), 3);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1f,
                    0.8f + level.random.nextFloat() * 0.4f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return !hasBerries;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        ripen(level, pos);
    }
}
