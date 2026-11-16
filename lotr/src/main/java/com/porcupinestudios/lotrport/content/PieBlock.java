package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.CakeBlock;

/** LOTR's placeable foods (pies, cakes, crumble): eaten a slice at a time like a cake (LOTRBlockPlaceableFood). */
public class PieBlock extends CakeBlock {
    public static final MapCodec<PieBlock> CODEC = simpleCodec(PieBlock::new);

    public PieBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends CakeBlock> codec() {
        return CODEC;
    }
}
