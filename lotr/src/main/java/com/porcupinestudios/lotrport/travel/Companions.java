package com.porcupinestudios.lotrport.travel;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.society.CoreComponents.Order;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;

/**
 * What travels with a player who fast travels or teleports by the map (as LOTR's fast travel took your mount and
 * followers): the mount they ride (they stay on it), animals on their lead, their tamed pets that aren't told to sit,
 * and their hired units that follow them. Anything else stays behind.
 */
public final class Companions {
    /** How far around the player companions are gathered from (blocks). */
    private static final double RADIUS = 24;

    private Companions() {}

    /** Takes the player and their companions to x, y, z in the player's level. */
    public static void teleport(ServerPlayer player, double x, double y, double z) {
        ServerLevel level = player.serverLevel();
        Entity mount = player.getVehicle() == null ? null : player.getRootVehicle();
        Set<Entity> others = new LinkedHashSet<>(gather(player, level));
        if (mount != null) {
            others.remove(mount);
            List<Entity> riders = new ArrayList<>(mount.getPassengers());
            player.stopRiding();
            mount.ejectPassengers();
            mount.teleportTo(x, y, z);
            player.teleportTo(level, x, y, z, Set.of(), player.getYRot(), player.getXRot());
            for (Entity rider : riders) {
                if (rider != player) {
                    rider.teleportTo(x, y, z);
                }
                rider.startRiding(mount, true);
            }
        } else {
            player.teleportTo(level, x, y, z, Set.of(), player.getYRot(), player.getXRot());
        }
        int n = 0;
        for (Entity e : others) {
            double a = n++ * 2.4; // around the player, so they don't stand in one block
            e.teleportTo(x + Math.cos(a) * 1.5, y, z + Math.sin(a) * 1.5);
            if (e instanceof Mob mob) {
                mob.getNavigation().stop();
            }
        }
    }

    static List<Entity> gather(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(RADIUS);
        List<Entity> out = new ArrayList<>();
        for (Entity e : level.getEntities(player, box, e -> e.isAlive() && !e.isPassenger())) {
            if (e instanceof Mob mob && mob.getLeashHolder() == player) {
                out.add(e);
            } else if (e instanceof TamableAnimal pet && pet.isOwnedBy(player) && !pet.isOrderedToSit()) {
                out.add(e);
            } else if (e instanceof LotrNpc npc && npc.isOwnedBy(player) && npc.command() == Order.FOLLOW) {
                out.add(e);
            } else if (e instanceof OwnableEntity own && !(e instanceof TamableAnimal) && !(e instanceof LotrNpc)
                    && player.getUUID().equals(own.getOwnerUUID()) && e instanceof Mob m && m.isLeashed()) {
                out.add(e);
            }
        }
        return out;
    }
}
