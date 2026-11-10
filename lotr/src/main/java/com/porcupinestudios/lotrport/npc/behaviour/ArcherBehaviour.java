package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.thrown.BlowgunItem;
import com.porcupinestudios.lotrport.content.thrown.SlingItem;
import com.porcupinestudios.lotrport.content.thrown.ThrowableItem;
import com.porcupinestudios.lotrport.content.thrown.Thrown;
import com.porcupinestudios.lotrport.content.thrown.ThrowingAxeItem;
import com.porcupinestudios.lotrport.content.thrown.ThrownMissile;
import com.porcupinestudios.lotrport.content.thrown.ThrownWeapon;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * LOTR's archers and throwers (LOTREntityAIRangedAttack with LOTREntityNPC.npcArrowAttack / npcCrossbowAttack and
 * the thrower NPCs' attackEntityWithRangedAttack). From the type's {@code behaviour_data}: {@code ranged_speed}
 * (pace closing in), {@code ranged_min}/{@code ranged_max} (ticks between shots, more the farther the target),
 * {@code ranged_range} (shooting range), {@code ranged_accuracy} (npcRangedAccuracy: spread) and {@code ranged_swap}
 * (elves and the like take out their melee weapon inside melee range, 4 + width^2 blocks; pure archers keep
 * shooting and back off from anything within 5 blocks). The ranged weapon is the type's {@code ranged} gear (or the
 * main hand for pure archers); what it shoots follows the weapon: arrows, bolts, darts, axes, fire pots, pebbles.
 */
public class ArcherBehaviour implements CreatureBehaviour {
    private static final String RANGED = LotrPort.MOD_ID + ":ranged_weapon";
    private static final String MELEE = LotrPort.MOD_ID + ":melee_weapon";

    @Override
    public void init(LotrNpc npc, NpcType type) {
        List<ResourceLocation> ranged = type.gear().get("ranged");
        if (ranged != null && !ranged.isEmpty()) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ranged.get(npc.random().nextInt(ranged.size()))));
            npc.getPersistentData().put(RANGED, stack.saveOptional(npc.registryAccess()));
        }
    }

    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(0, new ShootGoal(npc));
    }

    static boolean swaps(LotrNpc npc) {
        return CreatureBehaviour.data(npc, "ranged_swap", 0f) > 0f;
    }

    /** LOTREntityNPC.getMeleeRange: 4 + width^2. */
    static double meleeRange(LotrNpc npc) {
        return 4.0 + npc.getBbWidth() * npc.getBbWidth();
    }

    private static final class ShootGoal extends Goal {
        private final LotrNpc npc;
        private int seen;
        private int cooldown = -1;

        ShootGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean wanted() {
            LivingEntity t = npc.getTarget();
            if (t == null || !t.isAlive()) {
                return false;
            }
            double d = npc.distanceTo(t);
            return d <= npc.getAttributeValue(Attributes.FOLLOW_RANGE) * 0.95 && (!swaps(npc) || d >= meleeRange(npc));
        }

        @Override
        public boolean canUse() {
            return wanted();
        }

        @Override
        public boolean canContinueToUse() {
            return wanted();
        }

        @Override
        public void start() {
            if (swaps(npc)) {
                ItemStack ranged = ItemStack.parseOptional(npc.registryAccess(),
                        npc.getPersistentData().getCompound(RANGED));
                if (!ranged.isEmpty()) {
                    npc.getPersistentData().put(MELEE, npc.getMainHandItem().saveOptional(npc.registryAccess()));
                    npc.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ranged);
                }
            }
            seen = 0;
            cooldown = -1;
        }

        @Override
        public void stop() {
            if (swaps(npc) && npc.getPersistentData().contains(MELEE)) {
                npc.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        ItemStack.parseOptional(npc.registryAccess(), npc.getPersistentData().getCompound(MELEE)));
                npc.getPersistentData().remove(MELEE);
            }
            npc.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = npc.getTarget();
            if (t == null) {
                return;
            }
            double distSq = npc.distanceToSqr(t.getX(), t.getBoundingBox().minY, t.getZ());
            float range = CreatureBehaviour.data(npc, "ranged_range", 16f);
            boolean sees = npc.getSensing().hasLineOfSight(t);
            seen = sees ? seen + 1 : 0;
            if (distSq <= range * range) {
                double flee = Thrown.num("npc_flee_distance", 5f);
                if (npc.distanceToSqr(t) < flee * flee) {
                    Vec3 away = DefaultRandomPos.getPosAway(npc, (int) Thrown.num("npc_flee_max", 16),
                            (int) Thrown.num("npc_flee_min", 8) / 2, t.position());
                    if (away != null) {
                        npc.getNavigation().moveTo(away.x, away.y, away.z, Thrown.num("npc_flee_speed", 1.5f));
                    }
                } else if (seen >= Thrown.num("npc_stop_after", 20)) {
                    npc.getNavigation().stop();
                }
            } else {
                npc.getNavigation().moveTo(t, CreatureBehaviour.data(npc, "ranged_speed", 1.25f));
            }
            npc.getLookControl().setLookAt(t, 30f, 30f);
            float lo = CreatureBehaviour.data(npc, "ranged_min", 30f);
            float hi = CreatureBehaviour.data(npc, "ranged_max", 50f);
            if (--cooldown == 0) {
                if (distSq > range * range || !sees) {
                    return;
                }
                float f = (float) Math.sqrt(distSq) / range;
                fire(npc, t, Mth.clamp(f, 0.1f, 1f));
                cooldown = Mth.floor(f * (hi - lo) + lo);
            } else if (cooldown < 0) {
                cooldown = Mth.floor((float) Math.sqrt(distSq) / range * (hi - lo) + lo);
            }
        }
    }

    /** One shot with whatever the NPC holds. */
    static void fire(LotrNpc npc, LivingEntity t, float power) {
        ItemStack weapon = npc.getMainHandItem();
        float dist = npc.distanceTo(t);
        // 1.7.10 spread 0.0075 per accuracy point, 1.21's shoot() 0.0172275
        float spread = CreatureBehaviour.data(npc, "ranged_accuracy", 1f) * Thrown.num("npc_spread_scale", 0.4354f);
        var level = npc.level();
        Projectile shot;
        float speed;
        if (weapon.getItem() instanceof CrossbowItem) {
            ItemStack bolt = new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("crossbow_bolt")));
            AbstractArrow a = ProjectileUtil.getMobArrow(npc, bolt.isEmpty() ? new ItemStack(Items.ARROW) : bolt, 1f,
                    weapon);
            a = ((CrossbowItem) weapon.getItem()).customArrow(a, bolt, weapon);
            shot = a;
            speed = (Thrown.num("npc_bolt_speed", 1f) + dist * Thrown.num("npc_bolt_speed_per_block", 0.0009375f))
                    * 1.5f;
            LotrSounds.playAt(level, npc.getX(), npc.getY(), npc.getZ(), "item.crossbow", SoundSource.HOSTILE, 1f,
                    1f / (npc.random().nextFloat() * 0.4f + 0.8f));
        } else if (weapon.getItem() instanceof BowItem bow) {
            AbstractArrow a = ProjectileUtil.getMobArrow(npc, new ItemStack(Items.ARROW), 1f, weapon);
            shot = bow.customArrow(a, new ItemStack(Items.ARROW), weapon);
            speed = Thrown.num("npc_arrow_speed", 1.3f) + dist * Thrown.num("npc_arrow_speed_per_block", 0.0125f);
            level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1f,
                    1f / (npc.random().nextFloat() * 0.4f + 0.8f));
        } else if (weapon.getItem() instanceof BlowgunItem) {
            ItemStack dart = new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("taurethrim_dart")));
            ThrownWeapon d = new ThrownWeapon(level, npc, dart, ThrownWeapon.Kind.DART, Thrown.num("dart_damage", 1f));
            d.pickup = AbstractArrow.Pickup.DISALLOWED;
            shot = d;
            speed = (Thrown.num("npc_bolt_speed", 1f) + dist * Thrown.num("npc_bolt_speed_per_block", 0.0009375f))
                    * 1.5f;
            LotrSounds.playAt(level, npc.getX(), npc.getY(), npc.getZ(), "item.dart", SoundSource.HOSTILE, 1f,
                    1f / (npc.random().nextFloat() * 0.4f + 1.2f) + 0.5f);
        } else if (weapon.getItem() instanceof ThrowingAxeItem axe) {
            ThrownWeapon w = new ThrownWeapon(level, npc, weapon, ThrownWeapon.Kind.AXE, axe.multiplier());
            w.pickup = AbstractArrow.Pickup.DISALLOWED;
            shot = w;
            speed = 1.5f;
            level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1f,
                    1f / (npc.random().nextFloat() * 0.4f + 0.8f));
        } else {
            // LOTR's fire throwers and shirriffs aim a dummy arrow at 1.0 with spread 0.5 and send the missile along it
            ThrownMissile.Kind kind = weapon.getItem() instanceof ThrowableItem th ? th.kind()
                    : weapon.getItem() instanceof SlingItem ? ThrownMissile.Kind.SLING_PEBBLE : ThrownMissile.Kind.PEBBLE;
            ItemStack item = kind == ThrownMissile.Kind.SLING_PEBBLE
                    ? new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("pebble"))) : weapon.copyWithCount(1);
            shot = new ThrownMissile(level, npc, item, kind);
            speed = 1f;
            spread = 0.5f * Thrown.num("npc_spread_scale", 0.4354f);
            level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1f,
                    1f / (npc.random().nextFloat() * 0.4f + 0.8f));
        }
        double dx = t.getX() - npc.getX();
        double dy = t.getY(0.3333) - shot.getY();
        double dz = t.getZ() - npc.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        shot.shoot(dx, dy + flat * 0.2, dz, speed, spread);
        level.addFreshEntity(shot);
        npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
}
