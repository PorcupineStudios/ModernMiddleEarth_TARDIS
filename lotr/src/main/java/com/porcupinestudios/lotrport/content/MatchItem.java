package com.porcupinestudios.lotrport.content;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** LOTR's sulfur match (LOTRItemMatch): lights fire, campfires and candles like flint and steel, one match a use. */
public class MatchItem extends Item {
    public MatchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BlockPos target = null;
        BlockState lit = null;
        if (CampfireBlock.canLight(state) || state.hasProperty(BlockStateProperties.LIT)
                && !state.getValue(BlockStateProperties.LIT) && state.is(net.minecraft.tags.BlockTags.CANDLES)) {
            target = pos;
            lit = state.setValue(BlockStateProperties.LIT, true);
        } else {
            BlockPos fire = pos.relative(ctx.getClickedFace());
            if (BaseFireBlock.canBePlacedAt(level, fire, ctx.getHorizontalDirection())) {
                target = fire;
                lit = BaseFireBlock.getState(level, fire);
            }
        }
        if (target == null) {
            return InteractionResult.FAIL;
        }
        level.playSound(ctx.getPlayer(), target, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1f,
                level.getRandom().nextFloat() * 0.4f + 0.8f);
        if (!level.isClientSide) {
            level.setBlock(target, lit, 11);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
