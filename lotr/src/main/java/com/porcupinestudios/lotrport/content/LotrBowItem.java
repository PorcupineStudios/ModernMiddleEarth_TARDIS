package com.porcupinestudios.lotrport.content;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A LOTR bow: its own draw time (LOTRItemBow.bowPullTime; vanilla bows take 20 ticks to full power) and arrow damage
 * factor (arrowDamageFactor), both generated data. 1.21's BowItem reads the power from ticks held / 20, so the ticks
 * held are scaled to this bow's draw time before vanilla takes over.
 */
public class LotrBowItem extends BowItem {
    private final int drawTime;
    private final float damageFactor;

    public LotrBowItem(Properties properties, int drawTime, float damageFactor) {
        super(properties);
        this.drawTime = Math.max(1, drawTime);
        this.damageFactor = damageFactor;
    }

    public int drawTime() {
        return drawTime;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int duration = getUseDuration(stack, entity);
        int held = Math.round((duration - timeLeft) * 20f / drawTime);
        super.releaseUsing(stack, level, entity, duration - held);
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        arrow.setBaseDamage(arrow.getBaseDamage() * damageFactor);
        return arrow;
    }
}
