package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * LOTR's run-from-a-player AI (LOTREntityAINPCAvoidEvilPlayer, AIOrcAvoidGoodPlayer): when a player it fears is within
 * {@code range} ({@code range / 2} up or down), it runs to a spot up to 16 blocks off that is farther from the player,
 * at {@code speed}, or {@code nearSpeed} while the player is within 7.
 */
final class FleeGoal extends Goal {
    private final LotrNpc npc;
    private final Predicate<Player> fears;
    private final BooleanSupplier may;
    private final double range;
    private final double speed;
    private final double nearSpeed;
    private Player from;
    private Path path;

    FleeGoal(LotrNpc npc, Predicate<Player> fears, BooleanSupplier may, double range, double speed, double nearSpeed) {
        this.npc = npc;
        this.fears = fears;
        this.may = may;
        this.range = range;
        this.speed = speed;
        this.nearSpeed = nearSpeed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!may.getAsBoolean()) {
            return false;
        }
        List<Player> players = npc.level().getEntitiesOfClass(Player.class, npc.getBoundingBox().inflate(range, range / 2, range),
                p -> !p.isCreative() && !p.isSpectator() && p.isAlive() && fears.test(p));
        if (players.isEmpty()) {
            return false;
        }
        from = players.get(0);
        Vec3 away = DefaultRandomPos.getPosAway(npc, 16, 7, from.position());
        if (away == null || from.distanceToSqr(away) < from.distanceToSqr(npc)) {
            return false;
        }
        path = npc.getNavigation().createPath(away.x, away.y, away.z, 0);
        return path != null && path.canReach();
    }

    @Override
    public void start() {
        npc.getNavigation().moveTo(path, speed);
    }

    @Override
    public boolean canContinueToUse() {
        return !npc.getNavigation().isDone() && may.getAsBoolean();
    }

    @Override
    public void tick() {
        npc.getNavigation().setSpeedModifier(npc.distanceToSqr(from) < 49 ? nearSpeed : speed);
    }

    @Override
    public void stop() {
        from = null;
        path = null;
    }
}
