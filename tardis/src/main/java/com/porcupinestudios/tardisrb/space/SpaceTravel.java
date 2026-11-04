package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.gravity.gc.api.GravityChangerAPI;
import com.porcupinestudios.tardisrb.net.SkyNet;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Going up into space and coming back down (Aaron, 2026-11-03: seamless Outer Wilds-style space, from any dimension;
 * 2026-11-04: there was no way back to Middle-earth). Each planet's space is a scale model of it: its orbit layer
 * ({@link Planet}). Climb above the ceiling and you come out at the bottom of the layer over the same spot; sink below
 * the layer's floor and you come back down under the ceiling over the spot you're above. Both swaps happen inside the
 * cloud bank the client draws there, so they don't show. Walking (or flying) off a surface or layer edge wraps round
 * the globe; near players, {@link SeamPortals} make the edge see-through. Space has low gravity.
 */
public final class SpaceTravel {
    public static final ResourceLocation GRAVITY_ID = TardisRB.id("space_gravity");
    /** How far inside the boundary you come out, so you don't bounce straight back. */
    private static final double ARRIVE_INSIDE = 3;
    /** How long the view takes to turn when down changes between the planets (ms). */
    private static final int TURN_MS = 1000;
    /** How far past an edge something must be before the server wraps it (a seam portal usually does it first). */
    private static final double WRAP_SLACK = 1.5;

    /** The planet list players last got (sent again when the data reloads). */
    private static List<Planet> sent;

    private SpaceTravel() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SpaceTravel::onTick);
        NeoForge.EVENT_BUS.addListener(SpaceTravel::onJoin);
        NeoForge.EVENT_BUS.addListener(SpaceTravel::onLogin);
        NeoForge.EVENT_BUS.addListener(SpaceTravel::onChangeDimension);
        SeamPortals.register();
        SpacePhysics.register();
        com.porcupinestudios.tardisrb.gravity.GravityScale.register();
        com.porcupinestudios.tardisrb.gravity.GravityDirection.register();
        com.porcupinestudios.tardisrb.gravity.Tilt.register();
        PlanetMaps.register();
    }

    /** Whether a level is space (a planet's orbit layers or a solar system): vanilla gravity is off there. */
    public static boolean isSpace(net.minecraft.world.level.Level level) {
        MinecraftServer server = level.getServer();
        return server != null && (Planets.isSpace(server, level.dimension())
                || Systems.isSystem(server, level.dimension()));
    }

    /** Whether this entity is moved by the sky (not portals, riders or vehicles with riders). */
    static boolean travels(Entity e) {
        return e.isAlive() && !(e instanceof Portal) && !e.isPassenger() && !e.isVehicle();
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Tuning.SpaceTuning tuning = TardisContent.tuning().space();
        // players every tick (fast movers can't overshoot far before being caught), everything else every check_ticks
        boolean all = server.getTickCount() % tuning.checkTicks() == 0;
        List<Planet> planets = Planets.all(server);
        if (planets != sent) {
            sent = planets;
            server.getPlayerList().getPlayers().forEach(SkyNet::sendPlanets);
        }
        if (planets.isEmpty()) {
            return;
        }
        for (Planet planet : planets) {
            ServerLevel world = server.getLevel(planet.link().dimension());
            ServerLevel space = server.getLevel(planet.link().space());
            if (world == null || space == null) {
                continue;
            }
            List<Entity> up = new ArrayList<>();
            List<Entity> off = new ArrayList<>();
            for (Entity e : all ? world.getAllEntities() : List.<Entity>copyOf(world.players())) {
                if (!travels(e)) {
                    continue;
                }
                if (e.getY() > planet.link().ceiling()) {
                    up.add(e);
                } else if (outside(planet, e.getX(), e.getZ())) {
                    off.add(e);
                }
            }
            up.forEach(e -> toSpace(e, planet, space));
            off.forEach(e -> wrapOnSurface(e, planet));
        }
        tickSystems(server, tuning, all);
        for (ServerLevel space : spaceLevels(server, planets)) {
            List<Entity> down = new ArrayList<>();
            for (Entity e : all ? space.getAllEntities() : List.<Entity>copyOf(space.players())) {
                if (all && e instanceof LivingEntity living) {
                    setGravity(living, true);
                }
                if (travels(e)) {
                    down.add(e);
                }
            }
            for (Entity e : down) {
                Optional<Planet> under = Planets.under(server, space.dimension(), e.getX(), e.getZ());
                if (under.isEmpty()) {
                    continue;
                }
                Planet planet = under.get();
                boolean belowFloor = e.getY() < planet.link().orbit().floor()
                        || e.getY() < space.getMinBuildHeight() + 16;
                if (belowFloor) {
                    ServerLevel world = server.getLevel(planet.link().dimension());
                    if (world != null) {
                        toSurface(e, planet, world, tuning);
                    }
                } else if (e.getY() > planet.link().orbit().top()) {
                    Optional<StarSystem> system = Systems.ofPlanet(server, planet.id());
                    ServerLevel sys = system.map(st -> server.getLevel(st.dimension())).orElse(null);
                    if (sys != null) {
                        toSystem(e, planet, system.get().body(planet.id()), sys, tuning);
                    }
                } else if (!planet.overLayer(e.getX(), e.getZ(), WRAP_SLACK)) {
                    wrapInSpace(e, planet);
                }
            }
        }
    }

    /** Between the planets: into a ball's layer, burnt by the sun, down turning towards the strongest pull. */
    private static void tickSystems(MinecraftServer server, Tuning.SpaceTuning tuning, boolean all) {
        List<StarSystem> systems = Systems.all(server);
        List<ServerLevel> levels = new ArrayList<>();
        for (StarSystem st : systems) {
            ServerLevel l = server.getLevel(st.dimension());
            if (l != null && !levels.contains(l)) {
                levels.add(l);
            }
        }
        for (ServerLevel level : levels) {
            List<Entity> here = new ArrayList<>();
            for (Entity e : all ? level.getAllEntities() : List.<Entity>copyOf(level.players())) {
                if (all && e instanceof LivingEntity living) {
                    setGravity(living, true);
                }
                if (travels(e)) {
                    here.add(e);
                }
            }
            for (Entity e : here) {
                Vec3 pos = e.position();
                StarSystem st = Systems.near(systems, level.dimension(), pos).orElse(null);
                if (st == null) {
                    continue;
                }
                StarSystem.Body body = st.entered(pos);
                if (body != null) {
                    ServerLevel space = server.getLevel(body.planet().link().space());
                    if (space != null) {
                        toLayer(e, body, space);
                    }
                } else if (st.inSun(pos)) {
                    burn(e, st);
                } else if (all && Orbits.moves(e) && !e.onGround()) {
                    net.minecraft.core.Direction down = st.down(pos, tuning);
                    if (down != com.porcupinestudios.tardisrb.gravity.GravityDirection.of(e)) {
                        com.porcupinestudios.tardisrb.gravity.GravityDirection.set(e, down, TURN_MS);
                    }
                }
                keepInside(e, level);
            }
        }
    }

    /** Out of the top of a planet's layer: beside its ball in the solar system, moving the same way. */
    public static Entity toSystem(Entity e, Planet planet, StarSystem.Body body, ServerLevel system,
            Tuning.SpaceTuning tuning) {
        double sx = planet.surfaceX(e.getX());
        double sz = Math.max(planet.minZ(), Math.min(Math.nextDown(planet.minZ() + planet.height()),
                planet.surfaceZ(e.getZ())));
        Vec3 to = body.fromLayer(sx, sz);
        Vec3 velocity = body.velocityFromLayer(sx, sz, GravityChangerAPI.getWorldVelocity(e));
        system.getChunk(BlockPos.containing(to));
        Entity moved = move(e, system, to, 0);
        StarSystem st = Systems.ofPlanet(system.getServer(), planet.id()).orElse(null);
        net.minecraft.core.Direction down = st == null ? net.minecraft.core.Direction.DOWN : st.down(to, tuning);
        return arrive(moved, down, velocity);
    }

    /** Into a ball: back at the top of its planet's layer over the spot below you, down being down again. */
    public static Entity toLayer(Entity e, StarSystem.Body body, ServerLevel space) {
        Planet planet = body.planet();
        double[] spot = body.surfaceUnder(e.position());
        Vec3 velocity = body.velocityToLayer(spot[0], spot[1], GravityChangerAPI.getWorldVelocity(e));
        Vec3 to = new Vec3(planet.spaceX(spot[0]), planet.link().orbit().top() - ARRIVE_INSIDE,
                planet.spaceZ(spot[1]));
        space.getChunk(BlockPos.containing(to));
        return arrive(move(e, space, to, 0), net.minecraft.core.Direction.DOWN, velocity);
    }

    /** Sets gravity at once (no view turn) and the world velocity, and tells a player's client the speed. */
    private static Entity arrive(Entity e, net.minecraft.core.Direction down, Vec3 worldVelocity) {
        com.porcupinestudios.tardisrb.gravity.Tilt.clear(e);
        com.porcupinestudios.tardisrb.gravity.GravityDirection.set(e, down, 0);
        GravityChangerAPI.setWorldVelocity(e, worldVelocity);
        e.hurtMarked = true;
        return e;
    }

    /** Inside the sun: set alight, hurt, and thrown back out. */
    private static void burn(Entity e, StarSystem st) {
        Vec3 out = e.position().subtract(st.sunCenter());
        out = out.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : out.normalize();
        e.igniteForSeconds(8);
        if (st.sun().burn() > 0) {
            e.hurt(e.damageSources().inFire(), st.sun().burn());
        }
        GravityChangerAPI.setWorldVelocity(e, out.scale(1.5));
        e.hurtMarked = true;
    }

    /** The system dimension has a floor and a roof (build height): stop there instead of falling out. */
    private static void keepInside(Entity e, ServerLevel level) {
        double low = level.getMinBuildHeight() + 8;
        double high = level.getMaxBuildHeight() - 8;
        if (e.getY() < low || e.getY() > high) {
            Vec3 w = GravityChangerAPI.getWorldVelocity(e);
            double y = Math.max(low, Math.min(high, e.getY()));
            e.teleportTo(e.getX(), y, e.getZ());
            GravityChangerAPI.setWorldVelocity(e, new Vec3(w.x, 0, w.z));
            e.hurtMarked = true;
        }
    }

    private static List<ServerLevel> spaceLevels(MinecraftServer server, List<Planet> planets) {
        List<ServerLevel> out = new ArrayList<>();
        for (Planet p : planets) {
            ServerLevel s = server.getLevel(p.link().space());
            if (s != null && !out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    private static boolean outside(Planet p, double x, double z) {
        var b = p.link().surface();
        return x < b.minX() - WRAP_SLACK || x > b.maxX() + WRAP_SLACK || z < b.minZ() - WRAP_SLACK
                || z > b.maxZ() + WRAP_SLACK;
    }

    /** Up through the clouds: out at the bottom of the planet's orbit layer, over the same spot. */
    public static Entity toSpace(Entity e, Planet planet, ServerLevel space) {
        Vec3 to = new Vec3(planet.spaceX(e.getX()), planet.link().orbit().floor() + ARRIVE_INSIDE,
                planet.spaceZ(e.getZ()));
        space.getChunk(BlockPos.containing(to));
        return move(e, space, to, 0);
    }

    /** Down from space: under the ceiling over the spot below, falling slowly. */
    public static Entity toSurface(Entity e, Planet planet, ServerLevel world, Tuning.SpaceTuning tuning) {
        double x = planet.surfaceX(e.getX());
        double z = planet.surfaceZ(e.getZ());
        float turn = 0;
        Planet.Wrapped w = planet.wrap(x, z);
        if (w != null) {
            x = w.x();
            z = w.z();
            turn = w.turn();
        }
        Vec3 to = new Vec3(x, planet.link().ceiling() - ARRIVE_INSIDE, z);
        world.getChunk(BlockPos.containing(to));
        if (e instanceof LivingEntity living) {
            setGravity(living, false);
            if (tuning.landingSlowFall() > 0) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, tuning.landingSlowFall() * 20));
            }
        }
        e.fallDistance = 0;
        return move(e, world, to, turn);
    }

    /** Off the edge of the surface: round the globe to where that really is. */
    public static void wrapOnSurface(Entity e, Planet planet) {
        Planet.Wrapped w = planet.wrap(e.getX(), e.getZ());
        if (w != null) {
            move(e, (ServerLevel) e.level(), new Vec3(w.x(), e.getY(), w.z()), w.turn());
        }
    }

    /** Off the edge of the orbit layer: the same wrap as the surface, at orbit scale. */
    static void wrapInSpace(Entity e, Planet planet) {
        Planet.Wrapped w = planet.wrap(planet.surfaceX(e.getX()), planet.surfaceZ(e.getZ()));
        if (w != null) {
            move(e, (ServerLevel) e.level(), new Vec3(planet.spaceX(w.x()), e.getY(), planet.spaceZ(w.z())),
                    w.turn());
        }
    }

    /**
     * Moves the entity and returns it where it ended up (a non-player is a new entity after a dimension change).
     * Players go through Immersive Portals' seamless teleport (no loading screen) unless they must turn round (over a
     * pole); everything else moves the vanilla way, at once.
     */
    private static Entity move(Entity e, ServerLevel to, Vec3 pos, float turn) {
        if (e instanceof ServerPlayer sp) {
            if (to != e.level()) {
                setGravity(sp, isSpace(to));
            }
            if (turn == 0) {
                PortalAPI.teleportEntity(e, to, pos);
            } else {
                sp.teleportTo(to, pos.x, pos.y, pos.z, sp.getYRot() + turn, sp.getXRot());
            }
            return e;
        }
        Vec3 v = e.getDeltaMovement();
        if (turn != 0) {
            // Half a turn about world up; the velocity is in the entity's own frame when its gravity is turned.
            var g = com.porcupinestudios.tardisrb.gravity.gc.api.GravityChangerAPI.getGravityDirection(e);
            Vec3 w = com.porcupinestudios.tardisrb.gravity.gc.util.RotationUtil.vecPlayerToWorld(v, g);
            v = com.porcupinestudios.tardisrb.gravity.gc.util.RotationUtil.vecWorldToPlayer(
                    new Vec3(-w.x, w.y, -w.z), g);
        }
        if (e.level() == to) {
            e.teleportTo(pos.x, pos.y, pos.z);
            e.setYRot(e.getYRot() + turn);
            e.setDeltaMovement(v);
            return e;
        }
        Entity moved = e.changeDimension(new DimensionTransition(to, pos, v, e.getYRot() + turn, e.getXRot(),
                DimensionTransition.DO_NOTHING));
        return moved != null ? moved : e;
    }

    /** Vanilla gravity off in space ({@link Orbits} pulls instead), normal gravity elsewhere. */
    public static void setGravity(LivingEntity living, boolean inSpace) {
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        if (inSpace) {
            double amount = -1; // our own gravity takes over (Orbits)
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
            SkyNet.sendPlanets(sp);
        }
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            setGravity(sp, isSpace(sp.level()));
        }
    }
}
