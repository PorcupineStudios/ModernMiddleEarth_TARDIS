package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;

/**
 * LOTR's thieving birds (LOTREntityBird): 1 in 100 a tick a bird with room in its crop ({@code steal_slots}, 9
 * stacks) goes for a player within 16 holding something it wants in the selected hotbar slot, or else a dropped item
 * it wants, and takes 1-4 of it when it gets there. What it wants is {@code steal_kind}: 0 seeds (common birds), 1
 * food and bones (crows), 2 valuables (magpies: coins, gems, ingots). Otherwise, with mob griefing on, 1 in 100 a tick
 * it goes to eat a crop or a bush's berries within 16 (8 up or down) that no scarecrow guards. It gives up after 400
 * ticks, and drops what it stole when it dies.
 * <p>Birds fly there the way LOTR's did: each tick their motion eases ({@code movementSpeed}) towards half a block a
 * tick across and 0.8 up or down, towards the target.
 */
public class BirdThiefBehaviour implements CreatureBehaviour {
    private static final String STOLEN = "lotr_port.stolen";
    /** Squared reach: LOTR flew to within a block; walking birds stop a little short of their target. */
    private static final double REACH = 4;

    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(3, new ThiefGoal(npc));
    }

    @Override
    public void died(LotrNpc npc, DamageSource source) {
        for (Tag t : npc.getPersistentData().getList(STOLEN, Tag.TAG_COMPOUND)) {
            ItemStack.parse(npc.registryAccess(), t).ifPresent(npc::spawnAtLocation);
        }
        npc.getPersistentData().remove(STOLEN);
    }

    public static boolean wants(LotrNpc npc, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch ((int) CreatureBehaviour.data(npc, "steal_kind", 0)) {
            case 1 -> stack.has(DataComponents.FOOD) || stack.is(Tags.Items.BONES);
            // shortcut: LOTR's coins and the repair items of iron-or-better tools; coins are matched by name
            case 2 -> stack.is(Tags.Items.GEMS) || stack.is(Tags.Items.INGOTS)
                    || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_coin");
            default -> stack.is(Tags.Items.SEEDS);
        };
    }

    /** LOTR's bird flight (LOTREntityBird.updateAITasks): motion eased towards the target, the body turned along it. */
    private static void fly(LotrNpc npc, double x, double y, double z) {
        if (npc.onGround() && !npc.isNoGravity()) {
            // shortcut: a bird standing on the ground walks (the port's birds have no flight model)
            if (npc.tickCount % 10 == 0 || npc.getNavigation().isDone()) {
                npc.getNavigation().moveTo(x, y, z, 1.2);
            }
            return;
        }
        double speed = npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        var m = npc.getDeltaMovement();
        double mx = m.x + (Math.signum(x - npc.getX()) * 0.5 - m.x) * speed;
        double my = m.y + (Math.signum(y - npc.getY()) * 0.8 - m.y) * speed;
        double mz = m.z + (Math.signum(z - npc.getZ()) * 0.5 - m.z) * speed;
        npc.setDeltaMovement(mx, my, mz);
        npc.setYRot((float) (Math.atan2(mz, mx) * 180 / Math.PI) - 90);
        npc.setYBodyRot(npc.getYRot());
    }

    private static ListTag stolen(LotrNpc npc) {
        CompoundTag data = npc.getPersistentData();
        if (!data.contains(STOLEN)) {
            data.put(STOLEN, new ListTag());
        }
        return data.getList(STOLEN, Tag.TAG_COMPOUND);
    }

    private static final class ThiefGoal extends Goal {
        private final LotrNpc npc;
        private Entity target; // a player or an item
        private BlockPos crop;
        private int time;

        ThiefGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (npc.random().nextInt(100) != 0 || npc.getTarget() != null) {
                return false;
            }
            target = null;
            crop = null;
            if (stolen(npc).size() < CreatureBehaviour.data(npc, "steal_slots", 9)) {
                List<Player> players = npc.level().getEntitiesOfClass(Player.class, npc.getBoundingBox().inflate(16),
                        p -> !p.isCreative() && !p.isSpectator() && p.isAlive() && wants(npc, p.getInventory().getSelected()));
                if (!players.isEmpty()) {
                    target = players.get(npc.random().nextInt(players.size()));
                    return true;
                }
                List<ItemEntity> items = npc.level().getEntitiesOfClass(ItemEntity.class, npc.getBoundingBox().inflate(16),
                        i -> i.isAlive() && wants(npc, i.getItem()));
                if (!items.isEmpty()) {
                    target = items.get(npc.random().nextInt(items.size()));
                    return true;
                }
            }
            if (Crops.canGrief(npc.level())) {
                crop = Crops.find(npc, 16, 8, true, p -> !Crops.scarecrowNear(npc.level(), p));
            }
            return crop != null;
        }

        @Override
        public void start() {
            time = 0;
        }

        @Override
        public boolean canContinueToUse() {
            if (time >= 400) {
                return false;
            }
            if (crop != null) {
                return Crops.canGrief(npc.level()) && Crops.edible(npc.level(), crop, true);
            }
            return target.isAlive() && (target instanceof ItemEntity i ? wants(npc, i.getItem())
                    : wants(npc, ((Player) target).getInventory().getSelected()));
        }

        @Override
        public void stop() {
            target = null;
            crop = null;
        }

        @Override
        public void tick() {
            time++;
            if (crop != null) {
                if (npc.distanceToSqr(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5) < REACH) {
                    Crops.eat(npc.level(), crop);
                    npc.playSound(SoundEvents.GENERIC_EAT, 1f, 1f);
                    crop = null;
                    time = 400;
                } else {
                    if (time % 100 == 1 && Crops.scarecrowNear(npc.level(), crop)) {
                        time = 400;
                        return;
                    }
                    fly(npc, crop.getX() + 0.5, crop.getY() + 0.5, crop.getZ() + 0.5);
                }
                return;
            }
            if (npc.distanceToSqr(target) < REACH) {
                ItemStack from = target instanceof ItemEntity i ? i.getItem() : ((Player) target).getInventory().getSelected();
                int n = 1 + npc.random().nextInt(Math.min(from.getCount(), 4));
                stolen(npc).add(from.split(n).save(npc.registryAccess()));
                if (target instanceof ItemEntity i && from.isEmpty()) {
                    i.discard();
                }
                npc.playSound(SoundEvents.ITEM_PICKUP, 0.5f, 2f);
                time = 400;
            } else {
                fly(npc, target.getX(), target.getY() + 0.5, target.getZ());
            }
        }
    }
}
