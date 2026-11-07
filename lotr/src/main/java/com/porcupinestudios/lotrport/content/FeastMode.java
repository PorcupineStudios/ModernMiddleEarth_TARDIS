package com.porcupinestudios.lotrport.content;

import com.porcupinestudios.lotrport.LotrConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * LOTR's feast mode ({@link LotrConfig#FEAST_MODE}): any food can be eaten on a full stomach. Vanilla's eating refuses
 * when the hunger bar is full unless the food is always-edible, so a right-click with food the player can't otherwise
 * eat starts eating here; finishing still feeds as usual (saturation tops up). Runs on both sides, and the SERVER
 * config is synced, so the client's eating animation matches.
 */
public final class FeastMode {
    private FeastMode() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FeastMode::onUse);
    }

    private static void onUse(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null || food.canAlwaysEat() || player.canEat(false) || player.isSpectator()
                || !LotrConfig.SPEC.isLoaded() || !LotrConfig.FEAST_MODE.get()) {
            return;
        }
        player.startUsingItem(event.getHand());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }
}
