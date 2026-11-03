package com.porcupinestudios.tardisrb.console;

import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.flight.Destinations;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.net.ConsoleNet;
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
 * on the model instead of a flat screen). A control is on the console once its requirements hold. Actions:
 * {@code screen} opens the flight screen (the monitor); {@code x_up}..{@code z_down} move the course by the step size;
 * {@code step} turns the step dial; {@code reset} clears the course; {@code hop} flies a short hop to where the
 * TARDIS stands plus the course; {@code destination} turns the destination dial; {@code jump} jumps there.
 */
public final class ConsoleControls {
    public static final List<String> ACTIONS = List.of("screen", "x_up", "x_down", "y_up", "y_down", "z_up", "z_down",
            "step", "reset", "hop", "destination", "jump");

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
                out.add(new ConsoleBlockEntity.Shown(e.getKey().toString(), c.kind(), c.action(), c.panel(), c.u(),
                        c.v()));
            }
        }
        return out;
    }

    /** A player clicked a control on the console at {@code pos}: checks they may, then does it. */
    public static Optional<Component> press(ServerPlayer player, BlockPos pos, String controlId) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ConsoleBlockEntity console)) {
            return Optional.empty();
        }
        Optional<TardisRecord> tardis = Interiors.at(level, pos);
        if (tardis.isEmpty() || !tardis.get().isCrew(player.getUUID())) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.console.dead", "The console stays dark"));
        }
        Optional<ConsoleBlockEntity.Shown> control = console.shown().stream()
                .filter(c -> c.id().equals(controlId)).findFirst();
        if (control.isEmpty()) {
            return Optional.empty();
        }
        Vec3 at = Vec3.atLowerCornerOf(pos).add(ConsoleLayout.point(control.get().panel(), control.get().u(),
                control.get().v()));
        if (player.getEyePosition().distanceTo(at) > ConsoleLayout.REACH + 1) {
            return Optional.empty();
        }
        return Optional.of(use(player.server, tardis.get(), console, control.get(), Optional.of(player)));
    }

    /** Does what a control does and says what happened. */
    public static Component use(MinecraftServer server, TardisRecord tardis, ConsoleBlockEntity console,
            ConsoleBlockEntity.Shown control, Optional<ServerPlayer> player) {
        console.used(control.id());
        List<Integer> steps = TardisContent.tuning().console().steps();
        int step = steps.isEmpty() ? 1 : steps.get(Math.floorMod(console.step(), steps.size()));
        switch (control.action()) {
            case "screen" -> {
                player.ifPresent(p -> ConsoleNet.open(p, tardis));
                return Component.empty();
            }
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
            case "hop" -> {
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
