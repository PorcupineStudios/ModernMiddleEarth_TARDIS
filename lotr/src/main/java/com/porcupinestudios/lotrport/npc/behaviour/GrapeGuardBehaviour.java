package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.content.farm.GrapevineBlock;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * LOTR's Dorwinion guards (LOTREntityDorwinionGuard, GrapeAlert): a player who breaks a grapevine within
 * {@code range} (32) blocks of a guard that sees it is attacked by every such guard, unless hired by that player.
 * The guard stays on alert {@code alert_ticks} (600).
 */
public class GrapeGuardBehaviour implements CreatureBehaviour {
    private static final String ALERT = "grape.alert";

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GrapeGuardBehaviour::onBreak);
    }

    @Override
    public void tick(LotrNpc npc) {
        float a = npc.state(ALERT);
        if (a > 0) {
            npc.setState(ALERT, a - 1);
        }
    }

    /** LOTREntityDorwinionGuard.onDeath: a guard on alert killed by a player gives stealDorwinionGrapes. */
    @Override
    public void died(LotrNpc npc, net.minecraft.world.damagesource.DamageSource source) {
        if (npc.state(ALERT) > 0 && source.getEntity() instanceof ServerPlayer p) {
            com.porcupinestudios.lotrport.progress.Achievements.grant(p, "stealDorwinionGrapes");
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof GrapevineBlock) || !(event.getPlayer() instanceof ServerPlayer p)
                || p.isCreative()) {
            return;
        }
        double r = 32;
        for (LotrNpc npc : p.level().getEntitiesOfClass(LotrNpc.class, new AABB(event.getPos()).inflate(r))) {
            boolean guard = npc.behaviours().stream().anyMatch(b -> b instanceof GrapeGuardBehaviour);
            if (guard && npc.owner().map(p.getUUID()::equals).orElse(false) == false && npc.hasLineOfSight(p)) {
                npc.setTarget(p);
                npc.setState(ALERT, CreatureBehaviour.data(npc, "alert_ticks", 600f));
            }
        }
    }
}
