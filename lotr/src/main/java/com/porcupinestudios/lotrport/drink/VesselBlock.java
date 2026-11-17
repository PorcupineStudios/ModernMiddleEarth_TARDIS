package com.porcupinestudios.lotrport.drink;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.content.ShapedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.porcupinestudios.lotrport.content.LotrDrinkItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A mug, goblet, cup, horn, glass or bottle set down on a table (LOTRBlockMug): holds the drink (or empty vessel) it
 * was set down as and needs a floor under it. Placed by {@link Vessels}. As LOTR's: using a full one drinks it where it
 * stands (it stays, empty); using an empty one with a drink in that kind of vessel pours the drink in; using an empty
 * one otherwise does nothing. Breaking it gives back what it holds (structure vessels hold nothing: their loot table's
 * empty vessel).
 */
public class VesselBlock extends ShapedBlock implements EntityBlock {
    public VesselBlock(Properties properties, float[] box) {
        super(properties, box);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new VesselBlock(p, null));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VesselBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof VesselBlockEntity be) || full(be)
                || !(stack.getItem() instanceof LotrDrinkItem) || Drinks.vessel(stack) != Vessels.itemFor(this)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            be.set(stack);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Drinks.vessel(stack))));
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof VesselBlockEntity be) || !full(be)) {
            return InteractionResult.PASS; // an empty vessel: nothing to drink
        }
        if (!level.isClientSide) {
            ItemStack drink = be.held();
            ((LotrDrinkItem) drink.getItem()).drink(drink, level, player);
            be.set(new ItemStack(Drinks.vessel(drink)));
            level.playSound(null, pos, SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Holds a drink (not just its empty vessel). */
    private static boolean full(VesselBlockEntity be) {
        return be.held().getItem() instanceof LotrDrinkItem;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof VesselBlockEntity v && !v.held().isEmpty()) {
            return List.of(v.held().copy());
        }
        return super.getDrops(state, params);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }
}
