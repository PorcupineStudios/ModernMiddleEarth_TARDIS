package com.porcupinestudios.lotrport.content;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * A LOTR melee weapon (swords, daggers, spears, polearms, pikes, lances, hammers, battleaxes) with LOTR's numbers
 * moved onto 1.21's combat attributes. 1.7.10 had no attack cooldown, so LOTR timed melee itself (LOTRWeaponStats:
 * 15 ticks for a player's sword, divided by a speed factor per weapon class) and stretched reach by a factor of the
 * 3-block base. Here the generator turns that into {@code weapon.attack_speed} (20 / LOTR's ticks; a sword is 1.33,
 * a dagger 2, a pike 0.67), {@code weapon.reach} (added to player entity_interaction_range) and {@code
 * weapon.knockback} (attack_knockback, hammers and lances +1). Damage is LOTR's own (fractions kept; 1.7.10's +1 base
 * hand damage is the same in 1.21). Only plain swords sweep. NPCs swing at LOTR's mob rate ({@link #mobAttackTicks}).
 */
public class LotrWeaponItem extends SwordItem {
    private final boolean sweep;
    private final int mobTicks;
    private final JsonObject poison;

    public LotrWeaponItem(Tier tier, Properties properties, float damage, JsonObject weapon, JsonObject poison) {
        super(tier, properties.attributes(attributes(damage, weapon)));
        this.sweep = ContentData.bool(weapon, "sweep", true);
        this.mobTicks = ContentData.integer(weapon, "mob_ticks", 20);
        this.poison = poison;
    }

    static ItemAttributeModifiers attributes(float damage, JsonObject weapon) {
        float speed = ContentData.num(weapon, "attack_speed", 1.6f);
        float reach = ContentData.num(weapon, "reach", 0f);
        float knockback = ContentData.num(weapon, "knockback", 0f);
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                // the player's base attack speed is 4; the modifier brings it to the weapon's
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, speed - 4.0,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        if (reach != 0f) {
            b.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(LotrPort.id("weapon_reach"), reach,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        if (knockback != 0f) {
            b.add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(LotrPort.id("weapon_knockback"), knockback,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        return b.build();
    }

    /** Ticks between an NPC's swings with this weapon (LOTR: 20 / the class's speed factor). */
    public int mobAttackTicks() {
        return mobTicks;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        if (ability == ItemAbilities.SWORD_SWEEP) {
            return sweep;
        }
        return super.canPerformAction(stack, ability);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (poison != null) {
            applyPoison(poison, target);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    /** LOTR's standard poison (LOTRItemDagger.applyStandardPoison): n = base + per_difficulty x difficulty, lasting
     * n to 2n - 1 seconds of Poison I. */
    public static void applyPoison(JsonObject poison, LivingEntity target) {
        int n = ContentData.integer(poison, "base_seconds", 1)
                + ContentData.integer(poison, "per_difficulty", 2) * target.level().getDifficulty().getId();
        target.addEffect(new MobEffectInstance(MobEffects.POISON, (n + target.getRandom().nextInt(Math.max(1, n))) * 20));
    }
}
