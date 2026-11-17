package com.porcupinestudios.lotrport.modifier;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * An ancient sword, dagger or armour piece, put together from ancient parts (LOTR's "Archaeologist"): used, it is
 * revealed as one of the old weapons or armour in its {@code ancient_item/<item>.json}, with skilful modifiers.
 */
public class AncientItem extends Item {
    public AncientItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var def = Modifiers.ancient(this);
        if (def.isEmpty() || def.get().results().isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        var results = def.get().results();
        ItemStack out = new ItemStack(BuiltInRegistries.ITEM.get(results.get(level.random.nextInt(results.size()))));
        if (out.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        Modifiers.applyRandom(out, level.random, def.get().skilful(), level.registryAccess());
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS,
                0.5f, 1.2f);
        stack.shrink(1);
        if (stack.isEmpty()) {
            return InteractionResultHolder.success(out);
        }
        if (!player.getInventory().add(out)) {
            player.drop(out, false);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lotr_port.ancient.desc").withStyle(ChatFormatting.GRAY));
    }
}
