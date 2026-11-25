package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Flowers, grass, saplings and crops from the converter. Like vanilla's bushes they grow on dirt and farmland, and
 * also on the blocks in their own tag {@code lotr_port:grows_on/<plant>} (data; the converter fills it with the ground
 * LOTR's plant census found them on: Harad flowers on sand, arid grass...).
 */
public class PlantBlock extends BushBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);
    public static final MapCodec<PlantBlock> CODEC = simpleCodec(PlantBlock::new);

    public PlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    private TagKey<Block> growsOn;

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        if (super.mayPlaceOn(state, level, pos)) {
            return true;
        }
        if (growsOn == null) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(this);
            growsOn = TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "grows_on/" + id.getPath()));
        }
        return state.is(growsOn);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        net.minecraft.world.phys.Vec3 offset = state.getOffset(level, pos); // zero unless the entry has an offset
        return SHAPE.move(offset.x, offset.y, offset.z);
    }
}
