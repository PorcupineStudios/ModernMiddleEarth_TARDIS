package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * One piece of a LOTR creature's own behaviour (trolls turning to stone, spiders' venom, the Balrog's charge...),
 * attached to NPC types by their {@code behaviours} list. Numbers come from the type's {@code behaviour_data}, which
 * {@code converter/npcs.py} fills from LOTR's classes. All hooks run on the server.
 */
public interface CreatureBehaviour {
    /** A new NPC of the type: roll its looks and state. */
    default void init(LotrNpc npc, NpcType type) {}

    /** Adds AI goals (on spawn and on load). */
    default void addGoals(LotrNpc npc) {}

    default void tick(LotrNpc npc) {}

    /** Changes damage taken; 0 cancels it. */
    default float hurt(LotrNpc npc, DamageSource source, float amount) {
        return amount;
    }

    /** After a melee hit landed. */
    default void hit(LotrNpc npc, Entity target) {}

    /** A player's right-click; null lets the NPC's usual handling go on. */
    default InteractionResult interact(LotrNpc npc, ServerPlayer player, InteractionHand hand) {
        return null;
    }

    /** Whether it climbs walls like a spider. */
    default boolean climbs(LotrNpc npc) {
        return false;
    }

    static float data(NpcType type, String key, float fallback) {
        Float v = type.persona().behaviourData().get(key);
        return v == null ? fallback : v;
    }

    static float data(LotrNpc npc, String key, float fallback) {
        NpcType t = npc.npcType();
        return t == null ? fallback : data(t, key, fallback);
    }
}
