package com.porcupinestudios.tardisrb.block;

import net.minecraft.world.level.block.Block;

/**
 * Debris of a wrecked TARDIS, found rarely in the world (worldgen data {@code tardis_rb:salvage_wreck}). Breaking it
 * drops parts from its loot table; random wrecks are salvage, never a second TARDIS.
 */
public final class SalvageBlock extends Block {
    public SalvageBlock(Properties properties) {
        super(properties);
    }
}
