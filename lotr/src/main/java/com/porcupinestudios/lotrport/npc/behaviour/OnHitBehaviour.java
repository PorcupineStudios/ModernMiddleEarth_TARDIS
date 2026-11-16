package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * What a LOTR creature's melee blow does besides damage (each class's attackEntityAsMob), all from
 * {@code behaviour_data}:
 * <ul>
 * <li>Effects lasting {@code <effect>_d2} x d&sup2; + {@code <effect>_d} x d + {@code <effect>_c} seconds, d the
 * difficulty (1-3), for {@code poison} and {@code slow}: Mirk-troll poison 3d - 1, snow-troll and ice-warg slowness
 * d(d + 5) / 2, scorpion poison d(d + 5) / 2.</li>
 * <li>{@code fire}: sets the target burning that many seconds (Utumno fire warg, 4).</li>
 * <li>{@code frost} 1: LOTR's frost overlay (ice warg, ice spider), drawn as vanilla's freezing overlay.</li>
 * <li>{@code knockback} (flat, aurochs 0.75) or {@code knockback_per_damage} (x attack damage, olog-hai 0.25): pushed
 * the way the attacker faces, half that strength.</li>
 * <li>{@code splash_range} (olog-hai, 4): everything else within that of the target, but the attacker's side, takes
 * (4 - distance + 1, at most 4) / 4 of the attack damage and is thrown back and up.</li>
 * </ul>
 * The hit sound is the type's {@code attack} sound (the olog-hai's hammer).
 */
public class OnHitBehaviour implements CreatureBehaviour {
    @Override
    public void hit(LotrNpc npc, Entity target) {
        int d = npc.level().getDifficulty().getId();
        if (target instanceof LivingEntity living) {
            effect(npc, living, "poison", MobEffects.POISON, d);
            effect(npc, living, "slow", MobEffects.MOVEMENT_SLOWDOWN, d);
            if (CreatureBehaviour.data(npc, "frost", 0) > 0) {
                // just under fully frozen: the overlay shows and thaws, with no freezing damage
                living.setTicksFrozen(Math.max(living.getTicksFrozen(), living.getTicksRequiredToFreeze() - 1));
            }
        }
        float fire = CreatureBehaviour.data(npc, "fire", 0);
        if (fire > 0) {
            target.igniteForSeconds(fire);
        }
        float damage = (float) npc.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float kb = CreatureBehaviour.data(npc, "knockback", 0)
                + CreatureBehaviour.data(npc, "knockback_per_damage", 0) * damage;
        if (kb > 0) {
            push(npc, target, kb * 0.5f, 0);
        }
        float range = CreatureBehaviour.data(npc, "splash_range", 0);
        if (range > 0) {
            splash(npc, target, range, damage);
        }
    }

    private static void effect(LotrNpc npc, LivingEntity target, String key, Holder<MobEffect> effect, int d) {
        float seconds = CreatureBehaviour.data(npc, key + "_d2", 0) * d * d + CreatureBehaviour.data(npc, key + "_d", 0) * d
                + CreatureBehaviour.data(npc, key + "_c", 0);
        if (seconds > 0) {
            target.addEffect(new MobEffectInstance(effect, Math.round(seconds * 20), 0), npc);
        }
    }

    private static void splash(LotrNpc npc, Entity target, float range, float damage) {
        for (LivingEntity e : npc.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(range))) {
            if (e == npc || e == target || friend(npc, e)) {
                continue;
            }
            float strength = Math.min(range, range - target.distanceTo(e) + 1);
            if (strength > 0 && e.hurt(npc.damageSources().mobAttack(npc), strength / range * damage)) {
                float kb = Math.max(0.75f, strength * 0.25f);
                push(npc, e, kb * 0.5f, 0.2 + 0.12 * kb);
            }
        }
    }

    /** Who a splash spares: its own side, its faction's people, creative and spectating players. */
    private static boolean friend(LotrNpc npc, LivingEntity e) {
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return true;
        }
        return npc.sameSide(e) || e instanceof LotrNpc n && n.faction().isPresent() && n.faction().equals(npc.faction());
    }

    private static void push(LotrNpc npc, Entity e, float strength, double up) {
        double yaw = Math.toRadians(npc.getYRot());
        e.push(-Math.sin(yaw) * strength, up, Math.cos(yaw) * strength);
        e.hurtMarked = true;
    }
}
