package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A shaped block with a {@link Hazards} effect: quagmire (walk in, no collision), waste (sink a little), coral. */
public class HazardBlock extends ShapedBlock {
    private static final VoxelShape WASTE = Block.box(0, 0, 0, 16, 14, 16);
    private final Hazards.Hazard hazard;

    public HazardBlock(Properties properties, float[] box, Hazards.Hazard hazard) {
        super(properties, box);
        this.hazard = hazard;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new HazardBlock(p, null, hazard));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (hazard) {
            case QUAGMIRE -> Shapes.empty();
            case WASTE -> WASTE;
            default -> super.getCollisionShape(state, level, pos, ctx);
        };
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        Hazards.inside(hazard, state, level, pos, entity);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        Hazards.stepOn(hazard, level, entity);
        super.stepOn(level, pos, state, entity);
    }
}
