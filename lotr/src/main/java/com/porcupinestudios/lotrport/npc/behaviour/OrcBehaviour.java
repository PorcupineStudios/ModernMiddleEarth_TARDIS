package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcSpawns;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * LOTR's orcs (LOTREntityOrc). Weak orcs ({@code weak_orc}: not Uruks, Black Uruks, captains, Utumno orcs...) under
 * open sky by day get weakness and slowness for {@code daylight_ticks} (200), renewed every second, except in biomes
 * where hostiles walk by day (Mordor...). Weak orcs other than Mordor's ({@code avoid_good}) run from players at
 * {@code avoid_alignment} (-500) or worse within {@code avoid_range} (8), unless they or a friendly NPC near them is
 * already fighting a player (LOTREntityAIOrcAvoidGoodPlayer).
 * <p>LOTR's weakness was level -1, which in 1.7.10 took no strength away; here it is weakness I.
 */
public class OrcBehaviour implements CreatureBehaviour {
    @Override
    public void addGoals(LotrNpc npc) {
        if (CreatureBehaviour.data(npc, "avoid_good", 0) > 0) {
            // above the melee goal: LOTR's orcs flee before they fight
            npc.goals().addGoal(0, new FleeGoal(npc,
                    p -> npc.faction().map(f -> Alignment.get(p, f)).orElse(0f)
                            <= CreatureBehaviour.data(npc, "avoid_alignment", -500) && p != revenge(npc),
                    () -> npc.owner().isEmpty() && revenge(npc) == null && !alliesFightingPlayers(npc),
                    CreatureBehaviour.data(npc, "avoid_range", 8), CreatureBehaviour.data(npc, "avoid_speed", 1.5f),
                    CreatureBehaviour.data(npc, "avoid_speed", 1.5f)));
        }
    }

    /** LOTR's currentRevengeTarget: whoever hurt it, while it is still fighting. */
    private static Object revenge(LotrNpc npc) {
        return npc.getTarget() != null && npc.getTarget() == npc.getLastHurtByMob() ? npc.getTarget() : null;
    }

    private static boolean alliesFightingPlayers(LotrNpc npc) {
        double r = CreatureBehaviour.data(npc, "avoid_range", 8);
        return !npc.level().getEntitiesOfClass(LotrNpc.class, npc.getBoundingBox().inflate(r, r / 2, r),
                n -> n != npc && n.getTarget() instanceof Player && n.faction().isPresent() && npc.faction().isPresent()
                        && (n.faction().equals(npc.faction()) || Factions.relation(npc.getServer(),
                                npc.faction().get(), n.faction().get()).isGood())).isEmpty();
    }

    @Override
    public void tick(LotrNpc npc) {
        if (npc.tickCount % 20 != 0 || CreatureBehaviour.data(npc, "weak_orc", 0) <= 0) {
            return;
        }
        var level = npc.level();
        if (!level.isDay() || !level.canSeeSky(npc.blockPosition())) {
            return;
        }
        boolean darkBiome = level.getBiome(npc.blockPosition()).unwrapKey()
                .flatMap(k -> NpcSpawns.forBiome(k.location())).map(NpcSpawns.Table::hostilesInDay).orElse(false);
        if (!darkBiome) {
            int t = (int) CreatureBehaviour.data(npc, "daylight_ticks", 200);
            npc.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t));
            npc.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t));
        }
    }
}
