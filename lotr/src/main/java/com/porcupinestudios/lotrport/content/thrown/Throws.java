package com.porcupinestudios.lotrport.content.thrown;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Launching LOTR's thrown things, by hand, from a dispenser or by an NPC. */
public final class Throws {
    private Throws() {}

    /**
     * Throws a spear, axe or dart along the thrower's look. LOTREntityProjectileBase launched at speed x 1.5 (as
     * 1.7.10's arrows), so a full-power spear flies like a full bow's arrow.
     */
    public static ThrownWeapon weapon(Level level, LivingEntity thrower, ItemStack item, ThrownWeapon.Kind kind,
            float multiplier, float speed, boolean crit) {
        ThrownWeapon w = new ThrownWeapon(level, thrower, item, kind, multiplier);
        w.shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), 0f, speed * 1.5f, 1f);
        w.setCritArrow(crit);
        if (thrower instanceof Player p && p.getAbilities().instabuild) {
            w.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }
        level.addFreshEntity(w);
        return w;
    }

    /** Throws a pebble, conker, fire pot, web or plate along the thrower's look, at LOTR's speed for it. */
    public static ThrownMissile missile(Level level, LivingEntity thrower, ItemStack item, ThrownMissile.Kind kind) {
        ThrownMissile m = new ThrownMissile(level, thrower, item, kind);
        m.shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), 0f, ThrownMissile.speed(kind), 1f);
        level.addFreshEntity(m);
        return m;
    }

    /** From a dispenser: out of its face, slightly up, as vanilla's projectile dispensing. */
    public static void fromDispenser(Projectile p, Direction facing, float speed) {
        p.shoot(facing.getStepX(), facing.getStepY() + 0.1f, facing.getStepZ(), speed, 6f);
        p.level().addFreshEntity(p);
    }

    public static ThrownWeapon weaponAt(Level level, Position at, ItemStack item, ThrownWeapon.Kind kind,
            float multiplier) {
        ThrownWeapon w = new ThrownWeapon(level, at.x(), at.y(), at.z(), item, kind, multiplier);
        w.pickup = AbstractArrow.Pickup.ALLOWED;
        return w;
    }

    /** LOTR's "random.bow" when throwing: pitch 1 / (0.4 x rand + 1.2) + power / 2. */
    public static void sound(Level level, LivingEntity thrower, float power) {
        level.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 1f, 1f / (level.random.nextFloat() * 0.4f + 1.2f) + power * 0.5f);
    }

    /** LOTR's lighter throw sound for pebbles, conkers, pots and webs. */
    public static void lightSound(Level level, LivingEntity thrower) {
        level.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 0.5f, 0.4f / (level.random.nextFloat() * 0.4f + 0.8f));
    }

    /** LOTR's power curve for a drawn throw: f = ticks / draw, (f^2 + 2f) / 3, at most 1. */
    public static float power(int ticks, float draw) {
        float f = ticks / draw;
        return Math.min(1f, (f * f + f * 2f) / 3f);
    }
}
