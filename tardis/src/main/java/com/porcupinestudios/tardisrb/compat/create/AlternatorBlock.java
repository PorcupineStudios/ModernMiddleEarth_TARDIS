package com.porcupinestudios.tardisrb.compat.create;

import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Artron Alternator (Aaron 2026-11-19, his drawing: a gear on top, arms reaching down round the police box, lightning
 * crackling over it): set it over the box (at most {@code alternator.reach} blocks above its top). Its gear is a
 * Create small cogwheel on the vertical axis ({@link ICogWheel}, Aaron 2026-11-19: "turned by adjacent gears
 * integrating with its gear body"): cogwheels beside it mesh with it and turn it, as large cogwheels do diagonally;
 * a shaft from above also drives it. While it turns it fills the TARDIS's artron (its charge, up to the cap).
 */
public final class AlternatorBlock extends KineticBlock implements IBE<AlternatorBlockEntity>, ICogWheel {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 11, 16);

    public AlternatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.UP;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<AlternatorBlockEntity> getBlockEntityClass() {
        return AlternatorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AlternatorBlockEntity> getBlockEntityType() {
        return TardisBlocks.ALTERNATOR_BE.get();
    }
}
