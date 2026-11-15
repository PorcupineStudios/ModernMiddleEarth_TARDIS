package com.porcupinestudios.lotrport.machine;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.content.FacingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The kebab stand (LOTRBlockKebabStand): raw meat in hand puts a piece on the spit; once all of it is cooked an empty
 * hand takes a kebab off. It cooks on fuel from the container under it (a furnace or chest of coal).
 */
public class KebabStandBlock extends FacingBlock implements EntityBlock {
    public KebabStandBlock(Properties properties, float[] box) {
        super(properties, box);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new KebabStandBlock(p, null));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KebabStandBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, be) -> {
            if (be instanceof KebabStandBlockEntity k) {
                k.serverTick();
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof KebabStandBlockEntity be && !be.kebabFor(stack).isEmpty()) {
            if (be.cooked()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                if (!be.addMeat(stack)) {
                    return ItemInteractionResult.FAIL;
                }
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof KebabStandBlockEntity be) || !be.cooked()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack kebab = be.take();
            if (!player.getInventory().add(kebab)) {
                player.drop(kebab, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof KebabStandBlockEntity be) {
            Containers.dropContents(level, pos, be.contents());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Smoke rising off the meat while it cooks. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof KebabStandBlockEntity be && be.cooking() && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + random.nextGaussian() * 0.1,
                    pos.getY() + 0.3 + random.nextDouble() * 0.6, pos.getZ() + 0.5 + random.nextGaussian() * 0.1,
                    0, 0.02, 0);
        }
    }
}
