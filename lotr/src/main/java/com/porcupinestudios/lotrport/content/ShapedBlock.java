package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A plain block with a fixed box shape, used for converted blocks that have no closer vanilla kind yet. Their own
 * behaviour (tables, forges, beds...) is ported system by system in later phases.
 */
public class ShapedBlock extends Block implements net.minecraft.world.level.block.SimpleWaterloggedBlock {
    private final VoxelShape shape;

    public ShapedBlock(Properties properties, float[] box) {
        super(properties);
        registerDefaultState(Waterlog.dry(defaultBlockState()));
        this.shape = box == null ? Shapes.block()
                : Block.box(box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        Waterlog.define(builder);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return Waterlog.placed(defaultBlockState(), context);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new ShapedBlock(p, null));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    // ---- waterlogging (content/Waterlog): only blocks converter/waterlog.py marks have the property

    @Override
    public net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        return Waterlog.fluid(state, super.getFluidState(state));
    }

    @Override
    public boolean canPlaceLiquid(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state,
            net.minecraft.world.level.material.Fluid fluid) {
        return Waterlog.has(state) && net.minecraft.world.level.block.SimpleWaterloggedBlock.super.canPlaceLiquid(
                player, level, pos, state, fluid);
    }

    @Override
    public boolean placeLiquid(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state,
            net.minecraft.world.level.material.FluidState fluid) {
        return Waterlog.has(state)
                && net.minecraft.world.level.block.SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluid);
    }

    @Override
    public net.minecraft.world.item.ItemStack pickupBlock(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
        return Waterlog.has(state) ? net.minecraft.world.level.block.SimpleWaterloggedBlock.super.pickupBlock(
                player, level, pos, state) : net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction dir, BlockState neighbor,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Waterlog.tick(state, level, pos);
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }
}
