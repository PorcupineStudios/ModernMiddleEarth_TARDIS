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
     * Interiors in the shared dimension: plot n's origin is x = n x {@code plot_spacing}, z = 0, y = {@code floor_y}, the
     * floor in front of the console (Aaron 2026-11-04: the Capaldi console room). {@code room} is the console room's
     * structure template, placed with its corner at {@code room_offset} from the origin; {@code console} is where its
     * console stands and {@code spawn} where players appear. Added rooms grow out from the open ends of the room's
     * hallways ({@code room_anchors}, taken in turn), {@code room_step} blocks deeper each time, joined by an opening
     * the size of the hallway. (The repair basement is gone, Aaron 2026-11-04: the lower deck will take its place.)
     */
    public record Interior(int plotSpacing, int floorY, ResourceLocation room, BlockPos roomOffset, BlockPos console,
            BlockPos spawn, int roomStep, List<Anchor> roomAnchors) {
        static final Interior DEFAULT = new Interior(1024, 64,
                ResourceLocation.fromNamespaceAndPath("tardis_rb", "interior/console_room_capaldi"),
                new BlockPos(-15, -4, -15), BlockPos.ZERO, new BlockPos(0, 0, 6), 9,
                List.of(new Anchor(new BlockPos(-1, -3, -15), new BlockPos(2, -1, -15), Direction.NORTH),
                        new Anchor(new BlockPos(15, -3, -3), new BlockPos(15, -1, 0), Direction.EAST),
                        new Anchor(new BlockPos(-15, -3, -3), new BlockPos(-15, -1, 0), Direction.WEST)));
        static final Codec<Interior> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(64, 1 << 16).optionalFieldOf("plot_spacing", DEFAULT.plotSpacing)
                        .forGetter(Interior::plotSpacing),
                Codec.INT.optionalFieldOf("floor_y", DEFAULT.floorY).forGetter(Interior::floorY),
                ResourceLocation.CODEC.optionalFieldOf("room", DEFAULT.room).forGetter(Interior::room),
                BlockPos.CODEC.optionalFieldOf("room_offset", DEFAULT.roomOffset).forGetter(Interior::roomOffset),
                BlockPos.CODEC.optionalFieldOf("console", DEFAULT.console).forGetter(Interior::console),
                BlockPos.CODEC.optionalFieldOf("spawn", DEFAULT.spawn).forGetter(Interior::spawn),
                Codec.intRange(1, 256).optionalFieldOf("room_step", DEFAULT.roomStep).forGetter(Interior::roomStep),
                Anchor.CODEC.listOf().optionalFieldOf("room_anchors", DEFAULT.roomAnchors)
                        .forGetter(Interior::roomAnchors))
                .apply(i, Interior::new));

        /**
         * The open end of a hallway rooms grow from: its last cells ({@code min} to {@code max}, from the origin) and
         * the way it leads ({@code facing}).
         */
        public record Anchor(BlockPos min, BlockPos max, Direction facing) {
            static final Codec<Anchor> CODEC = RecordCodecBuilder.create(i -> i.group(
                    BlockPos.CODEC.fieldOf("min").forGetter(Anchor::min),
                    BlockPos.CODEC.fieldOf("max").forGetter(Anchor::max),
                    Direction.CODEC.fieldOf("facing").forGetter(Anchor::facing))
                    .apply(i, Anchor::new));
        }
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
    public record ConsoleTuning(List<Integer> steps, Coordinates coordinates, TimeTuning time) {
        static final ConsoleTuning DEFAULT = new ConsoleTuning(List.of(1, 10, 100), Coordinates.DEFAULT,
                TimeTuning.DEFAULT);
        static final Codec<ConsoleTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1 << 20).listOf().optionalFieldOf("steps", DEFAULT.steps)
                        .forGetter(ConsoleTuning::steps),
                Coordinates.CODEC.optionalFieldOf("coordinates", Coordinates.DEFAULT)
                        .forGetter(ConsoleTuning::coordinates),
                TimeTuning.CODEC.optionalFieldOf("time", TimeTuning.DEFAULT).forGetter(ConsoleTuning::time))
                .apply(i, ConsoleTuning::new));
    }

    /** One unit the time controls count in: its name on the monitor and how many game ticks it is. */
    public record TimeUnit(String name, long ticks) {
        static final Codec<TimeUnit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(TimeUnit::name),
                Codec.LONG.fieldOf("ticks").forGetter(TimeUnit::ticks))
                .apply(i, TimeUnit::new));
    }

    /**
     * The target-time controls (Aaron, 2026-11-08: "12 minutes from the beginning of time, or 12 minutes ago"):
     * {@code digits} number drums (0-9 each, the first is the highest), counted in one of {@code units} (the unit dial).
     */
    public record TimeTuning(int digits, List<TimeUnit> units) {
        static final TimeTuning DEFAULT = new TimeTuning(3, List.of(new TimeUnit("minutes", 1200),
                new TimeUnit("hours", 72_000), new TimeUnit("days", 24_000), new TimeUnit("years", 8_760_000)));
        static final Codec<TimeTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 9).optionalFieldOf("digits", DEFAULT.digits).forGetter(TimeTuning::digits),
                TimeUnit.CODEC.listOf().optionalFieldOf("units", DEFAULT.units).forGetter(TimeTuning::units))
                .apply(i, TimeTuning::new));
    }

    /**
     * One coordinate control (after the 1.7.10 console): its range ({@code cycle}: goes round instead of stopping);
     * it adds value x {@code times}, or value x {@code times} x {@code base}^(slot {@code power}'s value + 1) when it
     * has a power slot (-1 = none).
     */
    public record Slot(int min, int max, boolean cycle, int times, int base, int power) {
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(Slot::min),
                Codec.INT.fieldOf("max").forGetter(Slot::max),
                Codec.BOOL.optionalFieldOf("cycle", false).forGetter(Slot::cycle),
                Codec.INT.optionalFieldOf("times", 1).forGetter(Slot::times),
                Codec.INT.optionalFieldOf("base", 1).forGetter(Slot::base),
                Codec.INT.optionalFieldOf("power", -1).forGetter(Slot::power))
                .apply(i, Slot::new));

        static Slot lever(int times, int base, int power) {
            return new Slot(-6, 6, false, times, base, power);
        }
    }

    /**
     * The console's coordinate controls: the X and Z slots ({@code xz}, the same for both), the Y digits ({@code y},
     * plus {@code y_offset}), and how many memory slots there are.
     */
    public record Coordinates(List<Slot> xz, List<Slot> y, int yOffset, int memorySlots) {
        static final Coordinates DEFAULT = new Coordinates(
                List.of(Slot.lever(1, 4, 4), Slot.lever(1, 2, 5), Slot.lever(6, 1, -1), Slot.lever(1, 1, -1),
                        new Slot(0, 7, true, 0, 1, -1), new Slot(0, 7, true, 0, 1, -1), Slot.lever(71, 1, -1)),
                List.of(new Slot(0, 3, false, 64, 1, -1), new Slot(0, 3, false, 16, 1, -1),
                        new Slot(0, 3, false, 4, 1, -1), new Slot(0, 3, false, 1, 1, -1)),
                -64, 20);
        static final Codec<Coordinates> CODEC = RecordCodecBuilder.create(i -> i.group(
                Slot.CODEC.listOf().optionalFieldOf("xz", DEFAULT.xz).forGetter(Coordinates::xz),
                Slot.CODEC.listOf().optionalFieldOf("y", DEFAULT.y).forGetter(Coordinates::y),
                Codec.INT.optionalFieldOf("y_offset", DEFAULT.yOffset).forGetter(Coordinates::yOffset),
                Codec.intRange(0, 64).optionalFieldOf("memory_slots", DEFAULT.memorySlots)
                        .forGetter(Coordinates::memorySlots))
                .apply(i, Coordinates::new));
    }

    /**
     * The see-through door (Immersive Portals): the interior door's lower block ({@code interior_door}, from the console
     * room's corner) and the way it faces into the room ({@code interior_facing}); the opening's {@code width} and
     * {@code height}; and how far in front of each door face the portal plane stands ({@code offset}; negative is
     * inside the block: the open door is a hollow shell with a 1 px frame, and the portal sits just behind the frame,
     * in line with the doorway, Aaron 2026-11-03); {@code bottom}: how high above the door block's floor the opening
     * starts. The opening matches the police box model's doorway (0.75 x 1.625 from 0.125 up, the doors' outer face
     * 1 px in), so no portal shows past the frame at steep angles (Aaron 2026-11-04); {@code close_ticks}: how long the doors take to swing shut before
     * the portals go (match the model's {@code door_swing.ticks}, Aaron 2026-11-04).
     */
    public record DoorTuning(BlockPos interiorDoor, Direction interiorFacing, double width, double height,
            double offset, double bottom, int closeTicks) {
        static final DoorTuning DEFAULT = new DoorTuning(new BlockPos(0, 0, 8), Direction.NORTH, 0.75, 1.625, -0.0625,
                0.125, 8);
        static final Codec<DoorTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("interior_door", DEFAULT.interiorDoor).forGetter(DoorTuning::interiorDoor),
                Direction.CODEC.optionalFieldOf("interior_facing", DEFAULT.interiorFacing)
                        .forGetter(DoorTuning::interiorFacing),
                Codec.doubleRange(0.1, 4).optionalFieldOf("width", DEFAULT.width).forGetter(DoorTuning::width),
                Codec.doubleRange(0.1, 4).optionalFieldOf("height", DEFAULT.height).forGetter(DoorTuning::height),
                Codec.doubleRange(-0.5, 0.5).optionalFieldOf("offset", DEFAULT.offset).forGetter(DoorTuning::offset),
                Codec.doubleRange(0, 1).optionalFieldOf("bottom", DEFAULT.bottom).forGetter(DoorTuning::bottom),
                Codec.intRange(0, 200).optionalFieldOf("close_ticks", DEFAULT.closeTicks)
                        .forGetter(DoorTuning::closeTicks))
                .apply(i, DoorTuning::new));
    }

    /**
     * Space: {@code gravity} is the share of normal gravity in space dimensions (0.15 = 15%); sky links are checked
     * every {@code check_ticks}; coming down from space you fall slowly for {@code landing_slow_fall} seconds. Seam
     * portals (see-through planet edges) are put up for players within {@code seam_view} blocks of an edge, in pieces
     * {@code seam_segment} blocks long, and taken down {@code seam_linger} ticks after nobody needs them. Globe maps of
     * planets without a picture are sampled for at most {@code map_budget_ms} milliseconds a tick.
     */
    public record SpaceTuning(double gravity, int checkTicks, int landingSlowFall, int seamView, int seamSegment,
            int seamLinger, double mapBudgetMs, Motion motion) {
        public static final SpaceTuning DEFAULT = new SpaceTuning(0.15, 5, 30, 160, 64, 200, 2.0, Motion.DEFAULT);
        public static final Codec<SpaceTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1).optionalFieldOf("gravity", DEFAULT.gravity).forGetter(SpaceTuning::gravity),
                Codec.intRange(1, 200).optionalFieldOf("check_ticks", DEFAULT.checkTicks)
                        .forGetter(SpaceTuning::checkTicks),
                Codec.intRange(0, 600).optionalFieldOf("landing_slow_fall", DEFAULT.landingSlowFall)
                        .forGetter(SpaceTuning::landingSlowFall),
                Codec.intRange(0, 1024).optionalFieldOf("seam_view", DEFAULT.seamView)
                        .forGetter(SpaceTuning::seamView),
                Codec.intRange(16, 1024).optionalFieldOf("seam_segment", DEFAULT.seamSegment)
                        .forGetter(SpaceTuning::seamSegment),
                Codec.intRange(0, 72000).optionalFieldOf("seam_linger", DEFAULT.seamLinger)
                        .forGetter(SpaceTuning::seamLinger),
                Codec.doubleRange(0, 50).optionalFieldOf("map_budget_ms", DEFAULT.mapBudgetMs)
                        .forGetter(SpaceTuning::mapBudgetMs),
                Motion.CODEC.optionalFieldOf("motion", Motion.DEFAULT).forGetter(SpaceTuning::motion))
                .apply(i, SpaceTuning::new));
    }

    /**
     * Moving in space ({@code space/Orbits}). Gravity over a planet is {@code gravity} (share of normal) just above the
     * clouds and falls with the square of the distance from the planet's centre. Going sideways at
     * {@code orbit_speed} (blocks a tick in space, at the layer floor; slower higher up, as real orbits are) cancels it:
     * you orbit. Without air there is no drag ({@code drag_free}); jump and sneak push up and down by {@code thrust};
     * sprint + forward in the air speeds you along your look by {@code boost} a tick, sprint + back slows you by as
     * much (Aaron 2026-11-04: navigate space faster); nothing goes faster than {@code max_speed} blocks a tick (the
     * server's "moved too quickly" check is off for players in space, whose levels are empty). Floating in
     * a solar system, a player's view and body lean to the true pull ({@code free_tilt}), turning {@code tilt_speed}
     * of the way each tick; standing on blocks they stay six-way.
     */
    public record Motion(double orbitSpeed, double thrust, double maxSpeed, boolean dragFree, boolean freeTilt,
            double tiltSpeed, double boost) {
        public static final Motion DEFAULT = new Motion(2.5, 0.02, 40.0, true, true, 0.12, 0.25);
        public static final Codec<Motion> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 64).optionalFieldOf("orbit_speed", DEFAULT.orbitSpeed)
                        .forGetter(Motion::orbitSpeed),
                Codec.doubleRange(0, 1).optionalFieldOf("thrust", DEFAULT.thrust).forGetter(Motion::thrust),
                Codec.doubleRange(0.1, 200).optionalFieldOf("max_speed", DEFAULT.maxSpeed).forGetter(Motion::maxSpeed),
                Codec.BOOL.optionalFieldOf("drag_free", DEFAULT.dragFree).forGetter(Motion::dragFree),
                Codec.BOOL.optionalFieldOf("free_tilt", DEFAULT.freeTilt).forGetter(Motion::freeTilt),
                Codec.doubleRange(0.01, 1).optionalFieldOf("tilt_speed", DEFAULT.tiltSpeed)
                        .forGetter(Motion::tiltSpeed),
                Codec.doubleRange(0, 10).optionalFieldOf("boost", DEFAULT.boost).forGetter(Motion::boost))
                .apply(i, Motion::new));
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
