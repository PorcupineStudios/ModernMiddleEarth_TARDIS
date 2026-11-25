package com.porcupinestudios.lotrport.ring;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * A ring worn in a Curios ring slot (item tag {@code curios:ring}); right-click puts it on. What it does is the
 * {@code ring_power} data naming its item ({@link RingPowers}), not this class.
 */
public class RingItem extends Item implements ICurioItem {
    public RingItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(getDescriptionId() + ".lore")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }
}
