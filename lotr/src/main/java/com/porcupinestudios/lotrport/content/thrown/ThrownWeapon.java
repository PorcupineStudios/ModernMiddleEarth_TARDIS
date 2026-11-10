package com.porcupinestudios.lotrport.content.thrown;

import com.porcupinestudios.lotrport.content.LotrWeaponItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * A spear, throwing axe or dart in flight (LOTREntityProjectileBase and its LOTREntitySpear, ThrowingAxe and Dart):
 * an arrow carrying its item. Damage is speed x the item's multiplier, rounded up, as an arrow's (base damage =
 * multiplier); a crit adds LOTR's nextInt(damage / 2 + 2). A spear or axe that hits drops beside its target to be
 * picked up (one point more worn); a dart is spent. Axes spin a turn every {@code axe_spin_ticks}.
 */
public class ThrownWeapon extends AbstractArrow {
    public enum Kind {
        SPEAR, AXE, DART
    }

    private static final EntityDataAccessor<ItemStack> ITEM =
            SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> KIND =
            SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.BYTE);
    private int groundTicks;

    public ThrownWeapon(EntityType<? extends ThrownWeapon> type, Level level) {
        super(type, level);
    }

    public ThrownWeapon(Level level, LivingEntity owner, ItemStack item, Kind kind, float multiplier) {
        super(Thrown.WEAPON.get(), owner, level, item.copyWithCount(1), null);
        setup(item, kind, multiplier);
    }

    public ThrownWeapon(Level level, double x, double y, double z, ItemStack item, Kind kind, float multiplier) {
        super(Thrown.WEAPON.get(), x, y, z, level, item.copyWithCount(1), null);
        setup(item, kind, multiplier);
    }

    private void setup(ItemStack item, Kind kind, float multiplier) {
        entityData.set(ITEM, item.copyWithCount(1));
        entityData.set(KIND, (byte) kind.ordinal());
        setBaseDamage(multiplier);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(KIND, (byte) 0);
    }

    public ItemStack item() {
        return entityData.get(ITEM);
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(entityData.get(KIND), Kind.values().length)];
    }

    public boolean isInGround() {
        return inGround;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    /** LOTR's createPickupDrop: the item comes back one point more worn, or not at all if that breaks it. */
    @Override
    public ItemStack getPickupItem() {
        ItemStack out = item().copy();
        if (out.isDamageableItem()) {
            out.setDamageValue(out.getDamageValue() + 1);
            if (out.getDamageValue() >= out.getMaxDamage()) {
                return ItemStack.EMPTY;
            }
        }
        return out;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        double speed = getDeltaMovement().length();
        int damage = Mth.ceil(Mth.clamp(speed * getBaseDamage(), 0.0, Integer.MAX_VALUE));
        if (isCritArrow()) {
            damage = (int) Math.min((long) random.nextInt(damage / 2 + 2) + damage, Integer.MAX_VALUE);
        }
        Entity owner = getOwner();
        DamageSource source = damageSources().thrown(this, owner == null ? this : owner);
        if (owner instanceof LivingEntity living) {
            living.setLastHurtMob(target);
        }
        int fire = target.getRemainingFireTicks();
        if (isOnFire()) {
            target.igniteForSeconds(5f);
        }
        if (target.hurt(source, damage)) {
            if (target instanceof LivingEntity living) {
                if (kind() == Kind.DART) {
                    double knock = Thrown.num("dart_knockback", 0.5f);
                    living.knockback(0.4 * knock, -getDeltaMovement().x, -getDeltaMovement().z);
                }
                doPostHurtEffects(living);
            }
            playSound(getDefaultHitGroundSoundEvent(), 1f, 1.2f / (random.nextFloat() * 0.2f + 0.9f));
            if (item().isDamageableItem()) {
                setDeltaMovement(getDeltaMovement().multiply(-0.01, -0.1, -0.01)); // drops by its target
            } else {
                discard();
            }
        } else {
            target.setRemainingFireTicks(fire);
            setDeltaMovement(getDeltaMovement().scale(-0.1));
            setYRot(getYRot() + 180f);
            yRotO += 180f;
        }
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        if (item().getItem() instanceof DartItem dart && dart.poisoned()) {
            LotrWeaponItem.applyPoison(new com.google.gson.JsonObject(), target);
        }
    }

    @Override
    protected void tickDespawn() {
        int limit = kind() != Kind.DART && pickup == Pickup.ALLOWED
                ? (int) Thrown.num("ground_ticks_pickup", 6000) : (int) Thrown.num("ground_ticks", 1200);
        if (++groundTicks >= limit) {
            discard();
        }
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.ARROW_HIT;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Thrown", item().saveOptional(registryAccess()));
        tag.putByte("ThrownKind", entityData.get(KIND));
        tag.putInt("GroundTicks", groundTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ITEM, ItemStack.parseOptional(registryAccess(), tag.getCompound("Thrown")));
        entityData.set(KIND, tag.getByte("ThrownKind"));
        groundTicks = tag.getInt("GroundTicks");
    }
}
