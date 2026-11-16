package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.EnumSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * LOTR's NPCs eat and drink now and then (LOTREntityAIEat, AIDrink on LOTREntityAIConsumeBase): out of a fight, 1 in
 * {@code eat_chance} / {@code drink_chance} a tick (four times as often when hurt) a grown NPC holds food or a drink for
 * 32 ticks, munching or gulping every 4, then eating heals it by the food's nourishment.
 * <p>shortcut: LOTR's per-culture food and drink lists (LOTRFoods) are bread or maggoty bread and a mug of ale, and
 * drinks don't make NPCs drunk; upgrade when the exporter dumps LOTRFoods.
 */
public class EatDrinkBehaviour implements CreatureBehaviour {
    private static final int TICKS = 32;

    @Override
    public void addGoals(LotrNpc npc) {
        if (CreatureBehaviour.data(npc, "eat_chance", 0) > 0) {
            npc.goals().addGoal(5, new ConsumeGoal(npc, "eat_chance", true));
        }
        if (CreatureBehaviour.data(npc, "drink_chance", 0) > 0) {
            npc.goals().addGoal(5, new ConsumeGoal(npc, "drink_chance", false));
        }
    }

    private static ItemStack food(LotrNpc npc, boolean eat) {
        if (!eat) {
            return new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("ale")));
        }
        return "orc".equals(npc.model()) ? new ItemStack(BuiltInRegistries.ITEM.get(LotrPort.id("maggoty_bread")))
                : new ItemStack(Items.BREAD);
    }

    private static final class ConsumeGoal extends Goal {
        private final LotrNpc npc;
        private final String chanceKey;
        private final boolean eat;
        private ItemStack held = ItemStack.EMPTY;
        private int ticks;

        ConsumeGoal(LotrNpc npc, String chanceKey, boolean eat) {
            this.npc = npc;
            this.chanceKey = chanceKey;
            this.eat = eat;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (npc.isBaby() || npc.getTarget() != null) {
                return false;
            }
            int chance = (int) CreatureBehaviour.data(npc, chanceKey, 8000);
            boolean hurt = npc.getHealth() < npc.getMaxHealth();
            return hurt && npc.random().nextInt(Math.max(1, chance / 4)) == 0 || npc.random().nextInt(chance) == 0;
        }

        @Override
        public void start() {
            held = npc.getItemBySlot(EquipmentSlot.MAINHAND);
            npc.setItemSlot(EquipmentSlot.MAINHAND, food(npc, eat));
            ticks = TICKS;
            npc.getNavigation().stop();
        }

        @Override
        public boolean canContinueToUse() {
            return ticks > 0 && npc.getTarget() == null;
        }

        @Override
        public void tick() {
            ticks--;
            ItemStack stack = npc.getMainHandItem();
            if (ticks % 4 == 0) {
                npc.playSound(eat ? SoundEvents.GENERIC_EAT : SoundEvents.GENERIC_DRINK, 0.5f + 0.5f * npc.random().nextInt(2),
                        (npc.random().nextFloat() - npc.random().nextFloat()) * 0.2f + 1f);
                if (eat && npc.level() instanceof ServerLevel level) {
                    level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack), npc.getX(), npc.getEyeY() - 0.2,
                            npc.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
                }
            }
            if (ticks == 0 && eat) {
                var food = stack.get(DataComponents.FOOD);
                if (food != null) {
                    npc.heal(food.nutrition());
                }
            }
        }

        @Override
        public void stop() {
            npc.setItemSlot(EquipmentSlot.MAINHAND, held);
            held = ItemStack.EMPTY;
            ticks = 0;
        }
    }
}
