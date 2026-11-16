package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.world.entity.player.Player;

/**
 * LOTR's elves bow (LOTREntityElf): out of a fight, with a player within {@code bow_range} (8) who has positive
 * alignment with their faction, an elf stops, turns to the player and bows for {@code bow_ticks} (40), once per
 * meeting: it bows again only after every such player has gone. State {@code bowingTick} (0..40 bowing, -1 done) is
 * synced for the renderer.
 * <p>LOTR bowed only to players in its BOWING_ELVES perk group; here any friendly player.
 */
public class ElfBowBehaviour implements CreatureBehaviour {
    private static final String TICK = "bowingTick";

    @Override
    public void tick(LotrNpc npc) {
        double r = CreatureBehaviour.data(npc, "bow_range", 8);
        Player friend = npc.getTarget() != null ? null : npc.level().getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(), r,
                e -> e instanceof Player p && p.isAlive() && !p.isSpectator()
                        && npc.faction().map(f -> Alignment.get(p, f) > 0).orElse(false));
        if (friend == null) {
            npc.setState(TICK, 0);
            return;
        }
        int tick = (int) npc.state(TICK);
        if (tick >= 0) {
            tick++;
        }
        if (tick > CreatureBehaviour.data(npc, "bow_ticks", 40)) {
            tick = -1;
        }
        npc.setState(TICK, tick);
        if (tick >= 0) {
            npc.getNavigation().stop();
            float yaw = (float) Math.toDegrees(Math.atan2(friend.getZ() - npc.getZ(), friend.getX() - npc.getX())) - 90;
            npc.setYRot(yaw);
            npc.setYHeadRot(yaw);
            npc.setYBodyRot(yaw);
        }
    }
}
