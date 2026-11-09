package com.porcupinestudios.lotrport.npc;

import com.porcupinestudios.lotrport.content.GateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Opens a shut gate on the NPC's path and shuts it again once through: vanilla's OpenDoorGoal knows only doors.
 * {@link GateBlock} tells the pathfinder a shut gate is a door, so NPCs (whose navigation opens doors) route through.
 */
public final class OpenGateGoal extends Goal {
    private final Mob mob;
    private BlockPos gate;
    private int ticks;

    public OpenGateGoal(Mob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int next = path.getNextNodeIndex();
        for (int i = Math.max(0, next - 1); i < Math.min(next + 2, path.getNodeCount()); i++) {
            BlockPos node = path.getNodePos(i);
            for (BlockPos p : new BlockPos[] {node, node.above()}) {
                BlockState s = mob.level().getBlockState(p);
                if (s.getBlock() instanceof GateBlock && !s.getValue(GateBlock.OPEN)
                        && mob.distanceToSqr(Vec3.atBottomCenterOf(p)) < 6.25) {
                    gate = p;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void start() {
        ticks = 0;
        GateBlock.setOpen(mob.level(), gate, true);
    }

    /** Until it is through: at least a second, then while still within 2.5 blocks, at most 10 s. */
    @Override
    public boolean canContinueToUse() {
        return ticks < 200 && (ticks < 20 || mob.distanceToSqr(Vec3.atCenterOf(gate)) < 6.25);
    }

    @Override
    public void tick() {
        ticks++;
    }

    /** Shuts the gate behind it, unless someone is standing in it (a shut gate would trap them). */
    @Override
    public void stop() {
        Level level = mob.level();
        BlockState s = level.getBlockState(gate);
        if (s.getBlock() instanceof GateBlock && s.getValue(GateBlock.OPEN) && GateBlock.gate(level, gate, s).stream()
                .allMatch(p -> level.getEntitiesOfClass(LivingEntity.class, new AABB(p)).isEmpty())) {
            GateBlock.setOpen(level, gate, false);
        }
        gate = null;
    }
}
