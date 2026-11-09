package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.flight.PilotInput;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The pilot's side of flying the box (docs/free-flight-plan.md): while the local player sits in a {@link TardisBody},
 * the view is third person from behind, and W A S D, Q / E (our own key mappings, rebindable), sprint and jump are
 * handed to {@link PilotInput} for the box to read. Q and E would also drop an item and open the inventory, so those
 * clicks are taken first while flying (Aaron 2026-11-08: Q / E fly up / down; Shift only stops piloting, which is
 * vanilla's get-off key).
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class PilotClient {
    public static final String CATEGORY = "key.categories.tardis_rb";
    public static final KeyMapping UP = new KeyMapping("key.tardis_rb.fly_up", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Q, CATEGORY);
    public static final KeyMapping DOWN = new KeyMapping("key.tardis_rb.fly_down", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E, CATEGORY);

    /** The camera before flying, put back after. */
    private static CameraType before;

    private PilotClient() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean flying = player != null && player.getVehicle() instanceof TardisBody;
        if (!flying) {
            PilotInput.clear();
            if (before != null) {
                mc.options.setCameraType(before);
                before = null;
            }
            return;
        }
        if (before == null) {
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        while (mc.options.keyDrop.consumeClick()) {
            // Q flies up instead
        }
        while (mc.options.keyInventory.consumeClick()) {
            // E flies down instead
        }
        if (mc.screen != null) {
            PilotInput.clear();
            return;
        }
        PilotInput.set(player.input.forwardImpulse, player.input.leftImpulse,
                (UP.isDown() ? 1 : 0) - (DOWN.isDown() ? 1 : 0), mc.options.keySprint.isDown(),
                mc.options.keyJump.isDown());
    }
}
