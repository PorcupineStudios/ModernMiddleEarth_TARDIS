package com.porcupinestudios.lotrport.content.thrown;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * The Taurethrim blowgun (LOTRItemBlowgun): drawn like a bow over {@code blowgun_draw} ticks, no shot below
 * {@code blowgun_min_power}, a dart ({@code lotr_port:darts}) at power x {@code blowgun_speed}; worn a point a shot.
 */
public class BlowgunItem extends Item {
    public static final TagKey<Item> DARTS = TagKey.create(Registries.ITEM, LotrPort.id("darts"));

    public BlowgunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getAbilities().instabuild || !dart(player).isEmpty()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    static ItemStack dart(Player player) {
        if (player.getOffhandItem().is(DARTS)) {
            return player.getOffhandItem();
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(DARTS)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        float f = (getUseDuration(stack, entity) - timeLeft) / Thrown.num("blowgun_draw", 5);
        if (f < Thrown.num("blowgun_min_power", 0.65f)) {
            return;
        }
        float power = Math.min(1f, (f * f + f * 2f) / 3f);
        ItemStack ammo = dart(player);
        boolean creative = player.getAbilities().instabuild;
        if (ammo.isEmpty() && !creative) {
            return;
        }
        ItemStack shot = ammo.isEmpty() ? new ItemStack(Thrown.item("taurethrim_dart")) : ammo.copyWithCount(1);
        if (!level.isClientSide) {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
            ThrownWeapon dart = Throws.weapon(level, player, shot, ThrownWeapon.Kind.DART,
                    Thrown.num("dart_damage", 1f), power * Thrown.num("blowgun_speed", 2f), power >= 1f);
            if (creative) {
                dart.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.CREATIVE_ONLY;
            }
            LotrSounds.playAt(level, player.getX(), player.getY(), player.getZ(), "item.dart", SoundSource.PLAYERS,
                    1f, 1f / (level.random.nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        }
        if (!creative) {
            ammo.shrink(1);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }
}
