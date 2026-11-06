package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;

/**
 * Ents and huorns are trees (LOTREntityTree.doTreeDamageCalculation): axes and fire hurt them
 * {@code tree_weakness} (x2) as much, other blows {@code tree_resistance} (x0.5).
 */
public class TreeBehaviour implements CreatureBehaviour {
    @Override
    public float hurt(LotrNpc npc, DamageSource source, float amount) {
        boolean effective = source.is(DamageTypeTags.IS_FIRE)
                || source.getEntity() instanceof LivingEntity e && e.getMainHandItem().getItem() instanceof AxeItem;
        return amount * (effective ? CreatureBehaviour.data(npc, "tree_weakness", 2f)
                : CreatureBehaviour.data(npc, "tree_resistance", 0.5f));
    }
}
