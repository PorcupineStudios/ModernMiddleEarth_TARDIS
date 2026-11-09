package com.porcupinestudios.lotrport.drink;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * An empty LOTR vessel (mug, goblet, horn, waterskin...): fills with water from a water source, as LOTR's, with a drink
 * from a keg (right-click it, {@link KegBlock}), or from any fluid handler (Create's spout) through
 * {@link DrinkFluidHandler}.
 */
public class VesselItem extends Item {
    public VesselItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK || !level.mayInteract(player, hit.getBlockPos())
                || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            return InteractionResultHolder.pass(stack);
        }
        ItemStack water = Drinks.fromFluid(new FluidStack(Fluids.WATER, 1));
        if (water.isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }
        level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL,
                SoundSource.NEUTRAL, 1f, 1f);
        return InteractionResultHolder.sidedSuccess(
                ItemUtils.createFilledResult(stack, player, Drinks.inVessel(water, this)), level.isClientSide);
    }
}
