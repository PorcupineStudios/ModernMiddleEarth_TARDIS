package com.porcupinestudios.tardisrb.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The repair core's block entity: only there so the core gets a renderer. It holds nothing yet; when the panels start
 * showing state (installed upgrades, hull, oxygen), that state is synced here.
 */
public final class RepairCoreBlockEntity extends BlockEntity {
    public RepairCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TardisBlocks.REPAIR_CORE_BE.get(), pos, state);
    }
}
