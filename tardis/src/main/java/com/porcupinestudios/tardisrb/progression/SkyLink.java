package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where a dimension's sky leads ({@code tardis/sky}): any dimension can have one (Aaron, 2026-11-03: space isn't tied to
 * the Overworld). Above {@code ceiling} the dimension gives way to {@code space}, where it is a planet: a ball at
 * {@code center} of {@code radius}. The planet's surface is the square from -{@code half_size} to +{@code half_size} on
 * x and z, wrapped onto the ball by longitude (x) and latitude (z). Coming down from space you arrive
 * {@code arrive_below} blocks under the ceiling.
 */
public record SkyLink(ResourceKey<Level> dimension, ResourceKey<Level> space, Vec3 center, double radius, int ceiling,
        int halfSize, int arriveBelow) {
    public static final Codec<SkyLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(SkyLink::dimension),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("space").forGetter(SkyLink::space),
            Vec3.CODEC.optionalFieldOf("center", Vec3.ZERO).forGetter(SkyLink::center),
            Codec.doubleRange(8, 1000).optionalFieldOf("radius", 120.0).forGetter(SkyLink::radius),
            Codec.INT.optionalFieldOf("ceiling", 340).forGetter(SkyLink::ceiling),
            Codec.intRange(64, 1 << 24).optionalFieldOf("half_size", 4096).forGetter(SkyLink::halfSize),
            Codec.intRange(1, 256).optionalFieldOf("arrive_below", 24).forGetter(SkyLink::arriveBelow))
            .apply(i, SkyLink::new));

    /** Where on the ball (a point just outside the surface, {@code lift} blocks up) a spot on the planet lies. */
    public Vec3 toSpace(double x, double z, double lift) {
        Vec3 dir = direction(x, z);
        return center.add(dir.scale(radius + lift));
    }

    /** The outward direction for a spot on the planet: x gives the longitude, z the latitude. */
    public Vec3 direction(double x, double z) {
        double lon = (wrap(x) / halfSize) * Math.PI;
        double lat = Math.max(-1, Math.min(1, wrap(z) / halfSize)) * Math.PI / 2;
        return new Vec3(Math.cos(lat) * Math.sin(lon), Math.sin(lat), Math.cos(lat) * Math.cos(lon));
    }

    /** The spot on the planet below a point in space (by its direction from the centre): x and z. */
    public double[] toSurface(Vec3 inSpace) {
        Vec3 d = inSpace.subtract(center).normalize();
        double lat = Math.asin(Math.max(-1, Math.min(1, d.y)));
        double lon = Math.atan2(d.x, d.z);
        return new double[] {lon / Math.PI * halfSize, lat / (Math.PI / 2) * halfSize};
    }

    /** Folds a coordinate into the planet's square, so the surface wraps around. */
    public double wrap(double v) {
        double size = 2.0 * halfSize;
        return v - size * Math.floor((v + halfSize) / size);
    }
}
