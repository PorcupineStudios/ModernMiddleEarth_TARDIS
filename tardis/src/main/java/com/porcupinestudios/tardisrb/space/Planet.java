package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.progression.SkyLink;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * A planet as the game uses it: a sky link with its surface settled (a planet provider may have replaced it), plus the
 * maths shared by the server (moving things) and the client (drawing the globe). Pure maths, safe on both sides.
 *
 * <p>Surface to globe: x goes once round (longitude, east = +x), z runs from the north pole ({@code minZ}) to the south
 * pole ({@code maxZ}), as on a map with north up. Surface to orbit: the orbit layer is the surface shrunk
 * {@code orbit.scale} times across, centred on {@code orbit.x/z}; its height is altitude (see {@link #altitude}).
 */
public record Planet(ResourceLocation id, SkyLink link) {
    public double minX() {
        return link.surface().minX();
    }

    public double minZ() {
        return link.surface().minZ();
    }

    public double width() {
        return link.surface().maxX() - (double) link.surface().minX();
    }

    public double height() {
        return link.surface().maxZ() - (double) link.surface().minZ();
    }

    public double centerX() {
        return minX() + width() / 2;
    }

    public double centerZ() {
        return minZ() + height() / 2;
    }

    /**
     * The globe's radius at full size: the surface's longer side is once round it. The globe is drawn unwrapped round
     * each viewer's own spot (see {@code GlobeRenderer}), so any rectangle fits without stretching where you look.
     */
    public double radius() {
        return Math.max(width(), height()) / (2 * Math.PI);
    }

    /** Longitude in radians (-pi..pi) of a surface x. */
    public double lon(double x) {
        return (x - minX()) / width() * 2 * Math.PI - Math.PI;
    }

    /** Latitude in radians (pi/2 at the north pole, minZ) of a surface z. */
    public double lat(double z) {
        return Math.PI / 2 - (z - minZ()) / height() * Math.PI;
    }

    /** The outward direction of the globe at a longitude and latitude (y is the north pole). */
    public static Vec3 direction(double lon, double lat) {
        return new Vec3(Math.cos(lat) * Math.sin(lon), Math.sin(lat), Math.cos(lat) * Math.cos(lon));
    }

    // ---- the orbit layer ----

    public double spaceX(double x) {
        return link.orbit().x() + (x - centerX()) / link.orbit().scale();
    }

    public double spaceZ(double z) {
        return link.orbit().z() + (z - centerZ()) / link.orbit().scale();
    }

    public double surfaceX(double spaceX) {
        return centerX() + (spaceX - link.orbit().x()) * link.orbit().scale();
    }

    public double surfaceZ(double spaceZ) {
        return centerZ() + (spaceZ - link.orbit().z()) * link.orbit().scale();
    }

    /** Whether a point of the space dimension lies over this planet (inside its layer, give or take {@code margin}). */
    public boolean overLayer(double spaceX, double spaceZ, double margin) {
        double s = link.orbit().scale();
        return Math.abs(spaceX - link.orbit().x()) <= width() / (2 * s) + margin
                && Math.abs(spaceZ - link.orbit().z()) <= height() / (2 * s) + margin;
    }

    /** How far a point of space is outside this planet's layer (0 inside), for finding the nearest planet. */
    public double distanceToLayer(double spaceX, double spaceZ) {
        double s = link.orbit().scale();
        double dx = Math.max(0, Math.abs(spaceX - link.orbit().x()) - width() / (2 * s));
        double dz = Math.max(0, Math.abs(spaceZ - link.orbit().z()) - height() / (2 * s));
        return Math.hypot(dx, dz);
    }

    /** Altitude over the ground at the ceiling: where the orbit layer starts. */
    public double baseAltitude() {
        return Math.max(1, link.ceiling() - link.ground());
    }

    /** How fast altitude grows with height in the layer (it doubles every ln 2 / k blocks). */
    private double growth() {
        double top = Math.max(baseAltitude() * 1.01, link.orbit().maxAltitude() * radius());
        return Math.log(top / baseAltitude()) / (link.orbit().top() - link.orbit().floor());
    }

    /** Full-size altitude over the ground at a height of the orbit layer. */
    public double altitude(double spaceY) {
        return baseAltitude() * Math.exp(growth() * (spaceY - link.orbit().floor()));
    }

    /** The height in the orbit layer for a full-size altitude (the inverse of {@link #altitude}). */
    public double spaceY(double altitude) {
        return link.orbit().floor() + Math.log(Math.max(1e-9, altitude) / baseAltitude()) / growth();
    }

    // ---- wrapping round the globe ----

    /** Where a surface spot outside the planet's rectangle really is, and how much the traveller turns (degrees). */
    public record Wrapped(double x, double z, float turn) {}

    /**
     * The spot inside the rectangle for one outside it, or null if it's inside or the planet doesn't wrap. On a globe
     * x loops; crossing a pole folds the edge onto itself ({@link #poleX}) and turns you round, as walking over the
     * top of a globe does.
     */
    public Wrapped wrap(double x, double z) {
        SkyLink.Wrap w = link.wrap();
        if (w == SkyLink.Wrap.NONE) {
            return null;
        }
        double maxZ = minZ() + height();
        boolean changed = false;
        float turn = 0;
        if (z < minZ() || z >= maxZ) {
            changed = true;
            if (w == SkyLink.Wrap.TORUS) {
                z = minZ() + floorMod(z - minZ(), height());
            } else {
                z = z < minZ() ? 2 * minZ() - z : 2 * maxZ - z;
                z = Math.max(minZ(), Math.min(Math.nextDown(maxZ), z));
                x = poleX(x);
                turn = 180;
            }
        }
        if (x < minX() || x >= minX() + width()) {
            changed = true;
        }
        x = minX() + floorMod(x - minX(), width());
        return changed ? new Wrapped(x, z, turn) : null;
    }

    /**
     * Over a pole, x folds onto the far side: x becomes {@code c - x} with c = 2 * centre + half the width, so the
     * centre of the map comes out on the opposite side of the globe. A fold, not a shift, so a portal (which can only
     * turn, not mirror) carries the same view.
     */
    public double poleX(double x) {
        double c = 2 * centerX() + width() / 2;
        return minX() + floorMod(c - x - minX(), width());
    }

    private static double floorMod(double v, double m) {
        return v - m * Math.floor(v / m);
    }
}
