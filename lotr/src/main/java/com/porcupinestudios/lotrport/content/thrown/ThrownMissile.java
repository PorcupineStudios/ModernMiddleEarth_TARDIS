package com.porcupinestudios.lotrport.content.thrown;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * A pebble, conker, fire pot, mystery web or plate in flight (LOTR's EntityThrowables), drawn as its item.
 * <ul>
 * <li>pebble and conker: a little damage (a sling's pebble twice as much), then they fall as items again;</li>
 * <li>fire pot: burns and hurts everything within {@code fire_pot_range} blocks, more on a direct hit;</li>
 * <li>mystery web: sometimes a Mirkwood spider bursts out, angry at the thrower;</li>
 * <li>plate: hurts a little, breaks the glass it hits and some glass around, and shatters.</li>
 * </ul>
 */
public class ThrownMissile extends ThrowableItemProjectile {
    public enum Kind {
        PEBBLE, SLING_PEBBLE, CONKER, FIRE_POT, MYSTERY_WEB, PLATE;

        String key() {
            return switch (this) {
                case SLING_PEBBLE -> "pebble";
                default -> name().toLowerCase();
            };
        }

        public static Kind of(String name) {
            for (Kind k : values()) {
                if (k.name().equalsIgnoreCase(name)) {
                    return k;
                }
            }
            return PEBBLE;
        }
    }

    private static final EntityDataAccessor<Byte> KIND =
            SynchedEntityData.defineId(ThrownMissile.class, EntityDataSerializers.BYTE);

    public ThrownMissile(EntityType<? extends ThrownMissile> type, Level level) {
        super(type, level);
    }

    public ThrownMissile(Level level, LivingEntity thrower, ItemStack item, Kind kind) {
        super(Thrown.MISSILE.get(), thrower, level);
        setItem(item.copyWithCount(1));
        entityData.set(KIND, (byte) kind.ordinal());
    }

    public ThrownMissile(Level level, double x, double y, double z, ItemStack item, Kind kind) {
        super(Thrown.MISSILE.get(), x, y, z, level);
        setItem(item.copyWithCount(1));
        entityData.set(KIND, (byte) kind.ordinal());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, (byte) 0);
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(entityData.get(KIND), Kind.values().length)];
    }

    /** LOTR's launch speed for this missile (EntityThrowable.func_70182_d). */
    public static float speed(Kind kind) {
        return Thrown.num(kind.key() + "_speed", switch (kind) {
            case FIRE_POT -> 1.2f;
            case PLATE -> 1.5f;
            case MYSTERY_WEB -> 0.5f;
            default -> 1f;
        });
    }

    @Override
    protected double getDefaultGravity() {
        return Thrown.num(kind().key() + "_gravity", switch (kind()) {
            case PLATE -> 0.02f;
            case MYSTERY_WEB -> 0.01f;
            default -> 0.04f;
        });
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBBLESTONE;
    }

    @Override
    public void tick() {
        super.tick();
        // LOTR's pebbles and plates skip off water when thrown flat and fast enough
        if ((kind() == Kind.PEBBLE || kind() == Kind.SLING_PEBBLE || kind() == Kind.PLATE) && isInWater()) {
            Vec3 v = getDeltaMovement();
            if (v.horizontalDistance() > 0.1 && v.y < 0) {
                float bounce = 0.4f + random.nextFloat() * 0.4f;
                setDeltaMovement(v.x * bounce, v.y + bounce, v.z * bounce);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        Entity owner = getOwner();
        float damage = switch (kind()) {
            case SLING_PEBBLE -> Thrown.num("sling_damage", 2f);
            case PEBBLE -> Thrown.num("pebble_damage", 1f);
            case CONKER -> Thrown.num("conker_damage", 1f);
            case PLATE -> Thrown.num("plate_damage", 1f);
            default -> 0f;
        };
        if (damage > 0) {
            hit.getEntity().hurt(damageSources().thrown(this, owner), damage);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity direct = hit instanceof EntityHitResult e ? e.getEntity() : null;
        switch (kind()) {
            case PEBBLE, SLING_PEBBLE, CONKER -> spawnAtLocation(getItem().copyWithCount(1));
            case FIRE_POT -> firePot(level, direct);
            case MYSTERY_WEB -> web(level);
            case PLATE -> plate(level, hit);
        }
        discard();
    }

    /** LOTREntityFirePot.onImpact. */
    private void firePot(ServerLevel level, Entity direct) {
        double range = Thrown.num("fire_pot_range", 3f);
        List<LivingEntity> hit = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(range)));
        if (direct instanceof LivingEntity living && !hit.contains(living)) {
            hit.add(living);
        }
        int lo = (int) Thrown.num("fire_pot_burn_min", 2);
        int hi = (int) Thrown.num("fire_pot_burn_max", 4);
        for (LivingEntity e : hit) {
            boolean direct2 = e == direct;
            float damage = direct2 ? Thrown.num("fire_pot_hit_damage", 3f) : Thrown.num("fire_pot_damage", 1f);
            if (e.hurt(damageSources().thrown(this, getOwner()), damage)) {
                int burn = lo + random.nextInt(hi - lo + 1);
                if (direct2) {
                    burn += lo + random.nextInt(hi - lo + 1);
                }
                e.igniteForSeconds(burn);
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1f,
                (random.nextFloat() - random.nextFloat()) * 0.2f + 1f);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 16, 0.25, 0.25, 0.25, 0.05);
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 16, 0.25, 0.25, 0.25, 0.05);
    }

    /** LOTREntityMysteryWeb.onImpact: one throw in four, a Mirkwood spider (LOTR's chest loot isn't ported). */
    private void web(ServerLevel level) {
        if (random.nextFloat() < Thrown.num("web_spider_chance", 0.25f)) {
            LotrNpc.spawn(level, LotrPort.id("mirkwood_spider"), getX(), getY(), getZ()).ifPresent(spider -> {
                if (getOwner() instanceof LivingEntity thrower) {
                    spider.setTarget(thrower);
                }
            });
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.2f,
                ((random.nextFloat() - random.nextFloat()) * 0.7f + 1f) * 2f);
    }

    /** LOTREntityPlate.onImpact: glass breaks (where banners allow), then the plate shatters. */
    private void plate(ServerLevel level, HitResult hit) {
        if (hit instanceof BlockHitResult b && breakGlass(level, b.getBlockPos())) {
            int r = (int) Thrown.num("plate_glass_range", 2);
            float chance = Thrown.num("plate_glass_chance", 0.75f);
            for (BlockPos p : BlockPos.betweenClosed(b.getBlockPos().offset(-r, -r, -r),
                    b.getBlockPos().offset(r, r, r))) {
                if (random.nextFloat() < chance) {
                    breakGlass(level, p.immutable());
                }
            }
        }
        BlockState shard = getItem().getItem() instanceof net.minecraft.world.item.BlockItem bi
                ? bi.getBlock().defaultBlockState() : Blocks.TERRACOTTA.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, shard), getX(), getY(), getZ(), 8, 0.25,
                0.25, 0.25, 0.05);
        level.playSound(null, getX(), getY(), getZ(), shard.getSoundType().getBreakSound(), SoundSource.NEUTRAL,
                1f, (random.nextFloat() - random.nextFloat()) * 0.2f + 1f);
    }

    private boolean breakGlass(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        boolean glass = s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES);
        if (!glass || !mayGrief(level, pos)) {
            return false;
        }
        level.destroyBlock(pos, false, this);
        return true;
    }

    private boolean mayGrief(ServerLevel level, BlockPos pos) {
        Entity owner = getOwner();
        return owner instanceof net.minecraft.world.entity.player.Player p ? p.mayBuild()
                && level.mayInteract(p, pos) : net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, this);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("MissileKind", entityData.get(KIND));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getByte("MissileKind"));
    }
}
