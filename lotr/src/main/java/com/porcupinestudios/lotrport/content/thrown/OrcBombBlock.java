package com.porcupinestudios.lotrport.content.thrown;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An orc bomb (LOTRBlockOrcBomb): lit by redstone when placed or when a neighbour powers it, or by another
 * explosion; it then burns down as an {@link OrcBomb}. Each of LOTR's six (strength 0-2, plain or fire) is its own
 * block, with {@code bomb.strength} and {@code bomb.fire} from the generator.
 */
public class OrcBombBlock extends Block {
    private final int strength;
    private final boolean fire;

    public OrcBombBlock(Properties properties, int strength, boolean fire) {
        super(properties);
        this.strength = strength;
        this.fire = fire;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new OrcBombBlock(p, strength, fire));
    }

    public int strength() {
        return strength;
    }

    public boolean fire() {
        return fire;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(state.getBlock()) && level.hasNeighborSignal(pos)) {
            light(level, pos, state);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from,
            boolean moved) {
        if (level.hasNeighborSignal(pos)) {
            light(level, pos, state);
        }
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide) {
            OrcBomb bomb = new OrcBomb(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, defaultBlockState(),
                    explosion.getIndirectSourceEntity());
            bomb.fuseFromExplosion();
            level.addFreshEntity(bomb);
        }
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    private void light(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            return;
        }
        level.removeBlock(pos, false);
        level.addFreshEntity(new OrcBomb(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, state, null));
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.TNT_PRIMED,
                net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
    }
}
