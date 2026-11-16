package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;

/**
 * LOTR's scorpions (LOTREntityScorpion): each is one of three sizes (0 to {@code size_max} 2) scaling it by
 * 0.5 + size / 2, with {@code health_base} 12 + {@code health_per_size} 6 health and {@code attack_base} 2 +
 * {@code attack_per_size} 1 damage per size. A player milks one with a glass bottle for a bottle of poison. Its sting
 * (poison) is the {@code on_hit} behaviour.
 */
public class ScorpionBehaviour implements CreatureBehaviour {
    private static final ResourceLocation POISON = ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "bottle_of_poison");

    @Override
    public void init(LotrNpc npc, NpcType type) {
        int size = npc.random().nextInt((int) CreatureBehaviour.data(type, "size_max", 2) + 1);
        float scale = 0.5f + size / 2f;
        npc.setState("getScorpionScale", size);
        npc.setState("getScorpionScaleAmount", scale);
        npc.setBodySize(type.persona().width() * scale, type.persona().height() * scale);
        float health = CreatureBehaviour.data(type, "health_base", 12) + size * CreatureBehaviour.data(type, "health_per_size", 6);
        npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        npc.setHealth(health);
        npc.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(
                CreatureBehaviour.data(type, "attack_base", 2) + size * CreatureBehaviour.data(type, "attack_per_size", 1));
    }

    @Override
    public InteractionResult interact(LotrNpc npc, ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.GLASS_BOTTLE)) {
            return null;
        }
        ItemStack poison = new ItemStack(BuiltInRegistries.ITEM.get(POISON));
        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, poison));
        npc.playSound(SoundEvents.BOTTLE_FILL, 1f, 1f);
        return InteractionResult.SUCCESS;
    }
}
