package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.provider.PlanetSurface;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;

/**
 * A dimension as a planet ({@code tardis/sky}). Any dimension can have one (Aaron, 2026-11-03).
 *
 * <p>The planet keeps its full size on the ground (Aaron, 2026-11-04: Middle-earth can't be scaled down). Its surface
 * is the rectangle {@code surface} (x once round the globe, z from pole to pole), and it wraps at the edges
 * ({@code wrap}). Above {@code ceiling} you fly out into {@code space}, through a cloud bank {@code cloud_band} blocks
 * thick. Space is a scale model: the planet's orbit is a layer of the space dimension centred on {@code orbit.x/z},
 * {@code orbit.scale} times smaller across than the surface, from {@code orbit.floor} (just above the clouds) up to
 * {@code orbit.top}. Height in that layer is altitude, growing faster the higher you go, so at the top the whole globe
 * is a ball {@code orbit.max_altitude} planet radii below you. The globe is drawn under each player from their own spot,
 * so going up and coming down land on the same spot. A provider in the shared API (the LOTR port for Middle-earth) can
 * replace {@code surface} and {@code picture}.
 */
public record SkyLink(ResourceKey<Level> dimension, ResourceKey<Level> space, Bounds surface, int ceiling, int ground,
        Orbit orbit, Wrap wrap, boolean seamPortals, int cloudBand, Optional<PlanetSurface.Picture> picture,
        int mapSize) {
    public static final Bounds DEFAULT_SURFACE = new Bounds(-32768, 32768, -16384, 16384);

    /** How the surface wraps: round a globe (x loops, crossing a pole comes out on the far side), a torus, or not. */
    public enum Wrap implements StringRepresentable {
        GLOBE, TORUS, NONE;

        public static final Codec<Wrap> CODEC = StringRepresentable.fromEnum(Wrap::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** A block rectangle. */
    public record Bounds(int minX, int maxX, int minZ, int maxZ) {
        public static final Codec<Bounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("min_x").forGetter(Bounds::minX), Codec.INT.fieldOf("max_x").forGetter(Bounds::maxX),
                Codec.INT.fieldOf("min_z").forGetter(Bounds::minZ), Codec.INT.fieldOf("max_z").forGetter(Bounds::maxZ))
                .apply(i, Bounds::new));
    }

    /** The planet's layer of space; see the class comment. */
    public record Orbit(double x, double z, double scale, int floor, int top, double maxAltitude) {
        public static final Orbit DEFAULT = new Orbit(0, 0, 16, -1950, 1950, 3.0);
        public static final Codec<Orbit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(Orbit::x),
                Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(Orbit::z),
                Codec.doubleRange(1, 4096).optionalFieldOf("scale", DEFAULT.scale).forGetter(Orbit::scale),
                Codec.INT.optionalFieldOf("floor", DEFAULT.floor).forGetter(Orbit::floor),
                Codec.INT.optionalFieldOf("top", DEFAULT.top).forGetter(Orbit::top),
                Codec.doubleRange(0.1, 100).optionalFieldOf("max_altitude", DEFAULT.maxAltitude)
                        .forGetter(Orbit::maxAltitude))
                .apply(i, Orbit::new));
    }

    public static final Codec<PlanetSurface.Picture> PICTURE_CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("texture").forGetter(PlanetSurface.Picture::texture),
            Codec.INT.fieldOf("min_x").forGetter(PlanetSurface.Picture::minX),
            Codec.INT.fieldOf("max_x").forGetter(PlanetSurface.Picture::maxX),
            Codec.INT.fieldOf("min_z").forGetter(PlanetSurface.Picture::minZ),
            Codec.INT.fieldOf("max_z").forGetter(PlanetSurface.Picture::maxZ),
            Codec.INT.optionalFieldOf("fill", -1).forGetter(PlanetSurface.Picture::fill))
            .apply(i, PlanetSurface.Picture::new));

    public static final Codec<SkyLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(SkyLink::dimension),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("space").forGetter(SkyLink::space),
            Bounds.CODEC.optionalFieldOf("surface", DEFAULT_SURFACE).forGetter(SkyLink::surface),
            Codec.INT.optionalFieldOf("ceiling", 340).forGetter(SkyLink::ceiling),
            Codec.INT.optionalFieldOf("ground", 64).forGetter(SkyLink::ground),
            Orbit.CODEC.optionalFieldOf("orbit", Orbit.DEFAULT).forGetter(SkyLink::orbit),
            Wrap.CODEC.optionalFieldOf("wrap", Wrap.GLOBE).forGetter(SkyLink::wrap),
            Codec.BOOL.optionalFieldOf("seam_portals", true).forGetter(SkyLink::seamPortals),
            Codec.intRange(0, 256).optionalFieldOf("cloud_band", 40).forGetter(SkyLink::cloudBand),
            PICTURE_CODEC.optionalFieldOf("picture").forGetter(SkyLink::picture),
            Codec.intRange(64, 2048).optionalFieldOf("map_size", 512).forGetter(SkyLink::mapSize))
            .apply(i, SkyLink::new));

    /** This link with its surface and picture replaced (by a planet provider). */
    public SkyLink withSurface(PlanetSurface s) {
        return new SkyLink(dimension, space, new Bounds(s.minX(), s.maxX(), s.minZ(), s.maxZ()), ceiling, ground,
                orbit, wrap, seamPortals, cloudBand, s.picture().or(() -> picture), mapSize);
    }

    /** Why this link can't be used, or null. */
    public String problem() {
        if (dimension.equals(space)) {
            return "a dimension can't be its own space";
        }
        if (surface.maxX() - surface.minX() < 256 || surface.maxZ() - surface.minZ() < 256) {
            return "the surface must be at least 256 blocks each way";
        }
        if (orbit.top() - orbit.floor() < 64) {
            return "orbit top must be at least 64 above its floor";
        }
        if (ceiling - ground < 16) {
            return "the ceiling must be at least 16 above the ground";
        }
        return null;
    }
}
