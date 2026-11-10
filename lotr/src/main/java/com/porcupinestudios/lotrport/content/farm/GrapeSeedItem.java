package com.porcupinestudios.lotrport.content.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Grape seeds (LOTRItemGrapeSeeds): used on a grapevine post with farmland under it, they plant a vine there. */
public class GrapeSeedItem extends Item {
    private final Block vine;

    public GrapeSeedItem(Block vine, Properties properties) {
        super(properties);
        this.vine = vine;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(vine instanceof GrapevineBlock v)) {
            return InteractionResult.PASS;
        }
        Block post = Farming.block(v.postId());
        if (!level.getBlockState(pos).is(post) || GrapevineBlock.farmland(level, pos, post) == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, vine.defaultBlockState(), 3);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
            context.getItemInHand().consume(1, context.getPlayer());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
