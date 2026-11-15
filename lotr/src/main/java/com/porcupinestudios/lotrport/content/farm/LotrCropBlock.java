package com.porcupinestudios.lotrport.content.farm;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

/**
 * LOTR's leek, lettuce, turnip, yam, flax and pipe-weed crops (vanilla BlockCrops in LOTR): eight ages, planted from
 * their seed (the produce itself for the vegetables), bone meal, LOTR's stage textures.
 */
public class LotrCropBlock extends CropBlock {
    private final String seed;

    public LotrCropBlock(Properties properties, String seed) {
        super(properties);
        this.seed = seed;
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return simpleCodec(p -> new LotrCropBlock(p, seed));
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return Farming.item(seed);
    }
}
