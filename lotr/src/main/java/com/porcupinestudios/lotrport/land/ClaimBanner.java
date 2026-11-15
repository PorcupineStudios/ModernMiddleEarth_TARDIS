package com.porcupinestudios.lotrport.land;

import com.porcupinestudios.lotrport.colony.members.Perm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A planted banner (LOTR's banner entity): the land around it is claimed for as long as it stands. Its entity UUID
 * is the claim's id. Only someone allowed to take down banners there can (a hit drops it); breaking it any other way
 * (a command) also frees the land.
 */
public class ClaimBanner extends Entity {
    private static final EntityDataAccessor<ItemStack> ITEM =
            SynchedEntityData.defineId(ClaimBanner.class, EntityDataSerializers.ITEM_STACK);

    public ClaimBanner(EntityType<? extends ClaimBanner> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
    }

    public ItemStack item() {
        return entityData.get(ITEM);
    }

    public void setItem(ItemStack stack) {
        entityData.set(ITEM, stack.copyWithCount(1));
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return item().copy();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return true;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        boolean mayTake = Claims.get(player.server).get(getUUID())
                .map(c -> player.hasPermissions(2) || Protection.allowed(player.server, player, c, Perm.BANNERS))
                .orElse(true);
        if (!mayTake) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatableWithFallback(
                    "land.lotr_port.banner_not_yours", "You may not take down this banner"), true);
            return false;
        }
        if (!player.getAbilities().instabuild) {
            spawnAtLocation(item().copy());
        }
        discard();
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel level) {
            Claims.get(level.getServer()).remove(level.getServer(), getUUID());
        }
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 100 == 0 && level() instanceof ServerLevel level
                && Claims.get(level.getServer()).get(getUUID()).isEmpty()) {
            spawnAtLocation(item().copy()); // its land was released another way (colony disbanded): it falls
            discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!item().isEmpty()) {
            tag.put("Item", item().save(registryAccess()));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setItem(ItemStack.parseOptional(registryAccess(), tag.getCompound("Item")));
    }
}
