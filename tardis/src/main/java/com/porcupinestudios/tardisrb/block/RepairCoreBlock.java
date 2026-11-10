package com.porcupinestudios.tardisrb.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The repair core (Aaron 2026-11-10): the console's column carried on down into the basement as a thick hexagonal
 * core, the panel that will show and handle repairs and upgrades. It fills the 3 x 3 x 2 cells under the console:
 * this block in the middle of the bottom layer holds the block entity its renderer ({@code client/RepairCoreRenderer})
 * draws from, and {@link Casing} fills the rest. Clicking any of it opens the repair and upgrade tree, like the Repair
 * Station. Its blank panel slots come from {@code assets/tardis_rb/core/layout.json} (docs/repair-loop.md).
 */
public final class RepairCoreBlock extends Block implements EntityBlock {
    public RepairCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RepairCoreBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return RepairStationBlock.open(level, pos, player);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /** The rest of the core's cells: drawn by the core's renderer, solid and clickable here. */
    public static final class Casing extends Block {
        public Casing(Properties properties) {
            super(properties);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.INVISIBLE;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                BlockHitResult hit) {
            return RepairStationBlock.open(level, pos, player);
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
            return true;
        }

        @Override
        protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
            return 1.0F;
        }
    }
}
