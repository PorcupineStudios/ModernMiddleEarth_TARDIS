package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.net.SkyNet;
import com.porcupinestudios.tardisrb.progression.SkyLink;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Seamless space, first slice (Aaron, 2026-11-03: Outer Wilds-style space, in any dimension). Every dimension with a
 * sky link ({@code tardis/sky}) leads into its space dimension above its ceiling: whatever flies higher comes out over
 * the same spot on the planet, a ball in space. Whatever falls into the ball (or out of the bottom of space) comes down
 * to the spot below it, under the ceiling, falling slowly. Space has low gravity. The globe itself is drawn by the
 * client ({@code client/GlobeRenderer}); walking round a looping planet comes in a later slice.
 */
public final class SpaceTravel {
    public static final ResourceLocation GRAVITY_ID = TardisRB.id("space_gravity");
    private static final double LIFT = 16;
    private static final int BOTTOM_MARGIN = 16;

    private SpaceTravel() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SpaceTravel::onTick);
        NeoForge.EVENT_BUS.addListener(SpaceTravel::onJoin);
        NeoForge.EVENT_BUS.addListener(SpaceTravel::onLogin);
        NeoForge.EVENT_BUS.addListener(SpaceTravel::onChangeDimension);
    }

    public static Collection<SkyLink> links() {
        return TardisContent.get().skies().values();
    }

    /** The space dimensions some sky leads to. */
    public static Set<ResourceKey<Level>> spaces() {
        return links().stream().map(SkyLink::space).collect(Collectors.toSet());
    }

    public static boolean isSpace(Level level) {
        return spaces().contains(level.dimension());
    }

    /** The sky link of this dimension, if it has one. */
    public static Optional<SkyLink> linkOf(ResourceKey<Level> dimension) {
        return links().stream().filter(l -> l.dimension().equals(dimension)).findFirst();
    }

    /** Whether this entity is moved by the sky links (not portals, riders or vehicles with riders). */
    static boolean travels(Entity e) {
        return e.isAlive() && !(e instanceof Portal) && !e.isPassenger() && !e.isVehicle();
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Tuning.SpaceTuning tuning = TardisContent.tuning().space();
        if (server.getTickCount() % tuning.checkTicks() != 0 || links().isEmpty()) {
            return;
        }
        for (SkyLink link : links()) {
            ServerLevel world = server.getLevel(link.dimension());
            ServerLevel space = server.getLevel(link.space());
            if (world == null || space == null) {
                continue;
            }
            List<Entity> up = new ArrayList<>();
            for (Entity e : world.getAllEntities()) {
                if (e.getY() > link.ceiling() && travels(e)) {
                    up.add(e);
                }
            }
            up.forEach(e -> toSpace(e, link, space));
            double reach = link.radius() + 2;
            List<Entity> down = space.getEntitiesOfClass(Entity.class, new AABB(link.center(), link.center())
                    .inflate(reach), e -> travels(e) && e.position().distanceTo(link.center()) < reach);
            down.forEach(e -> toSurface(e, link, world, tuning));
        }
        for (ResourceKey<Level> key : spaces()) {
            ServerLevel space = server.getLevel(key);
            if (space == null) {
                continue;
            }
            List<Entity> lost = new ArrayList<>();
            for (Entity e : space.getAllEntities()) {
                if (e instanceof LivingEntity living) {
                    setGravity(living, true);
                }
                if (e.getY() < space.getMinBuildHeight() + BOTTOM_MARGIN && travels(e)) {
                    lost.add(e);
                }
            }
            for (Entity e : lost) {
                nearest(key, e.position()).ifPresent(link -> {
                    ServerLevel world = server.getLevel(link.dimension());
                    if (world != null) {
                        toSurface(e, link, world, tuning);
                    }
                });
            }
        }
    }

    /** The planet in this space whose surface is nearest. */
    static Optional<SkyLink> nearest(ResourceKey<Level> space, Vec3 at) {
        return links().stream().filter(l -> l.space().equals(space))
                .min((a, b) -> Double.compare(at.distanceTo(a.center()) - a.radius(),
                        at.distanceTo(b.center()) - b.radius()));
    }

    /** Up through the sky: out over the same spot on the planet. */
    public static Entity toSpace(Entity e, SkyLink link, ServerLevel space) {
        Vec3 to = link.toSpace(e.getX(), e.getZ(), LIFT);
        space.getChunk(BlockPos.containing(to));
        return move(e, space, to);
    }

    /** Down from space: to the spot below, under the ceiling, falling slowly. */
    public static Entity toSurface(Entity e, SkyLink link, ServerLevel world, Tuning.SpaceTuning tuning) {
        double[] xz = link.toSurface(e.position());
        Vec3 to = new Vec3(xz[0], link.ceiling() - link.arriveBelow(), xz[1]);
        world.getChunk(BlockPos.containing(to));
        if (e instanceof LivingEntity living) {
            setGravity(living, false);
            if (tuning.landingSlowFall() > 0) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, tuning.landingSlowFall() * 20));
            }
        }
        e.fallDistance = 0;
        return move(e, world, to);
    }

    /**
     * Players go through Immersive Portals' seamless teleport (no loading screen); everything else changes dimension
     * the vanilla way, which is done at once (IP's teleport of other entities waits a tick).
     */
    /** Moves the entity and returns it where it ended up (a non-player is a new entity after a dimension change). */
    private static Entity move(Entity e, ServerLevel to, Vec3 pos) {
        if (e instanceof ServerPlayer) {
            PortalAPI.teleportEntity(e, to, pos);
            return e;
        }
        if (e.level() == to) {
            e.teleportTo(pos.x, pos.y, pos.z);
            return e;
        }
        Entity moved = e.changeDimension(new DimensionTransition(to, pos, e.getDeltaMovement(), e.getYRot(),
                e.getXRot(), DimensionTransition.DO_NOTHING));
        return moved != null ? moved : e;
    }

    /** Low gravity in space, normal gravity elsewhere. */
    public static void setGravity(LivingEntity living, boolean inSpace) {
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        if (inSpace) {
            double amount = TardisContent.tuning().space().gravity() - 1;
            AttributeModifier now = gravity.getModifier(GRAVITY_ID);
            if (now == null || now.amount() != amount) {
                gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_ID, amount,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else if (gravity.hasModifier(GRAVITY_ID)) {
            gravity.removeModifier(GRAVITY_ID);
        }
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living) {
            setGravity(living, isSpace(event.getLevel()));
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            SkyNet.sendGlobes(sp);
        }
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            setGravity(sp, isSpace(sp.level()));
            SkyNet.sendGlobes(sp);
        }
    }
}
