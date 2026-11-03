package com.porcupinestudios.tardisrb.compat.create;

import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The winder: a shaft-through Create block that turns rotation into TARDIS charge. Place it within reach of the police
 * box (or anywhere inside the interior) and drive it with any Create power.
 */
public final class WinderBlock extends RotatedPillarKineticBlock implements IBE<WinderBlockEntity> {
    public WinderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Class<WinderBlockEntity> getBlockEntityClass() {
        return WinderBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WinderBlockEntity> getBlockEntityType() {
        return TardisBlocks.WINDER_BE.get();
    }
}
