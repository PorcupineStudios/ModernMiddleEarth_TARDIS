package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.Coins;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.progress.Achievements;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * LOTR's thieves (IBandit: bandits, ruffian spies; LOTREntityAIBanditSteal and BanditFlee). With empty pockets a thief
 * picks a player within {@code steal_range} (32) who has anything outside their held slot, runs at them
 * ({@code steal_speed}) for up to {@code chase_ticks} (600) and, once close, makes 1 to {@code max_thefts} thefts of
 * 1-{@code steal_amount} (8) from a random slot, preferring coins, then gems and ingots, rings, armour, swords, tools,
 * pouches, then anything. Then it runs from the nearest player within {@code flee_range} ({@code flee_speed}) and
 * fights no one; it drops what it stole when it dies. A bandit only fights players it can't rob; a ruffian spy only
 * robs a player it is fighting or one who is drunk (nausea), and its message is the ruffian's.
 */
public class BanditBehaviour implements CreatureBehaviour {
    private static final String LOOT = "lotr_port.bandit_loot";
    /** LOTR's order of what a thief looks for; coins are tried every theft besides one of these. */
    private static final List<Predicate<ItemStack>> WANTS = List.of(
            s -> s.is(Tags.Items.GEMS),
            // shortcut: LOTRValuableItems' tool materials as every ingot, until a tag lists them
            s -> s.is(Tags.Items.INGOTS),
            s -> path(s).endsWith("_ring"),
            s -> s.getItem() instanceof ArmorItem,
            s -> s.getItem() instanceof SwordItem,
            s -> s.getItem() instanceof DiggerItem,
            s -> path(s).contains("pouch"),
            s -> true);
    private Player robbing; // the ruffian spy's playerToRob

    private static String path(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
    }

    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(0, new StealGoal(npc));
        npc.goals().addGoal(0, new FleeWithLootGoal(npc));
    }

    @Override
    public void tick(LotrNpc npc) {
        if (npc.getTarget() instanceof Player p) {
            robbing = p;
            // LOTREntityAINearestAttackableTargetBandit: carrying loot, or facing someone it could rob, it doesn't fight
            boolean spy = CreatureBehaviour.data(npc, "steal_needs_nausea", 0) > 0;
            boolean fights = loot(npc).isEmpty() && (spy || !canRob(p));
            if (!fights && p != npc.getLastHurtByMob()) {
                npc.setTarget(null);
            }
        }
    }

    @Override
    public void died(LotrNpc npc, DamageSource source) {
        ListTag loot = loot(npc);
        if (!loot.isEmpty() && source.getEntity() instanceof ServerPlayer p
                && CreatureBehaviour.data(npc, "loot_achievement", 0) > 0) {
            Achievements.grant(p, "killThievingBandit");
        }
        for (Tag t : loot) {
            ItemStack.parse(npc.registryAccess(), t).ifPresent(npc::spawnAtLocation);
        }
        npc.getPersistentData().remove(LOOT);
    }

    public static ListTag loot(LotrNpc npc) {
        if (!npc.getPersistentData().contains(LOOT)) {
            npc.getPersistentData().put(LOOT, new ListTag());
        }
        return npc.getPersistentData().getList(LOOT, Tag.TAG_COMPOUND);
    }

    /** Anything outside the held slot (IBandit.Helper.canStealFromPlayerInv). */
    private static boolean canRob(Player p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            if (i != inv.selected && !inv.items.get(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** LOTREntityBreeRuffian.canRuffianTarget: more likely the longer the player's nausea lasts (20-120 s). */
    private boolean mayRob(LotrNpc npc, Player p) {
        if (CreatureBehaviour.data(npc, "steal_needs_nausea", 0) <= 0 || p == robbing) {
            return true;
        }
        MobEffectInstance nausea = p.getEffect(MobEffects.CONFUSION);
        return nausea != null && npc.random().nextFloat() < (nausea.getDuration() / 20 - 20) / 100f * 0.05f;
    }

    private static boolean stealOne(LotrNpc npc, Inventory inv, Predicate<ItemStack> wanted) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.items.size(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, new java.util.Random(npc.random().nextLong()));
        ListTag loot = loot(npc);
        for (int slot : slots) {
            ItemStack s = inv.items.get(slot);
            if (slot == inv.selected || s.isEmpty() || !wanted.test(s)) {
                continue;
            }
            if (loot.size() >= CreatureBehaviour.data(npc, "max_thefts", 3)) {
                return false;
            }
            int n = Math.min(s.getCount(), 1 + npc.random().nextInt((int) CreatureBehaviour.data(npc, "steal_amount", 8)));
            loot.add(s.split(n).save(npc.registryAccess()));
            npc.setPersistenceRequired();
            return true;
        }
        return false;
    }

    private final class StealGoal extends Goal {
        private final LotrNpc npc;
        private Player target;
        private Player last;
        private int chase;
        private int repath;

        StealGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!loot(npc).isEmpty()) {
                return false;
            }
            List<Player> players = npc.level().getEntitiesOfClass(Player.class,
                    npc.getBoundingBox().inflate(CreatureBehaviour.data(npc, "steal_range", 32)),
                    p -> !p.isCreative() && !p.isSpectator() && p.isAlive() && canRob(p) && mayRob(npc, p));
            if (players.isEmpty()) {
                return false;
            }
            target = players.get(npc.random().nextInt(players.size()));
            if (target != last && target instanceof ServerPlayer sp && npc.npcType() != null) {
                npc.npcType().speech().ifPresent(bank -> npc.speak(sp, bank, List.of("hostile", "neutral")));
            }
            return true;
        }

        @Override
        public void start() {
            chase = (int) CreatureBehaviour.data(npc, "chase_ticks", 600);
            repath = 0;
        }

        @Override
        public boolean canContinueToUse() {
            return target.isAlive() && !target.isCreative() && canRob(target) && chase > 0 && npc.distanceToSqr(target) < 256;
        }

        @Override
        public void stop() {
            last = target;
            npc.getNavigation().stop();
        }

        @Override
        public void tick() {
            chase--;
            npc.getLookControl().setLookAt(target, 30, 30);
            if (--repath <= 0) {
                repath = 10;
                npc.getNavigation().moveTo(target, CreatureBehaviour.data(npc, "steal_speed", 1.8f));
            }
            if (npc.distanceToSqr(target) <= CreatureBehaviour.data(npc, "steal_reach", 2)) {
                chase = 0;
                steal();
            }
        }

        private void steal() {
            Inventory inv = target.getInventory();
            int thefts = 1 + npc.random().nextInt((int) CreatureBehaviour.data(npc, "max_thefts", 3));
            boolean stole = false;
            for (int i = 0; i < thefts; i++) {
                stole |= stealOne(npc, inv, s -> Coins.value(s) > 0);
                for (Predicate<ItemStack> w : WANTS) {
                    if (stealOne(npc, inv, w)) {
                        stole = true;
                        break;
                    }
                }
            }
            if (stole) {
                boolean ruffian = CreatureBehaviour.data(npc, "steal_msg", 0) > 0;
                target.sendSystemMessage(ruffian
                        ? Component.translatableWithFallback("chat.lotr_port.ruffian_steal",
                                "A ruffian stole some items from your inventory!")
                        : Component.translatableWithFallback("chat.lotr_port.bandit_steal",
                                "A bandit stole some items from your inventory!"));
                npc.playSound(SoundEvents.HORSE_SADDLE, 0.5f, 1f);
                npc.setTarget(null);
            }
        }
    }

    private static final class FleeWithLootGoal extends Goal {
        private final LotrNpc npc;
        private Player from;

        FleeWithLootGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private double range() {
            return CreatureBehaviour.data(npc, "flee_range", (float) npc.getAttributeValue(Attributes.FOLLOW_RANGE));
        }

        private Player nearest() {
            return npc.level().getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(), range(),
                    e -> e instanceof Player p && !p.isCreative() && !p.isSpectator());
        }

        @Override
        public boolean canUse() {
            if (npc.getTarget() != null || loot(npc).isEmpty()) {
                return false;
            }
            from = nearest();
            return from != null;
        }

        @Override
        public boolean canContinueToUse() {
            return from != null && from.isAlive() && !from.isCreative() && npc.getTarget() == null
                    && npc.distanceToSqr(from) < range() * range();
        }

        @Override
        public void tick() {
            if (npc.getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(npc, (int) range(), 10, from.position());
                if (away != null) {
                    npc.getNavigation().moveTo(away.x, away.y, away.z, CreatureBehaviour.data(npc, "flee_speed", 1.5f));
                }
                from = nearest();
            }
        }

        @Override
        public void stop() {
            from = null;
        }
    }
}
