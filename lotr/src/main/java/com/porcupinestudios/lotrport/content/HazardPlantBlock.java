package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A plant with a {@link Hazards} effect: stabbing thorn, thistles, nettles, Morgul flowers. */
public class HazardPlantBlock extends PlantBlock {
    private final Hazards.Hazard hazard;

    public HazardPlantBlock(Properties properties, Hazards.Hazard hazard) {
        super(properties);
        this.hazard = hazard;
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return simpleCodec(p -> new HazardPlantBlock(p, hazard));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        Hazards.inside(hazard, state, level, pos, entity);
    }
}
