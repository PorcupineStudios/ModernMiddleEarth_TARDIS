package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.gravity.Tilt;
import com.porcupinestudios.tardisrb.net.SkyNet;
import com.porcupinestudios.tardisrb.space.Orbits;
import com.porcupinestudios.tardisrb.space.Planet;
import com.porcupinestudios.tardisrb.space.StarSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

/**
 * Space physics for this client's own player (Minecraft moves the player on the client): the planet's pull, orbits,
 * no drag ({@link Orbits}), and in the air jump / sneak push along the player's up. Floating free (Aaron 2026-11-15,
 * Outer Wilds style) the mouse and the roll keys turn the player's whole frame ({@link Tilt#spin}), WASD push along
 * its forward and right instead of walking, the align key eases it upright, and the frame goes to the server.
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class ClientSpacePhysics {
    public static final String CATEGORY = "key.categories.tardis_rb.space";
    public static final KeyMapping ROLL_LEFT = new KeyMapping("key.tardis_rb.roll_left", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping ROLL_RIGHT = new KeyMapping("key.tardis_rb.roll_right",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    public static final KeyMapping ALIGN = new KeyMapping("key.tardis_rb.align", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    /** WASD this tick while floating free, taken from vanilla's walking (forward, left). */
    private static float forward;
    private static float left;
    private static final Quaternionf sent = new Quaternionf();

    private ClientSpacePhysics() {}

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        var in = event.getInput();
        forward = 0;
        left = 0;
        if (Tilt.floating(event.getEntity()) && Orbits.moves(event.getEntity())) {
            forward = in.forwardImpulse;
            left = in.leftImpulse;
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player e = event.getEntity();
        if (!e.level().isClientSide()) {
            return;
        }
        boolean own = e == Minecraft.getInstance().player;
        boolean align = own && ALIGN.isDown();
        if (own && Tilt.floating(e)) {
            float roll = (ROLL_RIGHT.isDown() ? 1 : 0) - (ROLL_LEFT.isDown() ? 1 : 0);
            Tilt.of(e).spin(e, roll * (float) GlobeRenderer.tuning().motion().rollSpeed());
        }
        // every player this client sees has a frame (own view, others' bodies); others' come from the server
        StarSystem system = GlobeRenderer.systemNear(e.level(), e.position());
        if (system != null) {
            Tilt.tick(e, system, GlobeRenderer.tuning(), align);
        } else {
            Tilt.tickLayer(e, GlobeRenderer.planetBelow(e.level(), e.getX(), e.getZ()), GlobeRenderer.tuning(),
                    align);
        }
        if (!own) {
            return;
        }
        Tilt tilt = Tilt.of(e);
        if (tilt != null && !tilt.now().equals(sent, 1e-5f)) {
            sent.set(tilt.now());
            PacketDistributor.sendToServer(new SkyNet.FrameUp(new Quaternionf(sent)));
        }
        LocalPlayer player = (LocalPlayer) e;
        if (!Orbits.moves(player)) {
            return;
        }
        int thrust = 0;
        if (!player.onGround()) {
            thrust = (player.input.jumping ? 1 : 0) - (player.input.shiftKeyDown ? 1 : 0);
        }
        float ahead = Tilt.floating(player) ? forward : player.input.forwardImpulse;
        int boost = 0;
        if (!player.onGround() && Minecraft.getInstance().options.keySprint.isDown()) {
            boost = ahead > 0 ? 1 : ahead < 0 ? -1 : 0;
        }
        // sprinting the WASD go to boost, as before
        float fw = boost != 0 ? 0 : forward;
        if (system != null) {
            Orbits.stepSystem(player, system, GlobeRenderer.tuning(), thrust, boost, fw, left);
            return;
        }
        Planet p = GlobeRenderer.planetBelow(player.level(), player.getX(), player.getZ());
        if (p == null) {
            return;
        }
        Orbits.step(player, p, GlobeRenderer.tuning(), thrust, boost, fw, left);
    }
}
