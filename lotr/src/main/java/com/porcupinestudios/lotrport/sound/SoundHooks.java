package com.porcupinestudios.lotrport.sound;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrCrossbowItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/**
 * Sounds the game plays that LOTR replaced. A LOTR crossbow is a vanilla crossbow underneath, so vanilla plays its
 * shoot and loading sounds at the shooter's position; when the shooter holds a LOTR crossbow they become LOTR's
 * {@code item.crossbow} and {@code item.crossbow_load} (LOTRItemCrossbow).
 */
@EventBusSubscriber(modid = LotrPort.MOD_ID)
public final class SoundHooks {
    private SoundHooks() {}

    @SubscribeEvent
    public static void onSound(PlayLevelSoundEvent.AtPosition event) {
        var sound = event.getSound();
        if (sound == null) {
            return;
        }
        String replace = null;
        if (sound.is(SoundEvents.CROSSBOW_SHOOT.getLocation())) {
            replace = "item.crossbow";
        } else if (sound.is(SoundEvents.CROSSBOW_LOADING_END.value().getLocation())) {
            replace = "item.crossbow_load";
        }
        if (replace == null || !shooterHoldsLotrCrossbow(event)) {
            return;
        }
        LotrSounds.get(replace).ifPresent(event::setSound);
    }

    private static boolean shooterHoldsLotrCrossbow(PlayLevelSoundEvent.AtPosition event) {
        Vec3 at = event.getPosition();
        for (Player p : event.getLevel().players()) {
            if (p.position().distanceToSqr(at) < 0.01 && (p.getMainHandItem().getItem() instanceof LotrCrossbowItem
                    || p.getOffhandItem().getItem() instanceof LotrCrossbowItem)) {
                return true;
            }
        }
        return false;
    }
}
