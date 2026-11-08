package com.porcupinestudios.tardisrb.console;

import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.flight.Destinations;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.flight.FreeFlight;
import com.porcupinestudios.tardisrb.flight.Piloting;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.interior.Rooms;
import com.porcupinestudios.tardisrb.progression.Control;
import com.porcupinestudios.tardisrb.progression.Destination;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import com.porcupinestudios.tardisrb.repair.Repairs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * What the console's controls do (Aaron, 2026-11-03: a console like the original mod's, with buttons, levers and dials
 * on the model instead of a flat screen; 2026-11-08: small control models on six themed sides laid out like the 1.7.10
 * console: X, Z, dimension and time, exterior, Y and memory, rooms). A control is on the console once its requirements
 * hold; each clickable part of it has an action:
 * <ul>
 * <li>{@code x0}..{@code x6}, {@code z0}.., {@code y0}..: one coordinate slot each (tuning {@code console.coordinates},
 * after the 1.7.10 console's X/Z levers and power wheels and its Y digits): up one, sneaking down one.</li>
 * <li>{@code relative} (X and Z count from where the TARDIS stands), {@code land} (land on the ground, Y ignored),
 * {@code save} (memory buttons save instead of load), {@code handbrake} (on: no take-off): on/off.</li>
 * <li>{@code memory}: loads (or in save mode saves) the coordinate settings in that memory slot (the part's number).</li>
 * <li>{@code facing}: the way the doors face on landing; {@code destination}: the dimension to go to (sneak: back);
 * {@code random}: sets the small coordinate levers at random.</li>
 * <li>{@code time0}..: one digit (0-9) of the target time each; {@code time_unit}: what the digits count (minutes,
 * hours...: tuning {@code console.time}); {@code time_from}: off = that long ago, on = that long after the beginning
 * of time. Stored and shown only until time travel is built.</li>
 * <li>{@code fast_return}: arms (or disarms) going back: the next take-off returns to where the TARDIS last took off
 * from. {@code schema_prev} / {@code schema_next}: page through the rooms that can be added; {@code add_room} builds
 * the picked one (paid like the repair screen); {@code schema_info} says whether it can be added now.</li>
 * <li>Exterior (docs/free-flight-plan.md): {@code phase_key} on/off; {@code phase} (only with the key on) turns the
 * box into a loose entity and back ({@link FreeFlight#phase}); {@code thrusters} on (hovers) or off (a physics
 * object that falls); {@code pilot}: the flight yoke, takes the crew member out to fly the box ({@link Piloting}).</li>
 * <li>{@code jump}: takes off for the set coordinates: a hop in this dimension or a jump to the picked one.</li>
 * <li>{@code screen} says where the TARDIS is; {@code door} opens or shuts the doors; {@code none} only moves.</li>
 * <li>Older ones still load: {@code x}/{@code y}/{@code z}, {@code x_up}..{@code z_down}, {@code step},
 * {@code reset}, {@code hop} (the course offset).</li>
 * </ul>
 * Every setting is held by the console by role ({@link ConsoleBlockEntity#state}), and a control bound to it is drawn
 * at its value. There is no console screen (Aaron, 2026-11-04: 100% physical controls like the 1.7.10 mod): the
 * monitor shows {@link #readout} for the side it stands over, and clicking the time rotor's column ({@link #COLUMN})
 * turns the monitor to face the player.
 */
public final class ConsoleControls {
    /** Actions with no slot number. */
    public static final List<String> ACTIONS = List.of("screen", "relative", "land", "save", "memory", "facing",
            "destination", "random", "jump", "handbrake", "door", "none", "time_from", "time_unit", "fast_return",
            "schema_prev", "schema_next", "add_room", "schema_info", "phase_key", "phase", "thrusters", "pilot", "x", "y", "z", "x_up", "x_down", "y_up", "y_down",
            "z_up", "z_down", "step", "reset", "hop");
    /** What gauges can show ({@link #values}). */
    public static final List<String> VALUES = List.of("charge", "rooms");

    /** An action name data may use: one of {@link #ACTIONS} or a coordinate slot ({@code x0}, {@code z3}, {@code y1}). */
    public static boolean isAction(String action) {
        return ACTIONS.contains(action) || slot(action) >= 0 || timeDigit(action) >= 0;
    }

    /** The digit a target-time action sets ({@code time0} is the first, highest digit), or -1. */
    static int timeDigit(String action) {
        if (!action.startsWith("time") || action.length() != 5 || !Character.isDigit(action.charAt(4))) {
            return -1;
        }
        return action.charAt(4) - '0';
    }

    /** The number the time drums show (the first drum is the highest digit). */
    public static long timeAmount(ConsoleBlockEntity console) {
        int digits = TardisContent.tuning().console().time().digits();
        long n = 0;
        for (int i = 0; i < digits; i++) {
            n = n * 10 + Math.floorMod(console.state("time" + i), 10);
        }
        return n;
    }

    /** The unit the time drums count in (the unit dial). */
    public static Tuning.TimeUnit timeUnit(ConsoleBlockEntity console) {
        List<Tuning.TimeUnit> units = TardisContent.tuning().console().time().units();
        return units.isEmpty() ? new Tuning.TimeUnit("ticks", 1)
                : units.get(Math.floorMod(console.state("time_unit"), units.size()));
    }

    /** True when the target time counts from the beginning of time, false when it counts back from now. */
    public static boolean fromBeginning(ConsoleBlockEntity console) {
        return console.state("time_from") != 0;
    }

    /** The target time in words: "12 minutes ago", "12 minutes from the beginning of time", or "now". */
    public static String timeText(ConsoleBlockEntity console) {
        long n = timeAmount(console);
        String unit = timeUnit(console).name();
        if (fromBeginning(console)) {
            return n == 0 ? "the beginning of time" : n + " " + unit + " from the beginning of time";
        }
        return n == 0 ? "now" : n + " " + unit + " ago";
    }

    private static Component timeSet(ConsoleBlockEntity console) {
        return Component.translatableWithFallback("tardis_rb.console.time", "Target time: %s", timeText(console));
    }

    /** The game tick the time controls aim at, given the tick it is {@code now}; never before 0 or after now. */
    public static long targetTick(ConsoleBlockEntity console, long now) {
        long span = timeAmount(console) * timeUnit(console).ticks();
        long t = fromBeginning(console) ? span : now - span;
        return Math.max(0, Math.min(now, t));
    }

    /** The slot number of a coordinate action ({@code x0} -> 0), or -1. */
    static int slot(String action) {
        if (action.length() < 2 || "xyz".indexOf(action.charAt(0)) < 0) {
            return -1;
        }
        try {
            int n = Integer.parseInt(action.substring(1));
            List<Tuning.Slot> slots = slots(action.charAt(0));
            return n >= 0 && n < Math.max(slots.size(), 10) ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static List<Tuning.Slot> slots(char axis) {
        Tuning.Coordinates c = TardisContent.tuning().console().coordinates();
        return axis == 'y' ? c.y() : c.xz();
    }

    /** What the X or Z ({@code axis}) slots add up to now. */
    public static long axisValue(ConsoleBlockEntity console, char axis) {
        List<Tuning.Slot> slots = slots(axis);
        long sum = 0;
        for (int i = 0; i < slots.size(); i++) {
            Tuning.Slot s = slots.get(i);
            long v = (long) console.state(axis + "" + i) * s.times();
            if (s.power() >= 0) {
                v *= (long) Math.pow(s.base(), console.state(axis + "" + s.power()) + 1);
            }
            sum += v;
        }
        return sum;
    }

    /** The Y the controls give (the digits plus {@code y_offset}). */
    public static int yValue(ConsoleBlockEntity console) {
        return (int) axisValue(console, 'y') + TardisContent.tuning().console().coordinates().yOffset();
    }

    /** Where the coordinate controls aim from where the TARDIS stands ({@code here}); Y is the controls' Y. */
    public static BlockPos target(ConsoleBlockEntity console, BlockPos here) {
        boolean relative = console.state("relative") != 0;
        long x = axisValue(console, 'x') + (relative ? here.getX() : 0);
        long z = axisValue(console, 'z') + (relative ? here.getZ() : 0);
        int max = 29_999_000;
        return new BlockPos((int) Math.max(-max, Math.min(max, x)), yValue(console),
                (int) Math.max(-max, Math.min(max, z)));
    }

    /** The pseudo-control a click on the time rotor's column sends: turns the monitor toward the player. */
    public static final String COLUMN = "column";

    private static final Component HANDBRAKE = Component.translatableWithFallback("tardis_rb.console.handbrake",
            "The handbrake is on");

    private ConsoleControls() {}

    /** The controls on a console at this position: those of its TARDIS whose requirements hold, in id order. */
    public static List<ConsoleBlockEntity.Shown> shown(ServerLevel level, BlockPos console) {
        Optional<TardisRecord> tardis = Interiors.at(level, console);
        return tardis.isEmpty() ? List.of() : shown(level.getServer(), tardis.get());
    }

    public static List<ConsoleBlockEntity.Shown> shown(MinecraftServer server, TardisRecord tardis) {
        ConditionContext ctx = Unlocks.context(server, tardis, Optional.empty());
        List<ConsoleBlockEntity.Shown> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Control> e : new TreeMap<>(TardisContent.get().controls()).entrySet()) {
            Control c = e.getValue();
            if (c.requirements().map(r -> r.test(ctx)).orElse(true)) {
                out.add(new ConsoleBlockEntity.Shown(e.getKey().toString(), c.model(), c.panel(), c.u(), c.v(),
                        c.turn(), c.scale(), Map.copyOf(c.actions()), List.copyOf(c.shows())));
            }
        }
        return out;
    }

    /**
     * A player clicked a control on the console at {@code pos}: {@code pressed} is {@code <control id>|<part>}
     * ({@link ConsoleLayout#key}) or {@link #COLUMN}. Checks they may, then does it.
     */
    public static Optional<Component> press(ServerPlayer player, BlockPos pos, String pressed) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ConsoleBlockEntity console)) {
            return Optional.empty();
        }
        Optional<TardisRecord> tardis = Interiors.at(level, pos);
        if (tardis.isEmpty() || !tardis.get().isCrew(player.getUUID())) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.console.dead", "The console stays dark"));
        }
        // the controls are within the console's radius of its block
        if (player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > ConsoleLayout.REACH + ConsoleLayout.RADIUS) {
            return Optional.empty();
        }
        if (pressed.equals(COLUMN)) {
            faceMonitor(console, player);
            return Optional.empty();
        }
        int bar = pressed.lastIndexOf('|');
        if (bar < 0) {
            return Optional.empty();
        }
        String controlId = pressed.substring(0, bar);
        String click = pressed.substring(bar + 1);
        Optional<ConsoleBlockEntity.Shown> control = console.shown().stream()
                .filter(c -> c.id().equals(controlId)).findFirst();
        if (control.isEmpty() || !control.get().actions().containsKey(click)) {
            return Optional.empty();
        }
        return Optional.of(use(player.server, tardis.get(), console, control.get(), click, Optional.of(player)));
    }

    /** Does what clicking one part ({@code click}) of a control does and says what happened. */
    public static Component use(MinecraftServer server, TardisRecord tardis, ConsoleBlockEntity console,
            ConsoleBlockEntity.Shown control, String click, Optional<ServerPlayer> player) {
        console.used(ConsoleLayout.key(control.id(), click));
        List<Integer> steps = TardisContent.tuning().console().steps();
        int step = steps.isEmpty() ? 1 : steps.get(Math.floorMod(console.step(), steps.size()));
        int dir = player.map(p -> p.isShiftKeyDown() ? -1 : 1).orElse(1);
        String action = control.action(click);
        int slot = slot(action);
        if (slot >= 0) {
            return turnSlot(console, action.charAt(0), slot, dir);
        }
        if (timeDigit(action) >= 0) {
            console.setState(action, Math.floorMod(console.state(action) + dir, 10));
            return timeSet(console);
        }
        switch (action) {
            case "screen" -> {
                return tardis.location().isEmpty()
                        ? Component.translatableWithFallback("tardis_rb.console.nowhere",
                                "The TARDIS isn't standing anywhere")
                        : Component.translatableWithFallback("tardis_rb.console.at", "At %s %s %s in %s",
                                tardis.location().get().pos().getX(), tardis.location().get().pos().getY(),
                                tardis.location().get().pos().getZ(),
                                tardis.location().get().dimension().location().getPath());
            }
            case "x" -> console.setOffset(console.dx() + dir * step, console.dy(), console.dz());
            case "y" -> console.setOffset(console.dx(), console.dy() + dir * step, console.dz());
            case "z" -> console.setOffset(console.dx(), console.dy(), console.dz() + dir * step);
            case "x_up" -> console.setOffset(console.dx() + step, console.dy(), console.dz());
            case "x_down" -> console.setOffset(console.dx() - step, console.dy(), console.dz());
            case "y_up" -> console.setOffset(console.dx(), console.dy() + step, console.dz());
            case "y_down" -> console.setOffset(console.dx(), console.dy() - step, console.dz());
            case "z_up" -> console.setOffset(console.dx(), console.dy(), console.dz() + step);
            case "z_down" -> console.setOffset(console.dx(), console.dy(), console.dz() - step);
            case "reset" -> console.setOffset(0, 0, 0);
            case "step" -> {
                console.setStep(Math.floorMod(console.step() + 1, Math.max(1, steps.size())));
                int now = steps.isEmpty() ? 1 : steps.get(console.step());
                return Component.translatableWithFallback("tardis_rb.console.step", "Step: %s blocks", now);
            }
            case "destination" -> {
                List<ResourceLocation> open = Destinations.open(server, tardis, player);
                if (open.isEmpty()) {
                    return Component.translatableWithFallback("tardis_rb.console.no_destinations",
                            "No destinations open");
                }
                console.setDestination(Math.floorMod(console.destination() + dir, open.size()));
                return Component.translatableWithFallback("tardis_rb.console.destination", "To: %s",
                        name(open.get(console.destination())));
            }
            case "random" -> {
                // like the 1.7.10 randomiser: sets the small coordinate levers (no power, small steps) at random
                RandomSource random = server.overworld().getRandom();
                List<Tuning.Slot> xz = slots('x');
                for (int i = 0; i < xz.size(); i++) {
                    Tuning.Slot s = xz.get(i);
                    boolean small = s.power() < 0 && s.times() > 0 && (long) s.times() * s.max() <= 60;
                    for (char axis : new char[] {'x', 'z'}) {
                        if (s.cycle() || s.times() == 0) {
                            continue;
                        }
                        console.setState(axis + "" + i, small ? s.min() + random.nextInt(s.max() - s.min() + 1)
                                : Math.max(s.min(), Math.min(s.max(), 0)));
                    }
                }
                return Component.translatableWithFallback("tardis_rb.console.random", "Coordinates set at random: %s %s",
                        signed(axisValue(console, 'x')), signed(axisValue(console, 'z')));
            }
            case "fast_return" -> {
                if (lastSpot(tardis).isEmpty()) {
                    console.setState("fast_return", 0);
                    return Component.translatableWithFallback("tardis_rb.console.no_return",
                            "The TARDIS hasn't flown anywhere to go back from yet");
                }
                console.setState("fast_return", console.state("fast_return") == 0 ? 1 : 0);
                return console.state("fast_return") != 0
                        ? Component.translatableWithFallback("tardis_rb.console.return_on",
                                "Fast return set: the next take-off goes back to %s", spotText(lastSpot(tardis).get()))
                        : Component.translatableWithFallback("tardis_rb.console.return_off", "Fast return off");
            }
            case "schema_prev", "schema_next" -> {
                List<ResourceLocation> rooms = schemas();
                if (rooms.isEmpty()) {
                    return Component.translatableWithFallback("tardis_rb.console.no_schemas", "No room plans");
                }
                int by = (action.equals("schema_next") ? 1 : -1) * dir;
                console.setState("schema", Math.floorMod(console.state("schema") + by, rooms.size()));
                return Component.translatableWithFallback("tardis_rb.console.schema", "Room plan: %s",
                        roomName(rooms.get(console.state("schema"))));
            }
            case "schema_info", "add_room" -> {
                Optional<ResourceLocation> room = schema(console);
                if (room.isEmpty()) {
                    return Component.translatableWithFallback("tardis_rb.console.no_schemas", "No room plans");
                }
                if (action.equals("schema_info")) {
                    return Rooms.blocked(server, tardis, room.get(), player).orElse(Component
                            .translatableWithFallback("tardis_rb.console.schema_ok", "%s can be added",
                                    roomName(room.get())));
                }
                Repairs.Outcome added = Rooms.add(server, tardis, room.get(), player);
                return added.problem().orElse(Component.translatableWithFallback("tardis_rb.console.room_added",
                        "%s added", roomName(room.get())));
            }
            case "phase_key" -> {
                console.setState("phase_key", console.state("phase_key") == 0 ? 1 : 0);
                return console.state("phase_key") != 0
                        ? Component.translatableWithFallback("tardis_rb.console.phase_key_on",
                                "Phase key turned: the phase lever is free")
                        : Component.translatableWithFallback("tardis_rb.console.phase_key_off", "Phase key off");
            }
            case "phase" -> {
                if (console.state("phase_key") == 0) {
                    console.setState("phase", FreeFlight.isLoose(tardis) ? 1 : 0);
                    return Component.translatableWithFallback("tardis_rb.console.phase_locked",
                            "Turn the phase key first");
                }
                Component said = FreeFlight.phase(server, tardis, player);
                console.setState("phase", FreeFlight.isLoose(tardis) ? 1 : 0);
                return said;
            }
            case "thrusters" -> {
                Component said = FreeFlight.setThrusters(server, tardis, !FreeFlight.thrusters(tardis), player);
                console.setState("thrusters", FreeFlight.thrusters(tardis) ? 1 : 0);
                return said;
            }
            case "pilot" -> {
                return player.map(p -> Piloting.start(server, tardis, p)).orElse(Component
                        .translatableWithFallback("tardis_rb.pilot.nobody", "Only a crew member can fly it"));
            }
            case "time_unit" -> {
                int units = Math.max(1, TardisContent.tuning().console().time().units().size());
                console.setState("time_unit", Math.floorMod(console.state("time_unit") + dir, units));
                return timeSet(console);
            }
            case "time_from" -> {
                console.setState("time_from", console.state("time_from") == 0 ? 1 : 0);
                return timeSet(console);
            }
            case "relative", "land", "save" -> {
                console.setState(action, console.state(action) == 0 ? 1 : 0);
                return Component.translatableWithFallback("tardis_rb.console." + action + "_"
                        + (console.state(action) != 0 ? "on" : "off"), action + (console.state(action) != 0
                                ? " on" : " off"));
            }
            case "facing" -> {
                int now = console.hasState("facing") ? console.state("facing") : 2;
                console.setState("facing", Math.floorMod(now + dir, 4));
                return Component.translatableWithFallback("tardis_rb.console.facing", "Lands facing %s",
                        Direction.from2DDataValue(console.state("facing")).getName());
            }
            case "memory" -> {
                int n = number(click);
                if (console.state("save") != 0) {
                    CompoundTag saved = new CompoundTag();
                    CompoundTag now = console.stateTag();
                    for (String k : now.getAllKeys()) {
                        if (slot(k) >= 0 || timeDigit(k) >= 0 || k.startsWith("time_") || k.equals("destination")
                                || k.equals("facing") || k.equals("relative") || k.equals("land")) {
                            saved.putInt(k, now.getInt(k));
                        }
                    }
                    console.setMemory(n, saved);
                    return Component.translatableWithFallback("tardis_rb.console.saved", "Saved to slot %s", n + 1);
                }
                Optional<CompoundTag> saved = console.memory(n);
                if (saved.isEmpty()) {
                    return Component.translatableWithFallback("tardis_rb.console.empty_slot", "Slot %s is empty",
                            n + 1);
                }
                for (String k : saved.get().getAllKeys()) {
                    console.setState(k, saved.get().getInt(k));
                }
                return Component.translatableWithFallback("tardis_rb.console.loaded", "Loaded slot %s", n + 1);
            }
            case "handbrake" -> {
                console.setHandbrake(!console.handbrake());
                return console.handbrake()
                        ? Component.translatableWithFallback("tardis_rb.console.handbrake_on", "Handbrake on")
                        : Component.translatableWithFallback("tardis_rb.console.handbrake_off", "Handbrake off");
            }
            case "door" -> {
                if (Doors.isOpen(tardis) && !Doors.isClosing(tardis)) {
                    Doors.shut(server, tardis);
                    return Doors.message(true);
                }
                if (!Doors.isOpen(tardis) && !Doors.open(server, tardis)) {
                    return Component.translatableWithFallback("tardis_rb.console.door_stuck",
                            "The doors won't open here");
                }
                return Doors.message(false);
            }
            case "hop" -> {
                if (console.handbrake()) {
                    return HANDBRAKE;
                }
                if (tardis.location().isEmpty()) {
                    return Component.translatableWithFallback("tardis_rb.console.nowhere",
                            "The TARDIS isn't standing anywhere");
                }
                BlockPos target = tardis.location().get().pos().offset(console.dx(), console.dy(), console.dz());
                Flight.Outcome out = Flight.hop(server, tardis, target, player);
                if (out.done()) {
                    console.setOffset(0, 0, 0);
                }
                return result(out);
            }
            case "jump" -> {
                if (console.handbrake()) {
                    return HANDBRAKE;
                }
                return result(takeOff(server, tardis, console, player));
            }
            default -> {
                return Component.empty();
            }
        }
        return Component.translatableWithFallback("tardis_rb.console.course", "Course: %s %s %s (step %s)",
                signed(console.dx()), signed(console.dy()), signed(console.dz()), step);
    }

    /**
     * Takes off for where the controls aim: a hop when the picked dimension is the one the TARDIS stands in (or none
     * is open), else a jump there. Land-on-ground puts the target on the surface; the facing control turns the doors.
     */
    public static Flight.Outcome takeOff(MinecraftServer server, TardisRecord tardis, ConsoleBlockEntity console,
            Optional<ServerPlayer> player) {
        if (tardis.location().isEmpty()) {
            return Flight.Outcome.fail("tardis_rb.hop.nowhere", "The TARDIS isn't standing anywhere");
        }
        GlobalPos from = tardis.location().get();
        if (console.state("fast_return") != 0 && lastSpot(tardis).isPresent()) {
            Flight.Outcome back = goBack(server, tardis, lastSpot(tardis).get(), console, player);
            if (back.done()) {
                console.setState("fast_return", 0);
                remember(tardis, from);
            }
            return back;
        }
        BlockPos here = from.pos();
        BlockPos target = target(console, here);
        Optional<Direction> facing = console.hasState("facing")
                ? Optional.of(Direction.from2DDataValue(console.state("facing"))) : Optional.empty();
        List<ResourceLocation> open = Destinations.open(server, tardis, player);
        Optional<ResourceLocation> to = open.isEmpty() ? Optional.empty()
                : Optional.of(open.get(Math.floorMod(console.destination(), open.size())));
        Optional<Destination> dest = to.flatMap(TardisContent::destination);
        boolean sameWorld = dest.isEmpty() || dest.get().dimension().equals(tardis.location().get().dimension());
        ServerLevel level = server.getLevel(sameWorld ? tardis.location().get().dimension() : dest.get().dimension());
        if (console.state("land") != 0 && level != null) {
            target = new BlockPos(target.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    target.getX(), target.getZ()), target.getZ());
        }
        Flight.Outcome out = sameWorld ? Flight.hop(server, tardis, target, facing, player)
                : Flight.jump(server, tardis, to.get(), Optional.of(target), facing, player);
        if (out.done()) {
            remember(tardis, from);
        }
        return out;
    }

    /** Flies back to {@code spot}: a hop in the same dimension, else a jump through an open destination there. */
    private static Flight.Outcome goBack(MinecraftServer server, TardisRecord tardis, GlobalPos spot,
            ConsoleBlockEntity console, Optional<ServerPlayer> player) {
        Optional<Direction> facing = console.hasState("facing")
                ? Optional.of(Direction.from2DDataValue(console.state("facing"))) : Optional.empty();
        if (spot.dimension().equals(tardis.location().get().dimension())) {
            return Flight.hop(server, tardis, spot.pos(), facing, player);
        }
        for (ResourceLocation id : Destinations.open(server, tardis, player)) {
            if (TardisContent.destination(id).map(d -> d.dimension().equals(spot.dimension())).orElse(false)) {
                return Flight.jump(server, tardis, id, Optional.of(spot.pos()), facing, player);
            }
        }
        return Flight.Outcome.fail("tardis_rb.console.return_closed", "The way back to %s isn't open",
                spot.dimension().location().toString());
    }

    /** Where the TARDIS last took off from by the console (for fast return), if anywhere. */
    public static Optional<GlobalPos> lastSpot(TardisRecord tardis) {
        CompoundTag tag = tardis.moduleData("console");
        if (!tag.contains("last_dimension")) {
            return Optional.empty();
        }
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("last_dimension"));
        return dim == null ? Optional.empty()
                : Optional.of(GlobalPos.of(ResourceKey.create(Registries.DIMENSION,
                        dim), BlockPos.of(tag.getLong("last_pos"))));
    }

    private static void remember(TardisRecord tardis, GlobalPos from) {
        CompoundTag tag = tardis.moduleData("console");
        tag.putString("last_dimension", from.dimension().location().toString());
        tag.putLong("last_pos", from.pos().asLong());
        tardis.setModuleData("console", tag);
    }

    private static String spotText(GlobalPos spot) {
        return spot.pos().getX() + " " + spot.pos().getY() + " " + spot.pos().getZ() + " in "
                + spot.dimension().location().getPath();
    }

    /** The rooms the schema chooser pages through, in id order. */
    public static List<ResourceLocation> schemas() {
        return List.copyOf(new TreeMap<>(TardisContent.get().rooms()).keySet());
    }

    /** The room the schema chooser shows now. */
    public static Optional<ResourceLocation> schema(ConsoleBlockEntity console) {
        List<ResourceLocation> rooms = schemas();
        return rooms.isEmpty() ? Optional.empty()
                : Optional.of(rooms.get(Math.floorMod(console.state("schema"), rooms.size())));
    }

    /** The schema chooser's room in words, with whether it can be added now. */
    public static String schemaText(MinecraftServer server, TardisRecord tardis, ConsoleBlockEntity console) {
        return schema(console).map(r -> "Plan: " + roomName(r).getString()
                + (Rooms.blocked(server, tardis, r, Optional.empty()).isEmpty() ? " (can add)" : ""))
                .orElse("No room plans");
    }

    private static Component roomName(ResourceLocation id) {
        return Component.translatableWithFallback("tardis_rb.room_name." + id.getNamespace() + "." + id.getPath(),
                id.getPath());
    }

    /** Turns one coordinate slot up ({@code dir} 1) or down (-1) within its range, or round if it cycles. */
    private static Component turnSlot(ConsoleBlockEntity console, char axis, int slot, int dir) {
        List<Tuning.Slot> slots = slots(axis);
        if (slot >= slots.size()) {
            return Component.empty();
        }
        Tuning.Slot s = slots.get(slot);
        String role = axis + "" + slot;
        int v = console.state(role) + dir;
        v = s.cycle() ? s.min() + Math.floorMod(v - s.min(), s.max() - s.min() + 1)
                : Math.max(s.min(), Math.min(s.max(), v));
        console.setState(role, v);
        return axis == 'y'
                ? Component.translatableWithFallback("tardis_rb.console.y", "Y %s", yValue(console))
                : Component.translatableWithFallback("tardis_rb.console.axis", "%s %s",
                        String.valueOf(axis).toUpperCase(), signed(axisValue(console, axis)));
    }

    /** The number at the end of a part's name ({@code slot_4} -> 4), or 0. */
    private static int number(String click) {
        int i = click.lastIndexOf('_');
        try {
            return i < 0 ? 0 : Integer.parseInt(click.substring(i + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Turns the monitor round the time rotor to face the player, as clicking the console's column did in the 1.7.10
     * mod. The angle is the player's direction from the rotor axis ({@code atan2(z, x)} in degrees).
     */
    public static void faceMonitor(ConsoleBlockEntity console, ServerPlayer player) {
        double dx = player.getX() - (console.getBlockPos().getX() + ConsoleLayout.PIVOT_X);
        double dz = player.getZ() - (console.getBlockPos().getZ() + ConsoleLayout.PIVOT_Z);
        console.setMonitorAngle((float) Math.toDegrees(Math.atan2(dz, dx)));
    }

    /**
     * What the monitor shows, refreshed every second on the server. It depends on the side it stands over (Aaron,
     * 2026-11-08: each side has a theme and the monitor says different things on each): X coordinates, Z coordinates,
     * dimension and time, exterior, Y and memory, rooms. The first line names the side. Plain text, worked out on the
     * server.
     */
    public static List<String> readout(MinecraftServer server, TardisRecord tardis, ConsoleBlockEntity console) {
        int side = ConsoleLayout.side(console.monitorAngle());
        List<String> out = new ArrayList<>();
        List<ResourceLocation> open = Destinations.open(server, tardis, Optional.empty());
        Optional<ResourceLocation> pick = open.isEmpty() ? Optional.empty()
                : Optional.of(open.get(Math.floorMod(console.destination(), open.size())));
        Optional<BlockPos> at = tardis.location().map(g -> g.pos());
        BlockPos aim = target(console, at.orElse(BlockPos.ZERO));
        boolean relative = console.state("relative") != 0;
        switch (side) {
            case 0, 1 -> {
                char axis = side == 0 ? 'x' : 'z';
                String a = side == 0 ? "X" : "Z";
                out.add(a + " COORDINATES");
                out.add("Target " + a + " " + (side == 0 ? aim.getX() : aim.getZ()));
                out.add(relative ? "(here " + signed(axisValue(console, axis)) + ")" : "(absolute)");
                out.add(at.map(p -> "Now " + a + " " + (side == 0 ? p.getX() : p.getZ())).orElse("In flight"));
            }
            case 2 -> {
                out.add("DIMENSION & TIME");
                out.add(pick.map(d -> "Target " + name(d).getString() + "[" + TardisContent.destination(d)
                        .map(x -> x.dimension().location()).orElse(d) + "]").orElse("No other dimensions"));
                out.add("Target time: " + timeText(console));
                if (console.state("fast_return") != 0) {
                    out.add(lastSpot(tardis).map(g -> "Fast return to " + spotText(g)).orElse("Fast return: nowhere"));
                }
                out.add("Now in " + tardis.location().map(g -> g.dimension().location().toString()).orElse("-"));
            }
            case 3 -> {
                out.add("EXTERIOR - " + tardis.name());
                out.add(at.isEmpty() ? "In flight" : !FreeFlight.isLoose(tardis) ? "Landed (a block)"
                        : FreeFlight.body(server, tardis).map(b -> b.isVehicle()).orElse(false)
                                ? "Flown, " + FreeFlight.height(server, tardis) + " up"
                        : FreeFlight.thrusters(tardis) ? "Hovering, " + FreeFlight.height(server, tardis) + " up"
                        : "Loose: a physics object");
                out.add("Phase key " + (console.state("phase_key") != 0 ? "ON" : "off") + ", thrusters "
                        + (FreeFlight.thrusters(tardis) ? "ON" : "off"));
                out.add("Facing " + (console.hasState("facing")
                        ? Direction.from2DDataValue(console.state("facing")).getName() : "as before"));
                out.add(console.handbrake() ? "Handbrake ON" : "Handbrake off");
                out.add("Charge " + tardis.charge() + "/" + Unlocks.chargeCap(tardis));
                out.add("Doors " + (Doors.isOpen(tardis) ? "open" : "shut"));
            }
            case 4 -> {
                out.add("Y & MEMORY");
                out.add(console.state("land") != 0 ? "Target Y: the ground" : "Target Y " + yValue(console));
                out.add("Land on ground " + (console.state("land") != 0 ? "on" : "off"));
                out.add("Save mode " + (console.state("save") != 0 ? "ON" : "off"));
                out.add("Saved " + console.memoryUsed() + "/"
                        + TardisContent.tuning().console().coordinates().memorySlots());
            }
            default -> {
                out.add("ROOMS");
                out.add("Rooms " + tardis.roomCount() + "/" + Rooms.cap(tardis));
                out.add(schemaText(server, tardis, console));
                out.add("Crew " + (tardis.crew().size() + (tardis.owner().isPresent() ? 1 : 0)));
                out.add("Doors " + (Doors.isOpen(tardis) ? "open" : "shut"));
                out.add("Stage " + tardis.stage().map(ResourceLocation::getPath).orElse("-"));
            }
        }
        return out;
    }

    /** What gauges show, 0..1 of full: {@code charge} (of the cap) and {@code rooms} (used of the room cap). */
    public static Map<String, Float> values(TardisRecord tardis) {
        long cap = Unlocks.chargeCap(tardis);
        int rooms = Rooms.cap(tardis);
        return Map.of("charge", cap <= 0 ? 0F : Math.min(1F, (float) tardis.charge() / cap),
                "rooms", rooms <= 0 ? 0F : Math.min(1F, (float) tardis.roomCount() / rooms));
    }

    private static Component result(Flight.Outcome out) {
        return out.done()
                ? Component.translatableWithFallback("tardis_rb.hop.done", "The TARDIS lands at %s %s %s",
                        out.landed().get().pos().getX(), out.landed().get().pos().getY(),
                        out.landed().get().pos().getZ())
                : out.problem().get();
    }

    private static Component name(ResourceLocation id) {
        return Component.translatableWithFallback("tardis_rb.destination." + id.getNamespace() + "." + id.getPath(),
                id.getPath());
    }

    private static String signed(long n) {
        return n >= 0 ? "+" + n : String.valueOf(n);
    }
}
