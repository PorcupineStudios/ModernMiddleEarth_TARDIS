package com.porcupinestudios.lotrport.content.pouch;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * LOTR's pouches (LOTRItemPouch): a small, medium or large pouch holds 9, 18 or 27 stacks (no pouches inside).
 * Right-click opens it; it can be dyed and renamed in its own screen. Its stacks are the item's
 * {@code minecraft:container} component. While it's in your inventory, pickups that don't fit go into it.
 */
public class PouchItem extends Item {
    private final int slots;

    public PouchItem(Properties properties, int slots) {
        super(properties.stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
        this.slots = slots;
    }

    public int slots() {
        return slots;
    }

    public static NonNullList<ItemStack> contents(ItemStack pouch) {
        int size = pouch.getItem() instanceof PouchItem p ? p.slots : 0;
        NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
        pouch.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }

    public static void store(ItemStack pouch, List<ItemStack> items) {
        pouch.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            int slot = hand == InteractionHand.MAIN_HAND ? sp.getInventory().selected : Pouches.OFFHAND;
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new PouchMenu(id, inv, slot), stack.getHoverName()),
                    buf -> buf.writeVarInt(slot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long used = contents(stack).stream().filter(s -> !s.isEmpty()).count();
        tooltip.add(Component.translatable("item.lotr_port.pouch.slots", used, slots)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
