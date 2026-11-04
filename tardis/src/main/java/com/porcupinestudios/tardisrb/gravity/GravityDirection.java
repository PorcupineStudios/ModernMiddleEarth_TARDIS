package com.porcupinestudios.tardisrb.gravity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.porcupinestudios.tardisrb.gravity.gc.EntityTags;
import com.porcupinestudios.tardisrb.gravity.gc.GravityHolder;
import com.porcupinestudios.tardisrb.gravity.gc.RotationAnimation;
import com.porcupinestudios.tardisrb.gravity.gc.api.GravityChangerAPI;
import com.porcupinestudios.tardisrb.gravity.gc.mixin.EntityAccessor;
import com.porcupinestudios.tardisrb.gravity.gc.util.RotationUtil;
import com.porcupinestudios.tardisrb.net.SkyNet;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Which way gravity pulls an entity: one of six directions, as in Gravity Changer (the reference Aaron picked,
 * 2026-11-04: "i want to be able to change direction of gravity with commands"). The physics, camera and rendering
 * come from Gravity Changer's mixins, ported in {@code gravity/gc} (see its NOTICE); this class sets the direction,
 * syncs it to clients and adds {@code /gravity direction}.
 * <p>
 * The direction lives in a field on every entity (saved with it, not after death). While it isn't down, the entity's
 * delta movement is in its own frame: use {@link GravityChangerAPI#getWorldVelocity} for world velocity.
 */
public final class GravityDirection {
    /** How long the view takes to turn to a new gravity, in milliseconds, unless the command gives one. */
    public static final int DEFAULT_TURN_MS = 500;

    private GravityDirection() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> commands(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getEntity() instanceof ServerPlayer p && of(e.getTarget()) != Direction.DOWN) {
                PacketDistributor.sendToPlayer(p, packet(e.getTarget(), 0));
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> sendOwn(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> sendOwn(e.getEntity()));
    }

    public static Direction of(Entity e) {
        return GravityChangerAPI.getGravityDirection(e);
    }

    /** Whether this kind of entity can have its gravity turned (living things, projectiles, minecarts). */
    public static boolean canChange(Entity e) {
        return EntityTags.canChangeGravity(e);
    }

    public static boolean set(Entity e, Direction direction) {
        return set(e, direction, DEFAULT_TURN_MS);
    }

    /** Server side: turns the entity's gravity and tells the clients that see it. False if it can't change. */
    public static boolean set(Entity e, Direction direction, int turnMs) {
        if (!canChange(e)) {
            return false;
        }
        Direction old = of(e);
        if (old == direction) {
            return true;
        }
        apply(e, old, direction, turnMs);
        if (!e.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, packet(e, turnMs));
        }
        return true;
    }

    private static SkyNet.GravityDir packet(Entity e, int turnMs) {
        return new SkyNet.GravityDir(e.getId(), of(e).get3DDataValue(), turnMs);
    }

    private static void sendOwn(Player player) {
        if (player instanceof ServerPlayer p) {
            PacketDistributor.sendToPlayer(p, packet(p, 0));
        }
    }

    /** Client side: a direction the server sent. */
    public static void receive(Level level, SkyNet.GravityDir packet) {
        Entity e = level.getEntity(packet.entity());
        if (e != null) {
            Direction now = Direction.from3DDataValue(packet.direction());
            Direction old = of(e);
            if (old != now) {
                apply(e, old, now, packet.turnMs());
            }
        }
    }

    /**
     * Turns the entity round the new gravity (Gravity Changer's {@code applyGravityDirectionChange}): keeps its world
     * velocity, flips about the middle of its body when gravity reverses, steps out of blocks it would end up in, and
     * on the client starts the view turning.
     */
    static void apply(Entity e, Direction old, Direction now, int turnMs) {
        Vec3 worldVelocity = RotationUtil.vecPlayerToWorld(e.getDeltaMovement(), old);
        ((GravityHolder) e).tardis_rb$setGravityField(now);
        e.setBoundingBox(((EntityAccessor) e).gc_makeBoundingBox());
        e.fallDistance = 0;

        EntityDimensions dims = e.getDimensions(e.getPose());
        Vec3 center = now.getOpposite() == old ? new Vec3(0, dims.height() / 2, 0) : Vec3.ZERO;
        Vec3 oldPos = e.position();
        Vec3 pivot = oldPos.add(RotationUtil.vecPlayerToWorld(center, old));
        Vec3 newPos = pivot.subtract(RotationUtil.vecPlayerToWorld(center, now));
        Vec3 shift = newPos.subtract(oldPos);
        e.setPos(newPos);
        e.xo += shift.x;
        e.yo += shift.y;
        e.zo += shift.z;
        e.xOld += shift.x;
        e.yOld += shift.y;
        e.zOld += shift.z;
        stepOut(e, old.getOpposite());

        if (e.level().isClientSide()) {
            RotationAnimation animation = ((GravityHolder) e).tardis_rb$animation();
            if (animation != null) {
                animation.startRotationAnimation(now, old, turnMs, e, e.level().getGameTime() * 50, true, center);
            }
        }
        e.setDeltaMovement(RotationUtil.vecWorldToPlayer(worldVelocity, now));
    }

    /** Moves the entity along {@code moving} until its box clears the blocks it overlaps. */
    private static void stepOut(Entity e, Direction moving) {
        AABB box = e.getBoundingBox();
        AABB blocks = null;
        for (VoxelShape shape : e.level().getCollisions(e, box.inflate(-0.01))) {
            if (!shape.isEmpty()) {
                blocks = blocks == null ? shape.bounds() : blocks.minmax(shape.bounds());
            }
        }
        if (blocks == null) {
            return;
        }
        Direction.Axis axis = moving.getAxis();
        double offset = moving.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? Math.max(0, blocks.max(axis) - box.min(axis))
                : Math.max(0, box.max(axis) - blocks.min(axis));
        if (offset > 0) {
            e.setPos(e.position().add(new Vec3(moving.step()).scale(offset)));
        }
    }

    private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        String[] names = Arrays.stream(Direction.values()).map(Direction::getName).toArray(String[]::new);
        dispatcher.register(Commands.literal("gravity").requires(s -> s.hasPermission(2))
                .then(Commands.literal("direction")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(c -> show(c.getSource(), EntityArgument.getEntities(c, "targets")))
                                .then(Commands.argument("direction", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(names, b))
                                        .executes(c -> turn(c.getSource(), EntityArgument.getEntities(c, "targets"),
                                                StringArgumentType.getString(c, "direction"), DEFAULT_TURN_MS))
                                        .then(Commands.argument("turn_ms", IntegerArgumentType.integer(0, 10_000))
                                                .executes(c -> turn(c.getSource(),
                                                        EntityArgument.getEntities(c, "targets"),
                                                        StringArgumentType.getString(c, "direction"),
                                                        IntegerArgumentType.getInteger(c, "turn_ms"))))))));
    }

    private static int show(CommandSourceStack source, Collection<? extends Entity> targets) {
        for (Entity e : targets) {
            source.sendSuccess(() -> Component.literal(e.getName().getString() + ": gravity pulls " + of(e).getName()),
                    false);
        }
        return targets.size();
    }

    private static int turn(CommandSourceStack source, Collection<? extends Entity> targets, String name, int turnMs) {
        Direction direction = Direction.byName(name);
        if (direction == null) {
            source.sendFailure(Component.literal("Unknown direction " + name + ": use down, up, north, south, east "
                    + "or west"));
            return 0;
        }
        int n = 0;
        for (Entity e : targets) {
            if (set(e, direction, turnMs)) {
                n++;
            }
        }
        int count = n;
        source.sendSuccess(() -> Component.literal("Gravity pulls " + direction.getName() + " for " + count
                + " entities"), true);
        return n;
    }
}
