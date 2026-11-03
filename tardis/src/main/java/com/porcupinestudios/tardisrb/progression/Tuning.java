package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Loose numbers that belong to no single subsystem: the crash site, hops, the Create winder, interiors, damage,
 * research speed and the console's controls. Read from
 * {@code data/tardis_rb/tardis/tuning/main.json}; every field has a default, so a pack can override just one.
 */
public record Tuning(CrashSite crashSite, Hop hop, Winder winder, Interior interior, Damage damage,
        ResearchSpeed research, ConsoleTuning console, DoorTuning door, SpaceTuning space) {
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
     * Added rooms go {@code room_step} blocks further along z each; {@code doorway} (x and y; z unused) is the corner of
     * the 1 x 2 opening carved through the wall between a room and the next.
     */
    public record Interior(int plotSpacing, int floorY, ResourceLocation room, BlockPos spawn, int roomStep,
            BlockPos doorway, ResourceLocation basement, BlockPos basementOffset) {
        static final Interior DEFAULT = new Interior(1024, 64,
                ResourceLocation.fromNamespaceAndPath("tardis_rb", "interior/console_room_wreck"), new BlockPos(4, 1, 1),
                9, new BlockPos(5, 1, 0), ResourceLocation.fromNamespaceAndPath("tardis_rb", "interior/repair_basement"),
                new BlockPos(0, -5, 0));
        static final Codec<Interior> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(64, 1 << 16).optionalFieldOf("plot_spacing", DEFAULT.plotSpacing)
                        .forGetter(Interior::plotSpacing),
                Codec.INT.optionalFieldOf("floor_y", DEFAULT.floorY).forGetter(Interior::floorY),
                ResourceLocation.CODEC.optionalFieldOf("room", DEFAULT.room).forGetter(Interior::room),
                BlockPos.CODEC.optionalFieldOf("spawn", DEFAULT.spawn).forGetter(Interior::spawn),
                Codec.intRange(1, 256).optionalFieldOf("room_step", DEFAULT.roomStep).forGetter(Interior::roomStep),
                BlockPos.CODEC.optionalFieldOf("doorway", DEFAULT.doorway).forGetter(Interior::doorway),
                ResourceLocation.CODEC.optionalFieldOf("basement", DEFAULT.basement).forGetter(Interior::basement),
                BlockPos.CODEC.optionalFieldOf("basement_offset", DEFAULT.basementOffset)
                        .forGetter(Interior::basementOffset))
                .apply(i, Interior::new));
    }

    /**
     * Damage from the world: an explosion within {@code explosion_radius} blocks of the police box knocks
     * {@code explosion_subsystem} down {@code explosion_levels} levels (0 = explosions do nothing).
     */
    public record Damage(ResourceLocation explosionSubsystem, int explosionLevels, double explosionRadius) {
        static final Damage DEFAULT = new Damage(ResourceLocation.fromNamespaceAndPath("tardis_rb", "hull"), 1, 4.0);
        static final Codec<Damage> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("explosion_subsystem", DEFAULT.explosionSubsystem)
                        .forGetter(Damage::explosionSubsystem),
                Codec.INT.optionalFieldOf("explosion_levels", DEFAULT.explosionLevels).forGetter(Damage::explosionLevels),
                Codec.DOUBLE.optionalFieldOf("explosion_radius", DEFAULT.explosionRadius)
                        .forGetter(Damage::explosionRadius))
                .apply(i, Damage::new));
    }

    /**
     * Researchers (colonists, through the shared API) working for the crew: each one makes running research go
     * {@code speed_per_researcher} times faster on top of normal, counting at most {@code max_researchers}.
     */
    public record ResearchSpeed(double speedPerResearcher, int maxResearchers) {
        static final ResearchSpeed DEFAULT = new ResearchSpeed(0.5, 4);
        static final Codec<ResearchSpeed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("speed_per_researcher", DEFAULT.speedPerResearcher)
                        .forGetter(ResearchSpeed::speedPerResearcher),
                Codec.INT.optionalFieldOf("max_researchers", DEFAULT.maxResearchers)
                        .forGetter(ResearchSpeed::maxResearchers))
                .apply(i, ResearchSpeed::new));
    }

    /** The console: {@code steps} are the sizes the step dial picks from for the coordinate buttons. */
    public record ConsoleTuning(List<Integer> steps) {
        static final ConsoleTuning DEFAULT = new ConsoleTuning(List.of(1, 10, 100));
        static final Codec<ConsoleTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1 << 20).listOf().optionalFieldOf("steps", DEFAULT.steps)
                        .forGetter(ConsoleTuning::steps))
                .apply(i, ConsoleTuning::new));
    }

    /**
     * The see-through door (Immersive Portals): the interior door's lower block ({@code interior_door}, from the console
     * room's corner) and the way it faces into the room ({@code interior_facing}); the opening's {@code width} and
     * {@code height}; and how far in front of each door face the portal plane stands ({@code offset}; negative is
     * inside the block: the open door is a hollow shell with a 1 px frame, and the portal sits just behind the frame,
     * in line with the doorway, Aaron 2026-11-03).
     */
    public record DoorTuning(BlockPos interiorDoor, Direction interiorFacing, double width, double height,
            double offset) {
        static final DoorTuning DEFAULT = new DoorTuning(new BlockPos(4, 1, 0), Direction.SOUTH, 0.875, 1.875, -0.07);
        static final Codec<DoorTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("interior_door", DEFAULT.interiorDoor).forGetter(DoorTuning::interiorDoor),
                Direction.CODEC.optionalFieldOf("interior_facing", DEFAULT.interiorFacing)
                        .forGetter(DoorTuning::interiorFacing),
                Codec.doubleRange(0.1, 4).optionalFieldOf("width", DEFAULT.width).forGetter(DoorTuning::width),
                Codec.doubleRange(0.1, 4).optionalFieldOf("height", DEFAULT.height).forGetter(DoorTuning::height),
                Codec.doubleRange(-0.5, 0.5).optionalFieldOf("offset", DEFAULT.offset).forGetter(DoorTuning::offset))
                .apply(i, DoorTuning::new));
    }

    /**
     * Space: {@code gravity} is the share of normal gravity in space dimensions (0.15 = 15%); sky links are checked
     * every {@code check_ticks}; coming down from space you fall slowly for {@code landing_slow_fall} seconds.
     */
    public record SpaceTuning(double gravity, int checkTicks, int landingSlowFall) {
        static final SpaceTuning DEFAULT = new SpaceTuning(0.15, 5, 30);
        static final Codec<SpaceTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1).optionalFieldOf("gravity", DEFAULT.gravity).forGetter(SpaceTuning::gravity),
                Codec.intRange(1, 200).optionalFieldOf("check_ticks", DEFAULT.checkTicks)
                        .forGetter(SpaceTuning::checkTicks),
                Codec.intRange(0, 600).optionalFieldOf("landing_slow_fall", DEFAULT.landingSlowFall)
                        .forGetter(SpaceTuning::landingSlowFall))
                .apply(i, SpaceTuning::new));
    }

    public static final Tuning DEFAULT = new Tuning(CrashSite.DEFAULT, Hop.DEFAULT, Winder.DEFAULT, Interior.DEFAULT,
            Damage.DEFAULT, ResearchSpeed.DEFAULT, ConsoleTuning.DEFAULT, DoorTuning.DEFAULT, SpaceTuning.DEFAULT);

    public static final Codec<Tuning> CODEC = RecordCodecBuilder.create(i -> i.group(
            CrashSite.CODEC.optionalFieldOf("crash_site", CrashSite.DEFAULT).forGetter(Tuning::crashSite),
            Hop.CODEC.optionalFieldOf("hop", Hop.DEFAULT).forGetter(Tuning::hop),
            Winder.CODEC.optionalFieldOf("winder", Winder.DEFAULT).forGetter(Tuning::winder),
            Interior.CODEC.optionalFieldOf("interior", Interior.DEFAULT).forGetter(Tuning::interior),
            Damage.CODEC.optionalFieldOf("damage", Damage.DEFAULT).forGetter(Tuning::damage),
            ResearchSpeed.CODEC.optionalFieldOf("research", ResearchSpeed.DEFAULT).forGetter(Tuning::research),
            ConsoleTuning.CODEC.optionalFieldOf("console", ConsoleTuning.DEFAULT).forGetter(Tuning::console),
            DoorTuning.CODEC.optionalFieldOf("door", DoorTuning.DEFAULT).forGetter(Tuning::door),
            SpaceTuning.CODEC.optionalFieldOf("space", SpaceTuning.DEFAULT).forGetter(Tuning::space))
            .apply(i, Tuning::new));
}
