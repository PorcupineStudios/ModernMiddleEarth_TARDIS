package com.porcupinestudios.lotrport.drink;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.content.ShapedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
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
 * A mug, goblet, cup, horn or glass set down on a table (LOTRBlockMug): holds the drink (or empty vessel) it was set
 * down as, gives it back on use or when broken, and needs a floor under it. Placed by {@link Vessels}.
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof VesselBlockEntity be) {
            ItemStack held = be.take();
            level.removeBlock(pos, false);
            if (!held.isEmpty() && !player.getInventory().add(held)) {
                player.drop(held, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
