package com.porcupinestudios.lotrport.content;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A LOTR crossbow on 1.21's crossbow (load, then fire). It shoots LOTR's crossbow bolts only (item tag
 * {@code lotr_port:crossbow_bolts}), and the bolt's damage is scaled by the crossbow's boltDamageFactor. Loading time
 * stays vanilla's 1.25 s: CrossbowItem.getChargeDuration is static in 1.21.1, so it can't be per item.
 */
public class LotrCrossbowItem extends CrossbowItem {
    public static final TagKey<Item> BOLTS = TagKey.create(Registries.ITEM, LotrPort.id("crossbow_bolts"));
    private static final Predicate<ItemStack> IS_BOLT = s -> s.is(BOLTS);
    private final float damageFactor;

    public LotrCrossbowItem(Properties properties, float damageFactor) {
        super(properties);
        this.damageFactor = damageFactor;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return IS_BOLT;
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return IS_BOLT;
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        arrow.setBaseDamage(arrow.getBaseDamage() * damageFactor);
        return arrow;
    }

    @Override
    public ItemStack getDefaultCreativeAmmo(@Nullable Player player, ItemStack weapon) {
        Item bolt = BuiltInRegistries.ITEM.get(LotrPort.id("crossbow_bolt"));
        return new ItemStack(bolt);
    }
}
