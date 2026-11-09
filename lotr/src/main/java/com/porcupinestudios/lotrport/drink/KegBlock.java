package com.porcupinestudios.lotrport.drink;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.content.FacingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * LOTR's barrel, as a keg: right-click with an empty vessel pours a serving of a finished drink into it, otherwise opens
 * the keg's screen (brewing). Breaking it drops the ingredients and buckets; the drink is lost, as LOTR's.
 */
public class KegBlock extends FacingBlock implements EntityBlock {
    public KegBlock(Properties properties, float[] box) {
        super(properties, box);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new KegBlock(p, null));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KegBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide || type != Drinks.KEG.get() ? null
                : (BlockEntityTicker<T>) (BlockEntityTicker<KegBlockEntity>) (l, p, s, keg) -> keg.tick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!Drinks.isVessel(stack.getItem())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS; // the server pours, or opens the screen if there's nothing to pour
        }
        ItemStack drink = level.getBlockEntity(pos) instanceof KegBlockEntity keg ? keg.pour() : ItemStack.EMPTY;
        if (drink.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, Drinks.inVessel(drink, stack.getItem())));
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1f);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof KegBlockEntity keg) {
            player.openMenu(keg);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof KegBlockEntity keg) {
            Containers.dropContents(level, pos, keg.contents());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
