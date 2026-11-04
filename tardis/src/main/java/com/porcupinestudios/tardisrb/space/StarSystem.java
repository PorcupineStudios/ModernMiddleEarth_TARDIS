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
    public record Body(Planet planet, Vec3 center, double radius, double gravity) {
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
                    bodies.add(new Body(p, new Vec3(b.x(), b.y(), b.z()), b.radius(), b.gravity()));
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

    /** The pull at a point (blocks per tick per tick): every ball and the sun, each falling off with distance squared. */
    public Vec3 gravity(Vec3 pos, Tuning.SpaceTuning t) {
        double g0 = Orbits.G * t.gravity();
        Vec3 sum = pull(pos, sunCenter(), sun.radius(), g0 * sun.gravity());
        for (Body b : bodies) {
            sum = sum.add(pull(pos, b.center(), b.radius(), g0 * b.gravity()));
        }
        return sum;
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
