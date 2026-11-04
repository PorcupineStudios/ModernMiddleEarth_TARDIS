package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A solar system ({@code tardis/system}): the space between planets (Aaron, 2026-11-04: "down is always the planet"
 * doesn't work in a solar system, so this is the second zone). Climb out of the top of a planet's orbit layer and you
 * come out here, beside a small ball of that planet; fly into a ball and you drop back into its layer. Every body pulls
 * you towards it, falling off with distance squared, so there is no fixed down.
 *
 * <p>{@code dimension} is where the system is (several systems can share one if they keep apart). Each body names a
 * sky link ({@code planet}) and where its ball is; bodies whose planet isn't in this world are left out.
 * {@code radius} is the ball's size in blocks: you come out of the planet's layer at {@code 1 + max_altitude} radii
 * from its centre, so it looks the size it did from the top of the layer. {@code gravity} multiplies the pull (1 = the
 * same as just above the clouds, at the ball's surface). The sun is a glowing ball that burns and pushes you out.
 */
public record SystemFile(ResourceKey<Level> dimension, Sun sun, List<Body> bodies) {
    public record Sun(double x, double y, double z, double radius, double gravity, int color, float burn) {
        public static final Sun NONE = new Sun(0, 0, 0, 0, 0, 0xFFE9A0, 0);
        public static final Codec<Sun> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(Sun::x),
                Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(Sun::y),
                Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(Sun::z),
                Codec.doubleRange(0, 100_000).optionalFieldOf("radius", 400.0).forGetter(Sun::radius),
                Codec.doubleRange(0, 1000).optionalFieldOf("gravity", 1.0).forGetter(Sun::gravity),
                Codec.STRING.xmap(s -> Integer.parseInt(s.replace("#", ""), 16),
                        c -> String.format("#%06X", c & 0xFFFFFF)).optionalFieldOf("color", 0xFFE9A0)
                        .forGetter(Sun::color),
                Codec.floatRange(0, 1000).optionalFieldOf("burn", 4.0f).forGetter(Sun::burn))
                .apply(i, Sun::new));
    }

    public record Body(ResourceLocation planet, double x, double y, double z, double radius, double gravity) {
        public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("planet").forGetter(Body::planet),
                Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(Body::x),
                Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(Body::y),
                Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(Body::z),
                Codec.doubleRange(1, 10_000).optionalFieldOf("radius", 128.0).forGetter(Body::radius),
                Codec.doubleRange(0, 1000).optionalFieldOf("gravity", 1.0).forGetter(Body::gravity))
                .apply(i, Body::new));
    }

    public static final Codec<SystemFile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(SystemFile::dimension),
            Sun.CODEC.optionalFieldOf("sun", Sun.NONE).forGetter(SystemFile::sun),
            Body.CODEC.listOf().fieldOf("bodies").forGetter(SystemFile::bodies))
            .apply(i, SystemFile::new));

    /** What's wrong with it, or null. */
    public String problem() {
        if (bodies.isEmpty()) {
            return "needs at least one body";
        }
        for (Body b : bodies) {
            double d = Math.sqrt((b.x - sun.x) * (b.x - sun.x) + (b.y - sun.y) * (b.y - sun.y)
                    + (b.z - sun.z) * (b.z - sun.z));
            if (d < sun.radius + b.radius) {
                return "body " + b.planet + " is inside the sun";
            }
        }
        return null;
    }
}
