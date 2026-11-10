package com.porcupinestudios.lotrport.machine;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.LotrConfig;
import com.porcupinestudios.lotrport.content.FacingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A plate, weapon rack or armour stand (LOTRBlockPlate / WeaponRack / ArmorStand).
 * <ul>
 * <li>Plate: right-click with food puts it on (more of the same stacks up); with an empty hand you eat one.</li>
 * <li>Weapon rack: right-click with a weapon (melee, bow, crossbow, hoe, fishing rod) hangs it; empty hand takes it.</li>
 * <li>Armour stand: right-click opens its four armour slots beside your own.</li>
 * </ul>
 * The rack and stand are drawn by their block entity renderer, on LOTR's own models.
 */
public class DisplayBlock extends FacingBlock implements EntityBlock {
    private final String kind;

    public DisplayBlock(Properties properties, float[] box, String kind) {
        super(properties, box);
        this.kind = kind;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new DisplayBlock(p, null, kind));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return kind.equals("plate") ? RenderShape.MODEL : RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** LOTRTileEntityPlate.isValidFoodItem: food that leaves nothing behind (no bowls or bottles). */
    public static boolean plateFood(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null && food.usingConvertsTo().isEmpty() && !stack.hasCraftingRemainingItem();
    }

    /** LOTRTileEntityWeaponRack.canAcceptItem. */
    public static boolean rackWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof HoeItem || stack.getItem() instanceof FishingRodItem
                || stack.getItem() instanceof net.minecraft.world.item.AxeItem;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DisplayBlockEntity be) || kind.equals("armor_stand")) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemStack held = be.held();
        if (kind.equals("plate") && plateFood(stack)) {
            if (held.isEmpty() || ItemStack.isSameItemSameComponents(held, stack)
                    && held.getCount() < held.getMaxStackSize()) {
                if (!level.isClientSide) {
                    be.hold(held.isEmpty() ? stack.copyWithCount(1) : held.copyWithCount(held.getCount() + 1));
                    stack.consume(1, player);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        } else if (kind.equals("weapon_rack") && held.isEmpty() && rackWeapon(stack)) {
            if (!level.isClientSide) {
                be.hold(stack.copyWithCount(1));
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1f, 1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DisplayBlockEntity be)) {
            return InteractionResult.PASS;
        }
        switch (kind) {
            case "armor_stand" -> {
                if (!level.isClientSide) {
                    player.openMenu(be);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            case "weapon_rack" -> {
                if (be.held().isEmpty()) {
                    return InteractionResult.PASS;
                }
                if (!level.isClientSide) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, be.held().copy());
                    be.hold(ItemStack.EMPTY);
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 1f);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            default -> {
                ItemStack food = be.held();
                boolean feast = LotrConfig.SPEC.isLoaded() && LotrConfig.FEAST_MODE.get();
                if (food.isEmpty() || !player.canEat(feast)) {
                    return InteractionResult.PASS;
                }
                if (!level.isClientSide) {
                    ItemStack one = food.copyWithCount(1);
                    one.getItem().finishUsingItem(one, level, player);
                    level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.5f,
                            level.random.nextFloat() * 0.1f + 0.9f);
                    be.hold(food.copyWithCount(food.getCount() - 1));
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DisplayBlockEntity be) {
            Containers.dropContents(level, pos, be);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
