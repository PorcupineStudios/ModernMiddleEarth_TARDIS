package com.porcupinestudios.lotrport.drink;

import com.porcupinestudios.lotrport.faction.Alignment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.EffectCures;

/**
 * A LOTR drink in its vessel (LOTRItemMug, full): drinking it does what its {@link DrinkData} says, scaled by its
 * strength, and leaves the empty vessel. Food drinks (juices, cocoa) only when hungry, as LOTR.
 */
public class DrinkItem extends Item {
    private final boolean brewable;
    private final boolean food;

    public DrinkItem(Properties properties, boolean brewable, boolean food) {
        super(properties);
        this.brewable = brewable;
        this.food = food;
    }

    public boolean brewable() {
        return brewable;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32; // vanilla's and LOTR's drinking time
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (food && !player.canEat(false)) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel server) {
            drink(stack, server, entity);
        }
        Item vessel = Drinks.vessel(stack);
        stack.consume(1, entity);
        if (stack.isEmpty()) {
            return new ItemStack(vessel);
        }
        if (entity instanceof Player player && !player.hasInfiniteMaterials()) {
            ItemStack empty = new ItemStack(vessel);
            if (!player.getInventory().add(empty)) {
                player.drop(empty, false);
            }
        }
        return stack;
    }

    /** LOTRItemMug's drinking: food, then nausea by alcohol, cures, effects, magic damage, and the odd explosion. */
    static void drink(ItemStack stack, ServerLevel level, LivingEntity entity) {
        DrinkData d = DrinkData.of(stack.getItem());
        DrinkData.Settings s = DrinkData.settings();
        boolean brewable = Drinks.brewable(stack.getItem());
        int i = Math.min(Math.max(Drinks.strength(stack), 0), s.maxStrength());
        float strength = brewable ? s.alcoholStrength().get(i) : 1f;
        float foodStrength = brewable ? s.foodStrength().get(i) : 1f;
        if (entity instanceof Player p && (d.nutrition() > 0 || d.saturation() > 0)) {
            p.getFoodData().eat(Math.round(d.nutrition() * foodStrength), d.saturation() * foodStrength);
        }
        if (d.alcohol() > 0) {
            // shortcut: LOTR also lowered this by 1% per point of the drinker's alcohol tolerance, which isn't ported
            int seconds = (int) (60 * (1 + level.random.nextFloat() * 0.5f) * d.alcohol() * strength);
            if (seconds >= 1) {
                entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, seconds * 20));
            }
        }
        if (d.cures()) {
            entity.removeEffectsCuredBy(EffectCures.MILK);
        }
        if (d.curesHarmful()) {
            for (MobEffectInstance e : new ArrayList<>(entity.getActiveEffects())) {
                if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                    entity.removeEffect(e.getEffect());
                }
            }
        }
        if (d.faction().isPresent() && !(entity instanceof Player p && Alignment.get(p, d.faction().get().id()) > 0)) {
            for (DrinkData.Effect e : d.faction().get().otherwise()) {
                entity.addEffect(new MobEffectInstance(e.id(), e.duration(), e.amplifier()));
            }
        } else {
            for (DrinkData.Effect e : d.effects()) {
                entity.addEffect(new MobEffectInstance(e.id(), (int) (e.duration() * strength), e.amplifier()));
            }
        }
        if (d.damage() > 0) {
            entity.hurt(level.damageSources().magic(), d.damage() * strength);
        }
        d.explode().ifPresent(x -> {
            if (level.random.nextInt(Math.max(1, x.chance())) == 0) {
                level.explode(null, entity.getX(), entity.getY(), entity.getZ(), x.power(),
                        Level.ExplosionInteraction.TNT);
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (brewable) {
            tooltip.add(Component.translatable("item.lotr_port.drink.strength." + Drinks.strength(stack))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
