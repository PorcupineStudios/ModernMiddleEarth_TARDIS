package com.porcupinestudios.lotrport.content;

import net.minecraft.world.level.block.CakeBlock;

/** LOTR's placeable foods (pies, cakes, crumble): eaten a slice at a time like a cake (LOTRBlockPlaceableFood). */
public class PieBlock extends CakeBlock {
    // CakeBlock.codec() is typed MapCodec<CakeBlock>, so it can't be overridden with a PieBlock codec; vanilla's is unused at runtime.
    public PieBlock(Properties properties) {
        super(properties);
    }
}
