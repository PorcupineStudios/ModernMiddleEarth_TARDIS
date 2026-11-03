package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Loose numbers that belong to no single subsystem: the crash site, hops, the Create winder and interiors. Read from
 * {@code data/tardis_rb/tardis/tuning/main.json}; every field has a default, so a pack can override just one.
 */
public record Tuning(CrashSite crashSite, Hop hop, Winder winder, Interior interior) {
    /** The wreck made once at the start dimension's spawn. {@code offset}: blocks east of the spawn point. */
    public record CrashSite(boolean enabled, int offset, String name, long startCharge) {
        static final CrashSite DEFAULT = new CrashSite(true, 8, "The Wreck", 0);
        static final Codec<CrashSite> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled).forGetter(CrashSite::enabled),
                Codec.INT.optionalFieldOf("offset", DEFAULT.offset).forGetter(CrashSite::offset),
                Codec.STRING.optionalFieldOf("name", DEFAULT.name).forGetter(CrashSite::name),
                Codec.LONG.optionalFieldOf("start_charge", DEFAULT.startCharge).forGetter(CrashSite::startCharge))
                .apply(i, CrashSite::new));
    }

    /**
     * Short hops in one dimension. Cost = max({@code min_charge}, distance x {@code charge_per_block}). The landing
     * search looks {@code landing_radius} blocks sideways and {@code landing_height} up and down for a dry, safe spot.
     */
    public record Hop(double chargePerBlock, long minCharge, int landingRadius, int landingHeight) {
        static final Hop DEFAULT = new Hop(0.5, 5, 8, 24);
        static final Codec<Hop> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("charge_per_block", DEFAULT.chargePerBlock).forGetter(Hop::chargePerBlock),
                Codec.LONG.optionalFieldOf("min_charge", DEFAULT.minCharge).forGetter(Hop::minCharge),
                Codec.INT.optionalFieldOf("landing_radius", DEFAULT.landingRadius).forGetter(Hop::landingRadius),
                Codec.INT.optionalFieldOf("landing_height", DEFAULT.landingHeight).forGetter(Hop::landingHeight))
                .apply(i, Hop::new));
    }

    /**
     * The Create winder: charge per second = |RPM| x {@code charge_per_rpm_second}; {@code stress} is its stress
     * impact per RPM. It feeds the TARDIS whose exterior is within {@code link_range} blocks, or whose interior it is in.
     */
    public record Winder(double chargePerRpmSecond, double stress, int linkRange) {
        static final Winder DEFAULT = new Winder(0.05, 4.0, 2);
        static final Codec<Winder> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("charge_per_rpm_second", DEFAULT.chargePerRpmSecond)
                        .forGetter(Winder::chargePerRpmSecond),
                Codec.DOUBLE.optionalFieldOf("stress", DEFAULT.stress).forGetter(Winder::stress),
                Codec.INT.optionalFieldOf("link_range", DEFAULT.linkRange).forGetter(Winder::linkRange))
                .apply(i, Winder::new));
    }

    /**
     * Interiors in the shared dimension: plot n starts at x = n x {@code plot_spacing}, z = 0, y = {@code floor_y}.
     * {@code room} is the structure template of the first console room; {@code spawn} is where players appear in it.
     */
    public record Interior(int plotSpacing, int floorY, ResourceLocation room, BlockPos spawn) {
        static final Interior DEFAULT = new Interior(1024, 64,
                ResourceLocation.fromNamespaceAndPath("tardis_rb", "interior/console_room_wreck"), new BlockPos(4, 1, 1));
        static final Codec<Interior> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(64, 1 << 16).optionalFieldOf("plot_spacing", DEFAULT.plotSpacing)
                        .forGetter(Interior::plotSpacing),
                Codec.INT.optionalFieldOf("floor_y", DEFAULT.floorY).forGetter(Interior::floorY),
                ResourceLocation.CODEC.optionalFieldOf("room", DEFAULT.room).forGetter(Interior::room),
                BlockPos.CODEC.optionalFieldOf("spawn", DEFAULT.spawn).forGetter(Interior::spawn))
                .apply(i, Interior::new));
    }

    public static final Tuning DEFAULT = new Tuning(CrashSite.DEFAULT, Hop.DEFAULT, Winder.DEFAULT, Interior.DEFAULT);

    public static final Codec<Tuning> CODEC = RecordCodecBuilder.create(i -> i.group(
            CrashSite.CODEC.optionalFieldOf("crash_site", CrashSite.DEFAULT).forGetter(Tuning::crashSite),
            Hop.CODEC.optionalFieldOf("hop", Hop.DEFAULT).forGetter(Tuning::hop),
            Winder.CODEC.optionalFieldOf("winder", Winder.DEFAULT).forGetter(Tuning::winder),
            Interior.CODEC.optionalFieldOf("interior", Interior.DEFAULT).forGetter(Tuning::interior))
            .apply(i, Tuning::new));
}
