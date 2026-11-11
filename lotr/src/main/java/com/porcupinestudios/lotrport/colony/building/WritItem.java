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

/** RingRealms' writ: used on a door, the room behind it becomes a colony building. */
public class WritItem extends Item {
    public WritItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ColonyBuildings.doorway(ctx.getLevel(), ctx.getClickedPos()).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (ctx.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(ctx.getPlayer() instanceof ServerPlayer player) || !(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = ctx.getItemInHand();
        Optional<String> name = stack.has(DataComponents.CUSTOM_NAME)
                ? Optional.of(stack.getHoverName().getString()) : Optional.empty();
        ColonyBuildings.Result r = ColonyBuildings.add(player, level, ctx.getClickedPos(),
                Optional.ofNullable(stack.get(LotrComponents.BUILDING_TYPE.get())), name);
        player.sendSystemMessage(r.message());
        if (r.building().isPresent() && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return r.building().isPresent() ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** Doors open on a right-click before the item is asked: with a writ in hand the door waits. */
    public static void onRightClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (e.getItemStack().getItem() instanceof WritItem
                && ColonyBuildings.doorway(e.getLevel(), e.getPos()).isPresent()) {
            e.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var type = stack.get(LotrComponents.BUILDING_TYPE.get());
        lines.add((type == null ? Component.translatableWithFallback("item.lotr_port.building_writ.any",
                "Use on a door: the room behind it becomes the building that fits best")
                : Component.translatableWithFallback("item.lotr_port.building_writ.type", "Makes a %s",
                        type.getPath().replace('_', ' '))).withStyle(ChatFormatting.GRAY));
    }
}
