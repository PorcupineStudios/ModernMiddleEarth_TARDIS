package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.progression.SystemFile;
import com.porcupinestudios.tardisrb.progression.Tuning;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A solar system as the game uses it ({@link SystemFile} with its planets found): where the balls are, how they pull,
 * and how a spot of a planet's orbit layer maps to a spot beside its ball and back. Pure maths, safe on both sides.
 *
 * <p>A ball's north pole is +y and longitude 0 faces +z, as in {@link Planet#direction}. Leaving the top of the layer
 * over surface spot (x, z) puts you on the ray from the ball's centre through that spot, {@link Body#entry} out (plus
 * a little); flying inside {@code entry} drops you back into the layer over the spot below you.
 */
public record StarSystem(ResourceLocation id, ResourceKey<Level> dimension, SystemFile.Sun sun, List<Body> bodies) {
    /** How far past a boundary you come out, so you don't bounce straight back. */
    public static final double ARRIVE_OUTSIDE = 3;

    /** A planet's ball in the system. */
    public record Body(Planet planet, Vec3 center, double radius, double gravity, double gravityRadius) {
        public Body(Planet planet, Vec3 center, double radius, double gravity) {
            this(planet, center, radius, gravity, 0);
        }

        /** How far it pulls: its {@code gravity_radius}, else {@code focus_range} x its entry radius. */
        public double reach(Tuning.SpaceTuning t) {
            return gravityRadius > 0 ? gravityRadius : entry() * t.motion().focusRange();
        }

        /** How far from the centre the layer's top is: 1 + max_altitude radii, so the ball looks as big as it did. */
        public double entry() {
            return radius * (1 + planet.link().orbit().maxAltitude());
        }

        /** The outward direction over a surface spot. */
        public Vec3 outward(double surfaceX, double surfaceZ) {
            return Planet.direction(planet.lon(surfaceX), planet.lat(surfaceZ));
        }

        /** Where you come out beside the ball when you leave the top of the layer over this surface spot. */
        public Vec3 fromLayer(double surfaceX, double surfaceZ) {
            return center.add(outward(surfaceX, surfaceZ).scale(entry() + ARRIVE_OUTSIDE));
        }

        /** The surface spot under a point beside the ball (x, z). */
        public double[] surfaceUnder(Vec3 pos) {
            Vec3 d = pos.subtract(center).normalize();
            double lon = Math.atan2(d.x, d.z);
            double lat = Math.asin(Math.max(-1, Math.min(1, d.y)));
            Planet p = planet;
            double x = p.minX() + (lon + Math.PI) / (2 * Math.PI) * p.width();
            double z = p.minZ() + (Math.PI / 2 - lat) / Math.PI * p.height();
            return new double[] {Math.min(x, Math.nextDown(p.minX() + p.width())),
                    Math.min(z, Math.nextDown(p.minZ() + p.height()))};
        }

        /** East, north and up at a surface spot, in system directions. */
        public Vec3[] frame(double surfaceX, double surfaceZ) {
            double lon = planet.lon(surfaceX);
            double lat = planet.lat(surfaceZ);
            Vec3 east = new Vec3(Math.cos(lon), 0, -Math.sin(lon));
            Vec3 north = new Vec3(-Math.sin(lat) * Math.sin(lon), Math.cos(lat), -Math.sin(lat) * Math.cos(lon));
            return new Vec3[] {east, north, Planet.direction(lon, lat)};
        }

        /** Turns layer axes (x east, y up, z south) into system axes at that spot (velocityFromLayer as a rotation). */
        public org.joml.Quaternionf layerToSystem(double surfaceX, double surfaceZ) {
            Vec3[] f = frame(surfaceX, surfaceZ);
            return new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f()
                    .setColumn(0, (float) f[0].x, (float) f[0].y, (float) f[0].z)
                    .setColumn(1, (float) f[2].x, (float) f[2].y, (float) f[2].z)
                    .setColumn(2, (float) -f[1].x, (float) -f[1].y, (float) -f[1].z));
        }

        /** A layer velocity (x east, y up, z south) as a system velocity at that spot. */
        public Vec3 velocityFromLayer(double surfaceX, double surfaceZ, Vec3 v) {
            Vec3[] f = frame(surfaceX, surfaceZ);
            return f[0].scale(v.x).add(f[1].scale(-v.z)).add(f[2].scale(v.y));
        }

        /** A system velocity as a layer velocity over that spot. */
        public Vec3 velocityToLayer(double surfaceX, double surfaceZ, Vec3 w) {
            Vec3[] f = frame(surfaceX, surfaceZ);
            return new Vec3(w.dot(f[0]), w.dot(f[2]), -w.dot(f[1]));
        }
    }

    /** The system with the bodies whose planets exist, or null if none do. */
    public static StarSystem resolve(ResourceLocation id, SystemFile file, List<Planet> planets) {
        List<Body> bodies = new ArrayList<>();
        for (SystemFile.Body b : file.bodies()) {
            for (Planet p : planets) {
                if (p.id().equals(b.planet())) {
                    bodies.add(new Body(p, new Vec3(b.x(), b.y(), b.z()), b.radius(), b.gravity(), b.gravityRadius()));
                    break;
                }
            }
        }
        return bodies.isEmpty() ? null : new StarSystem(id, file.dimension(), file.sun(), List.copyOf(bodies));
    }

    public Vec3 sunCenter() {
        return new Vec3(sun.x(), sun.y(), sun.z());
    }

    public Body body(ResourceLocation planet) {
        for (Body b : bodies) {
            if (b.planet().id().equals(planet)) {
                return b;
            }
        }
        return null;
    }

    /**
     * The one body that pulls here (Aaron 2026-11-15: focus on one gravitational body at a time, per player, for
     * performance): the nearest ball whose gravity radius ({@link Body#reach}) holds the point, else the sun (null
     * body). Up is away from the focus, so going round it turns you and your ship.
     */
    public Body focus(Vec3 pos, Tuning.SpaceTuning t) {
        Body best = null;
        double bestD = Double.MAX_VALUE;
        for (Body b : bodies) {
            double d = pos.distanceToSqr(b.center());
            double r = b.reach(t);
            if (d < r * r && d < bestD) {
                best = b;
                bestD = d;
            }
        }
        return best;
    }

    /** Whether the sun pulls here: inside its gravity radius (unset: everywhere). */
    public boolean inSunReach(Vec3 pos) {
        double r = sun.gravityRadius();
        return r <= 0 || pos.distanceToSqr(sunCenter()) < r * r;
    }

    /** The centre of the focus here (a ball's or the sun's), or null where nothing pulls. */
    public Vec3 focusCenter(Vec3 pos, Tuning.SpaceTuning t) {
        Body b = focus(pos, t);
        return b != null ? b.center() : inSunReach(pos) ? sunCenter() : null;
    }

    /**
     * The pull at a point (blocks per tick per tick): only the focus body's, falling off with distance squared and
     * fading to nothing over the outer {@code gravity_edge} of its gravity radius; zero outside every radius.
     */
    public Vec3 gravity(Vec3 pos, Tuning.SpaceTuning t) {
        double g0 = Orbits.G * t.gravity();
        double edge = t.motion().gravityEdge();
        Body b = focus(pos, t);
        if (b != null) {
            return pull(pos, b.center(), b.radius(), g0 * b.gravity())
                    .scale(fade(pos.distanceTo(b.center()), b.reach(t), edge));
        }
        if (!inSunReach(pos)) {
            return Vec3.ZERO;
        }
        Vec3 g = pull(pos, sunCenter(), sun.radius(), g0 * sun.gravity());
        return sun.gravityRadius() > 0 ? g.scale(fade(pos.distanceTo(sunCenter()), sun.gravityRadius(), edge)) : g;
    }

    /** 1 well inside a radius, easing (smoothstep) to 0 at its edge over the outer {@code edge} share. */
    public static double fade(double d, double radius, double edge) {
        if (edge <= 0) {
            return d < radius ? 1 : 0;
        }
        double s = Math.max(0, Math.min(1, (radius - d) / (edge * radius)));
        return s * s * (3 - 2 * s);
    }

    private static Vec3 pull(Vec3 pos, Vec3 center, double radius, double surface) {
        if (radius <= 0 || surface <= 0) {
            return Vec3.ZERO;
        }
        Vec3 to = center.subtract(pos);
        double d = Math.max(radius, to.length());
        return to.scale(surface * radius * radius / (d * d * d));
    }

    /** The ball whose layer you'd drop into here, or null. */
    public Body entered(Vec3 pos) {
        for (Body b : bodies) {
            if (pos.distanceToSqr(b.center()) < b.entry() * b.entry()) {
                return b;
            }
        }
        return null;
    }

    public boolean inSun(Vec3 pos) {
        return sun.radius() > 0 && pos.distanceToSqr(sunCenter()) < sun.radius() * sun.radius();
    }

    /** Which of the six gravity directions is closest to the pull here (down when there's none). */
    public Direction down(Vec3 pos, Tuning.SpaceTuning t) {
        Vec3 g = gravity(pos, t);
        return g.lengthSqr() < 1e-18 ? Direction.DOWN : Direction.getNearest(g.x, g.y, g.z);
    }
}
