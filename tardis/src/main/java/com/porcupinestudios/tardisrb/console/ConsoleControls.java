package com.porcupinestudios.tardisrb.console;

import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.flight.Destinations;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.interior.Rooms;
import com.porcupinestudios.tardisrb.progression.Control;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * What the console's controls do (Aaron, 2026-11-03: a console like the original mod's, with buttons, levers and dials
 * on the model instead of a flat screen; 2026-11-08: small control models on six themed sides). A control is on the
 * console once its requirements hold; each clickable part of it has an action:
 * {@code screen} says where the TARDIS is; {@code x}, {@code y}, {@code z} move the course by the step size (sneaking:
 * back; {@code x_up}..{@code z_down} one way only); {@code step} changes the step size; {@code reset} clears the
 * course; {@code hop} flies a short hop to where the TARDIS stands plus the course; {@code destination} picks the next
 * open destination, {@code random} a random one; {@code jump} jumps there; {@code handbrake} puts the handbrake on or
 * off (on: no hop or jump starts); {@code door} opens or shuts the doors; {@code none} only moves (decoration).
 * There is no console screen (Aaron, 2026-11-04: 100% physical controls like the 1.7.10 mod): the monitor shows
 * {@link #readout}, which depends on the side it stands over, and clicking the time rotor's column ({@link #COLUMN})
 * turns the monitor to face the player.
 */
public final class ConsoleControls {
    public static final List<String> ACTIONS = List.of("screen", "x", "y", "z", "x_up", "x_down", "y_up", "y_down",
            "z_up", "z_down", "step", "reset", "hop", "destination", "random", "jump", "handbrake", "door", "none");
    /** What gauges can show ({@link #values}). */
    public static final List<String> VALUES = List.of("charge", "rooms");

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
        switch (control.action(click)) {
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
                console.setDestination(Math.floorMod(console.destination() + 1, open.size()));
                return Component.translatableWithFallback("tardis_rb.console.destination", "To: %s",
                        name(open.get(console.destination())));
            }
            case "random" -> {
                List<ResourceLocation> open = Destinations.open(server, tardis, player);
                if (open.isEmpty()) {
                    return Component.translatableWithFallback("tardis_rb.console.no_destinations",
                            "No destinations open");
                }
                console.setDestination(server.overworld().getRandom().nextInt(open.size()));
                return Component.translatableWithFallback("tardis_rb.console.destination", "To: %s",
                        name(open.get(console.destination())));
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
                List<ResourceLocation> open = Destinations.open(server, tardis, player);
                if (open.isEmpty()) {
                    return Component.translatableWithFallback("tardis_rb.console.no_destinations",
                            "No destinations open");
                }
                ResourceLocation to = open.get(Math.floorMod(console.destination(), open.size()));
                return result(Flight.jump(server, tardis, to, Optional.empty(), player));
            }
            default -> {
                return Component.empty();
            }
        }
        return Component.translatableWithFallback("tardis_rb.console.course", "Course: %s %s %s (step %s)",
                signed(console.dx()), signed(console.dy()), signed(console.dz()), step);
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
     * 2026-11-08: each side has a theme and the monitor says different things on each): Flight, Navigation, Exterior,
     * Destinations, Systems, Interior and crew. The first line names the side. Plain text, worked out on the server.
     */
    public static List<String> readout(MinecraftServer server, TardisRecord tardis, ConsoleBlockEntity console) {
        int side = ConsoleLayout.side(console.monitorAngle());
        List<String> out = new ArrayList<>();
        List<ResourceLocation> open = Destinations.open(server, tardis, Optional.empty());
        String picked = open.isEmpty() ? "No destinations"
                : "To " + name(open.get(Math.floorMod(console.destination(), open.size()))).getString();
        String charge = "Charge " + tardis.charge() + "/" + Unlocks.chargeCap(tardis);
        Optional<BlockPos> at = tardis.location().map(g -> g.pos());
        switch (side) {
            case 0 -> {
                out.add("FLIGHT - " + tardis.name());
                out.add(at.isPresent() ? "Landed" : "In flight");
                out.add(console.handbrake() ? "Handbrake ON" : "Handbrake off");
                out.add(charge);
                out.add(picked);
            }
            case 2 -> {
                out.add("EXTERIOR");
                out.add(at.isPresent() ? "Landed (a block)" : "Not standing anywhere");
                out.add(tardis.location().map(g -> g.dimension().location().getPath()).orElse("-"));
                out.add(at.map(p -> "Height " + p.getY()).orElse("-"));
                out.add("Doors " + (Doors.isOpen(tardis) ? "open" : "shut"));
            }
            case 3 -> {
                out.add("DESTINATIONS");
                out.add(picked);
                out.add(open.size() + " open");
                for (int i = 0; i < Math.min(3, open.size()); i++) {
                    out.add("- " + name(open.get(i)).getString());
                }
            }
            case 4 -> {
                out.add("SYSTEMS");
                out.add(charge);
                for (ResourceLocation id : new TreeMap<>(TardisContent.get().subsystems()).keySet()) {
                    out.add(id.getPath() + " " + tardis.repairedLevel(id) + "/" + Unlocks.cap(tardis, id));
                }
                out.add("Stage " + tardis.stage().map(ResourceLocation::getPath).orElse("-"));
            }
            case 5 -> {
                out.add("INTERIOR & CREW");
                out.add("Crew " + (tardis.crew().size() + (tardis.owner().isPresent() ? 1 : 0)));
                out.add("Rooms " + tardis.roomCount() + "/" + Rooms.cap(tardis));
                out.add("Doors " + (Doors.isOpen(tardis) ? "open" : "shut"));
            }
            default -> {
                // side 1, navigation: where the TARDIS is and the course being set
                out.add(tardis.name());
                if (at.isPresent()) {
                    out.add(at.get().getX() + " " + at.get().getY() + " " + at.get().getZ());
                    out.add(tardis.location().get().dimension().location().getPath());
                } else {
                    out.add("-");
                    out.add("in flight");
                }
                List<Integer> steps = TardisContent.tuning().console().steps();
                int step = steps.isEmpty() ? 1 : steps.get(Math.floorMod(console.step(), steps.size()));
                out.add("Course " + signed(console.dx()) + " " + signed(console.dy()) + " " + signed(console.dz())
                        + " x" + step);
                out.add(picked);
                out.add(charge);
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

    private static String signed(int n) {
        return n >= 0 ? "+" + n : String.valueOf(n);
    }
}
