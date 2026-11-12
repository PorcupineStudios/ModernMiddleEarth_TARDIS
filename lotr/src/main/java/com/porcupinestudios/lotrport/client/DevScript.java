package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Dev only: drives the client from a script, to check rendering with no mouse or keyboard (a Claude session, the cloud
 * sandbox). Off unless the JVM has {@code -Dlotr_port.devscript=<file in the run folder>}; with
 * {@code -PquickPlay=<save>} the client opens the save and runs it once the player is in. One step per line:
 * {@code wait <ticks>}, {@code /<command>} (sent as the player), {@code use <x> <y> <z>} (right-click that block with
 * the main hand), {@code mouse <x> <y>} (move the pointer, window pixels), {@code click <x> <y>} (left-click the open screen at
 * GUI-scaled x, y), {@code close} (the open screen), {@code hud on|off}, {@code shot <name>}
 * ({@code screenshots/<name>.png}), {@code quit}; {@code #} starts a comment.
 */
@EventBusSubscriber(modid = LotrPort.MOD_ID, value = Dist.CLIENT)
public final class DevScript {
    private static final String FILE = System.getProperty("lotr_port.devscript");
    private static List<String> steps;
    private static int next;
    private static int waiting;

    private DevScript() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (FILE == null || mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (steps == null) {
            try {
                steps = Files.readAllLines(mc.gameDirectory.toPath().resolve(FILE));
            } catch (Exception e) {
                LotrPort.LOG.error("[devscript] can't read {}", FILE, e);
                steps = List.of();
            }
        }
        if (waiting > 0) {
            waiting--;
            return;
        }
        while (next < steps.size()) {
            String line = steps.get(next++).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            LotrPort.LOG.info("[devscript] {}", line);
            String[] w = line.split(" ");
            switch (w[0]) {
                case "wait" -> {
                    waiting = Integer.parseInt(w[1]);
                    return;
                }
                case "shot" -> {
                    Screenshot.grab(mc.gameDirectory, w[1] + ".png", mc.getMainRenderTarget(),
                            msg -> LotrPort.LOG.info("[devscript] {}", msg.getString()));
                    return;
                }
                case "use" -> {
                    BlockPos p = new BlockPos(Integer.parseInt(w[1]), Integer.parseInt(w[2]), Integer.parseInt(w[3]));
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));
                }
                case "mouse" -> mouse(mc, Double.parseDouble(w[1]), Double.parseDouble(w[2]));
                case "click" -> {
                    if (mc.screen != null) {
                        double x = Double.parseDouble(w[1]);
                        double y = Double.parseDouble(w[2]);
                        mc.screen.mouseClicked(x, y, 0);
                        mc.screen.mouseReleased(x, y, 0);
                    }
                }
                case "scroll" -> {
                    if (mc.screen != null) {
                        mc.screen.mouseScrolled(Double.parseDouble(w[1]), Double.parseDouble(w[2]), 0,
                                Double.parseDouble(w[3]));
                    }
                }
                case "close" -> mc.player.closeContainer();
                case "hud" -> mc.options.hideGui = w[1].equals("off");
                case "quit" -> {
                    mc.stop();
                    return;
                }
                default -> {
                    if (line.startsWith("/")) {
                        mc.player.connection.sendCommand(line.substring(1));
                    } else {
                        LotrPort.LOG.warn("[devscript] unknown step {}", line);
                    }
                }
            }
        }
    }

    /** Moves the pointer. GLFW drops the event of its own cursor warp on X11, so set the handler's fields too. */
    private static void mouse(Minecraft mc, double x, double y) {
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), x, y);
        try {
            for (String f : new String[] {"xpos", "ypos"}) {
                var field = net.minecraft.client.MouseHandler.class.getDeclaredField(f);
                field.setAccessible(true);
                field.setDouble(mc.mouseHandler, f.equals("xpos") ? x : y);
            }
        } catch (ReflectiveOperationException e) {
            LotrPort.LOG.warn("[devscript] can't move the mouse", e);
        }
    }
}
