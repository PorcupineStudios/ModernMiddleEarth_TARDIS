package com.porcupinestudios.lotrport.compat.jade;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrDrinkItem;
import com.porcupinestudios.lotrport.drink.VesselBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

/**
 * A set-down mug, goblet, horn, glass or bottle: named as the drink it holds ("Ale"), with the drink's own tooltip lines
 * under it (strength, alcohol, and "Poisoned!" for whoever may see that, as in the hand: {@code Drinks.poisonTooltip}),
 * or "Empty". The held stack reaches the client with the block entity's update packet.
 */
final class JadeVessel implements IBlockComponentProvider {
    static final JadeVessel INSTANCE = new JadeVessel();
    private static final ResourceLocation UID = LotrPort.id("vessel");

    private JadeVessel() {}

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof VesselBlockEntity be)) {
            return;
        }
        ItemStack held = be.held();
        if (!(held.getItem() instanceof LotrDrinkItem)) {
            tooltip.add(Component.translatableWithFallback("jade.lotr_port.vessel.empty", "Empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.replace(JadeIds.CORE_OBJECT_NAME, held.getHoverName());
        List<Component> lines = held.getTooltipLines(Item.TooltipContext.of(accessor.getLevel()), accessor.getPlayer(),
                TooltipFlag.Default.NORMAL);
        for (int i = 1; i < lines.size(); i++) { // line 0 is the name
            tooltip.add(lines.get(i));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
