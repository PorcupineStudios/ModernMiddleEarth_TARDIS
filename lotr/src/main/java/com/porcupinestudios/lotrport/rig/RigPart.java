package com.porcupinestudios.lotrport.rig;

import com.porcupinestudios.lotrport.npc.RiggedNpc;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One hitbox of a rigged creature (head, body, a leg...), as the Ender Dragon's parts: arrows, swords and the
 * crosshair hit it, and the damage goes to the creature times the part's factor. It is moved every tick to where its
 * bone is. Unused slots (a rig with fewer parts than {@link RiggedNpc#MAX_PARTS}) are switched off.
 */
public class RigPart extends PartEntity<RiggedNpc> {
    private EntityDimensions size = EntityDimensions.scalable(0.1f, 0.1f);
    private boolean active;
    private boolean solid;
    private float damage = 1;
    private String partName = "";

    public RigPart(RiggedNpc parent) {
        super(parent);
        noPhysics = true;
        refreshDimensions();
    }

    /** Sets what this slot is; an inactive slot can't be hit. */
    public void configure(boolean active, String name, float width, float height, float damage, boolean solid) {
        this.active = active;
        this.partName = name;
        this.damage = damage;
        this.solid = solid;
        float w = Math.max(0.05f, width);
        float h = Math.max(0.05f, height);
        if (size.width() != w || size.height() != h) {
            size = EntityDimensions.scalable(w, h);
            refreshDimensions();
        }
    }

    public boolean active() {
        return active;
    }

    public String partName() {
        return partName;
    }

    /** Moves it to a centre point (its box hangs from there by half its height), keeping last tick's spot for lerp. */
    public void follow(double x, double y, double z) {
        double ox = getX();
        double oy = getY();
        double oz = getZ();
        setPos(x, y - size.height() / 2, z);
        xo = ox;
        yo = oy;
        zo = oz;
        xOld = ox;
        yOld = oy;
        zOld = oz;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return active && getParent().isAlive();
    }

    @Override
    public boolean canBeCollidedWith() {
        return active && solid && getParent().isAlive();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!active || isInvulnerableTo(source)) {
            return false;
        }
        return getParent().hurtPart(this, source, amount * damage);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interact(player, hand);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
