package com.porcupinestudios.lotrport.modifier;

import com.porcupinestudios.lotrport.content.LotrComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * LOTR's Smith's Scroll (LOTRItemModifierTemplate): holds one modifier ({@link LotrComponents#MODIFIER}, named
 * "Keen Smith's Scroll"). On an anvil it puts its modifier on a piece of gear; two of one modifier make the next one up
 * ({@link Modifiers}). A scroll that came without one (an NPC's drop) gets a random one the first time it lands in the
 * world or sits in an inventory.
 */
public class SmithScrollItem extends Item {
    public SmithScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(LotrComponents.MODIFIER.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        // a blank scroll: "%s Smith's Scroll" with nothing in it
        return Component.translatable(getDescriptionId(), Component.translatable("item.lotr_port.s_smiths_scroll.blank"));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide) {
            Modifiers.fillScroll(stack, level.random, level.registryAccess());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lotr_port.s_smiths_scroll.desc").withStyle(ChatFormatting.GRAY));
    }
}
