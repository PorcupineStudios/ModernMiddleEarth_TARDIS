package com.porcupinestudios.tardisrb.compat.jade;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ConsoleBlock;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.client.ConsoleAim;
import com.porcupinestudios.tardisrb.client.ControlModels;
import com.porcupinestudios.tardisrb.console.ConsoleControls;
import com.porcupinestudios.tardisrb.console.ConsoleLayout;
import com.porcupinestudios.tardisrb.progression.Control;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

/**
 * The console's controls stand outside the console block, so Jade alone would only see air or the invisible hull. A
 * ray-trace callback asks {@link ConsoleAim} (the same hit boxes clicking uses) what is under the crosshair and, when
 * it is a control nearer than any block, hands Jade the console block instead; the tooltip then names the control
 * (and the part, for controls with several), its state, what a click does and its side. The invisible hull, arch
 * posts and pedestal posts are hidden from Jade.
 */
public final class JadeConsole implements IBlockComponentProvider {
    static final ResourceLocation UID = TardisRB.id("console_control");
    private static final JadeConsole INSTANCE = new JadeConsole();
    /** The control the last ray trace found (client thread only). */
    private static ConsoleAim.Aim last;

    private JadeConsole() {}

    static void register(IWailaClientRegistration registration) {
        registration.registerBlockComponent(INSTANCE, ConsoleBlock.class);
        registration.hideTarget(TardisBlocks.CONSOLE_HULL.get());
        registration.hideTarget(TardisBlocks.COLLISION_POST.get());
        registration.hideTarget(TardisBlocks.COLLISION_PILLAR.get());
        registration.addRayTraceCallback((hit, accessor, original) -> onRayTrace(registration, hit, accessor));
    }

    private static Accessor<?> onRayTrace(IWailaClientRegistration registration, HitResult hit, Accessor<?> accessor) {
        Minecraft mc = Minecraft.getInstance();
        Optional<ConsoleAim.Aim> aim = ConsoleAim.find(1F);
        last = aim.orElse(null);
        if (aim.isEmpty() || mc.player == null) {
            return accessor;
        }
        // a real block in front of the control wins (the invisible hull and the console itself don't count)
        if (hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && block.getLocation().distanceTo(mc.player.getEyePosition()) < aim.get().distance()
                && !(mc.level.getBlockEntity(block.getBlockPos()) instanceof ConsoleBlockEntity)
                && !mc.level.getBlockState(block.getBlockPos()).is(TardisBlocks.CONSOLE_HULL.get())) {
            return accessor;
        }
        ConsoleBlockEntity be = aim.get().console();
        return registration.blockAccessor()
                .blockState(be.getBlockState())
                .blockEntity(be)
                .hit(new BlockHitResult(aim.get().hit(), Direction.UP, be.getBlockPos(), false))
                .build();
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        ConsoleAim.Aim aim = last;
        if (aim == null || !aim.console().getBlockPos().equals(accessor.getPosition())) {
            return;
        }
        ConsoleBlockEntity be = aim.console();
        ConsoleBlockEntity.Shown c = aim.control();
        if (c == null) {
            tooltip.replace(JadeIds.CORE_OBJECT_NAME,
                    Component.translatableWithFallback("tardis_rb.control.time_rotor", "Time Rotor"));
            tooltip.add(Component.translatableWithFallback("tardis_rb.jade.column", "Click: turns the monitor to you")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        String path = ResourceLocation.parse(c.id()).getPath();
        tooltip.replace(JadeIds.CORE_OBJECT_NAME, Component.translatableWithFallback("tardis_rb.control." + path,
                Component.translatable("tardis_rb.control." + c.model()).getString()));
        if (c.actions().size() > 1 && c.actions().values().stream().distinct().count() > 1) {
            tooltip.add(Component.translatable("tardis_rb.click." + aim.click()).withStyle(ChatFormatting.YELLOW));
        }
        state(be, c, aim.click()).ifPresent(s -> tooltip.add(s));
        if (!aim.click().isEmpty()) {
            tooltip.add(Component.translatableWithFallback("tardis_rb.jade.click", "Click: %s",
                    Component.translatable("tardis_rb.action." + c.action(aim.click())))
                    .withStyle(ChatFormatting.GRAY));
        }
        int side = c.model().equals(Control.MONITOR) ? ConsoleLayout.side(be.monitorAngle()) : c.panel();
        tooltip.add(Component.translatableWithFallback("tardis_rb.jade.side", "%s side",
                Component.translatable("tardis_rb.side." + side)).withStyle(ChatFormatting.DARK_AQUA));
    }

    /** How the clicked part is set now: on/off, its setting, its notch, or a gauge's value. */
    private static Optional<Component> state(ConsoleBlockEntity be, ConsoleBlockEntity.Shown c, String click) {
        String role = c.action(click);
        if (role.length() > 1 && "xz".indexOf(role.charAt(0)) >= 0 && Character.isDigit(role.charAt(1))) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.jade.slot", "Set to %s (%s total %s)",
                    be.state(role), String.valueOf(role.charAt(0)).toUpperCase(),
                    ConsoleControls.axisValue(be, role.charAt(0))));
        }
        if (role.length() > 1 && role.charAt(0) == 'y' && Character.isDigit(role.charAt(1))) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.jade.y", "Set to %s (Y %s)",
                    be.state(role), ConsoleControls.yValue(be)));
        }
        if (List.of("handbrake", "relative", "land", "save").contains(role)) {
            return Optional.of(onOff(be.state(role) != 0));
        }
        if (role.startsWith("time")) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.jade.time", "Target time: %s",
                    ConsoleControls.timeText(be)));
        }
        if (role.equals("facing")) {
            return Optional.of(Component.literal(be.hasState("facing")
                    ? Direction.from2DDataValue(be.state("facing")).getName() : "as before"));
        }
        Optional<ControlModels.Model> model = ControlModels.get(c.model());
        if (model.isEmpty()) {
            return Optional.empty();
        }
        int uses = be.uses(ConsoleLayout.key(c.id(), click));
        for (ControlModels.Action a : model.get().actions()) {
            if (click.isEmpty() && a.type().equals("gauge")) {
                StringBuilder s = new StringBuilder();
                for (String shows : c.shows()) {
                    s.append(s.isEmpty() ? "" : ", ").append(shows).append(' ')
                            .append(Math.round(be.value(shows) * 100)).append('%');
                }
                return s.isEmpty() ? Optional.empty() : Optional.of(Component.literal(s.toString()));
            }
            if (!click.equals(a.click())) {
                continue;
            }
            return switch (a.type()) {
                case "toggle" -> Optional.of(onOff(uses % 2 == 1));
                case "steps" -> Optional.of(Component.translatableWithFallback("tardis_rb.jade.notch",
                        "Setting %s of %s", Math.floorMod(uses, Math.max(1, a.count())) + 1, a.count()));
                default -> Optional.empty();
            };
        }
        return Optional.empty();
    }

    private static Component onOff(boolean on) {
        return on ? Component.translatableWithFallback("tardis_rb.jade.on", "On").withStyle(ChatFormatting.GREEN)
                : Component.translatableWithFallback("tardis_rb.jade.off", "Off").withStyle(ChatFormatting.RED);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
