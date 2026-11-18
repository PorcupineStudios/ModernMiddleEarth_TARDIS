package com.porcupinestudios.lotrport.colony.building;

import com.porcupinestudios.lotrport.content.LotrComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** RingRealms' writ, retired: Building Plaques register buildings (ColonyBuildings.plaque). */
public class WritItem extends Item {
    public WritItem(Properties properties) {
        super(properties);
    }

    /**
     * Retired (Aaron 2026-11-18): Building Plaques register buildings now. A writ left in an inventory only says so;
     * the item stays registered so old worlds keep their stacks.
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(RETIRED, true);
        }
        return InteractionResult.FAIL;
    }

    private static final Component RETIRED = Component.translatableWithFallback("item.lotr_port.building_writ.retired",
            "Writs are retired: hang a Building Plaque in the room, or by its door");

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(RETIRED.copy().withStyle(ChatFormatting.GRAY));
    }
}
